package io.github.u2894638479.kotlinmcuibackend

import com.mojang.blaze3d.platform.NativeImage
import io.github.u2894638479.kotlinmcui.backend.DslBackendUtils
import io.github.u2894638479.kotlinmcui.image.ImageHolder
import io.github.u2894638479.kotlinmcui.logger.dslLogger
import io.github.u2894638479.kotlinmcui.math.Color
import io.github.u2894638479.kotlinmcui.math.px
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlinx.coroutines.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.locale.Language
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Util
import java.io.File
import java.io.IOException
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.streams.asSequence

internal object Utils: DslBackendUtils {
    override fun translate(key: String,vararg args: Any?): String? {
        if(!Language.getInstance().has(key)) return null
        return Language.getInstance().getOrDefault(key,key).let {
            if(args.isEmpty()) it else try {
                return String.format(it,*Array(args.size) { args[it].toString() })
            } catch (_: Exception) { it }
        }
    }

    override val mainDispatcher = Minecraft.getInstance().asCoroutineDispatcher()
    override var clipBoard by Minecraft.getInstance().keyboardHandler::clipboard
    override fun openUri(uri: String) = Util.getPlatform().openUri(uri)

    override fun forceLoadLocalImage(file: File): ImageHolder {
        imageMap.remove(file)
        return loadLocalImage(file)
    }

    override fun playButtonSound() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
    }

    override fun narrate(string: String) {
        Minecraft.getInstance().narrator.saySystemNow(string.ifEmpty { return })
    }

    override val isInWorld get() = Minecraft.getInstance().level != null

    val imageMap = Object2ObjectOpenHashMap<File, ImageHolder>()
    private suspend fun loadImageFile(file: File): () -> DynamicTexture? {
        val image = try {
            withContext(Dispatchers.IO) {
                ImageIO.read(file)
            }
        } catch (e: IOException){
            dslLogger.warn("load texture failed : $file")
            dslLogger.warn(e.toString())
            return { null }
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
                native.setPixelABGR(x, y, abgr)
            }
        }
        return { DynamicTexture({file.path},native) }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    override fun loadLocalImage(file: File): ImageHolder {
        imageMap[file]?.let { return it }
        if(!imageMap.containsKey(file)) {
            imageMap[file] = ImageHolder.empty
            scope.launch {
                val dynamic = loadImageFile(file)
                Minecraft.getInstance().execute {
                    val dynamic = dynamic()
                    val native = dynamic?.pixels ?: run {
                        Minecraft.getInstance().execute {
                            imageMap[file] = ImageHolder("missing",16.px,16.px)
                        }
                        return@execute
                    }
                    val location = Identifier.tryParse("kotlinmcui:${file.toString().chars().asSequence().joinToString("") { "d$it" }}")!!
                    Minecraft.getInstance().textureManager.register(location, dynamic)
                    imageMap[file] = ImageHolder(location.toString(), native.width.px, native.height.px)
                }
            }
        }
        return ImageHolder.empty
    }
}