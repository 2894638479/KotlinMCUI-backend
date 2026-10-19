package io.github.u2894638479.kotlinmcuibackend

import com.mojang.blaze3d.platform.NativeImage
import io.github.u2894638479.kotlinmcui.backend.DslPlatformUtils
import io.github.u2894638479.kotlinmcui.backend.DslRootUtils
import io.github.u2894638479.kotlinmcui.image.ImageHolder
import io.github.u2894638479.kotlinmcui.image.ImageSource
import io.github.u2894638479.kotlinmcui.logger.dslLogger
import io.github.u2894638479.kotlinmcui.math.Color
import io.github.u2894638479.kotlinmcui.math.px
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlinx.coroutines.*
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.locale.Language
import net.minecraft.sounds.SoundEvents
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object Utils: DslPlatformUtils, DslRootUtils {
    override fun translate(key: String,vararg args: Any?): String? {
        if(!Language.getInstance().has(key)) return null
        return Language.getInstance().getOrDefault(key,key).let {
            if(args.isEmpty()) it else try {
                return String.format(it,*Array(args.size) { args[it].toString() })
            } catch (_: Exception) { it }
        }
    }

    override val sessionDispatcher = Minecraft.getInstance().asCoroutineDispatcher()

    /** Drawing happens on the game's render thread, and that thread is this root's session thread. */
    override val isOnSessionThread get() = Minecraft.getInstance().isSameThread()

    override suspend fun capture(fileName: String): File? = suspendCancellableCoroutine { continuation ->
        val minecraft = Minecraft.getInstance()
        val file = File(File(minecraft.gameDirectory,"screenshots"),fileName)
        minecraft.execute {
            try {
                // The callback runs once the file is on disk, on the IO worker.
                Screenshot.grab(minecraft.gameDirectory,fileName,minecraft.mainRenderTarget) { _ ->
                    continuation.resume(file)
                }
            } catch (e: Throwable) {
                continuation.resumeWithException(e)
            }
        }
    }

    override fun quit(exitCode: Int) {
        val minecraft = Minecraft.getInstance()
        if(exitCode == 0) {
            minecraft.execute { minecraft.stop() }
            return
        }
        // The game's own shutdown always reports success, and a run nobody can gate on is useless,
        // so a failing preview leaves immediately instead.
        dslLogger.error("preview: leaving with code $exitCode")
        System.exit(exitCode)
    }

    override var clipboard by Minecraft.getInstance().keyboardHandler::clipboard
    override fun openUri(uri: String) = Util.getPlatform().openUri(uri)

    /** Forget the cached holder; the next [localImage] starts reading [file] again. */
    override fun reloadLocalImage(file: File) {
        imageMap.remove(file)
    }

    /**
     * This backend has no http client, so a remote image is never drawn. Answering empty (rather than
     * throwing) is the contract: a page still renders, minus the image.
     */
    override fun remoteImage(url: String) = ImageHolder.empty

    /** Remote images are never cached here, so there is nothing to drop. */
    override fun reloadRemoteImage(url: String) {}

    override fun playButtonSound() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
    }

    override fun narrate(string: String) {
        Minecraft.getInstance().narrator.sayNow(string.ifEmpty { return })
    }

    override val isInWorld get() = Minecraft.getInstance().level != null

    private val imageMap = Object2ObjectOpenHashMap<File, ImageHolder>()
    private suspend fun loadImageFile(file: File): DynamicTexture? {
        val image = try {
            withContext(Dispatchers.IO) {
                ImageIO.read(file)
            }
        } catch (e: IOException){
            dslLogger.warn("load texture failed : $file")
            dslLogger.warn(e.toString())
            return null
        }
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.getRGB(0, 0, width, height, pixels, 0, width)

        val native = NativeImage(width, height, false)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = pixels[y * width + x]
                val abgr = Color.ofARGB(argb).abgrInt
                native.setPixelRGBA(x, y, abgr)
            }
        }
        return DynamicTexture(native)
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    override fun localImage(file: File): ImageHolder {
        imageMap[file]?.let { return it }
        if(!imageMap.containsKey(file)) {
            imageMap[file] = ImageHolder.empty
            scope.launch {
                val dynamic = loadImageFile(file)
                val native = dynamic?.pixels ?: run {
                    Minecraft.getInstance().execute {
                        imageMap[file] = ImageHolder(ImageSource.Resource("missing"),16.px,16.px)
                    }
                    return@launch
                }
                Minecraft.getInstance().execute {
                    val location = Minecraft.getInstance().textureManager.register("dslimageid", dynamic)
                    imageMap[file] = ImageHolder(ImageSource.Resource(location.toString()), native.width.px, native.height.px)
                }
            }
        }
        return ImageHolder.empty
    }
}