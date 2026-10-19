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
import net.minecraft.resources.ResourceLocation
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

    /**
     * Forget the cached holder and release the texture it owns; the next [localImage] starts reading
     * [file] again.
     *
     * Both the map and the texture belong to the render thread, so a call from anywhere else is
     * posted there instead of touching them. The cost is that such a call only promises "the next
     * frame reflects the file", not "the next [localImage] does": a caller already on the render
     * thread - which is where the DSL runs - takes the synchronous branch below.
     */
    override fun reloadLocalImage(file: File) {
        if(!isOnSessionThread) {
            Minecraft.getInstance().execute { dropImage(file) }
            return
        }
        dropImage(file)
    }

    private fun dropImage(file: File) {
        release(imageMap.remove(file) ?: return)
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

    /**
     * How long an unused local image may keep its texture registered.
     *
     * Wall clock, not frames: a page that stopped rendering (another screen, a paused game, a window
     * in the background) cannot be aged by a clock that only ticks while it is drawn - its entries
     * would then hold their textures indefinitely, which is exactly the leak this cache is bounded
     * against.
     */
    private const val IMAGE_CACHE_TTL_NANOS = 60_000_000_000L

    /**
     * One cached local image: the holder handed out, the texture registered for it, and when it was
     * last asked for. [texture] is null while the pixels are still loading, and for the placeholder
     * that failed to load - neither owns a texture, so releasing is a no-op for them.
     */
    private class CachedImage(
        var holder: ImageHolder,
        var texture: ResourceLocation?,
        var lastUsedNanos: Long,
    )

    private val imageMap = Object2ObjectOpenHashMap<File, CachedImage>()

    /**
     * Decode [file] into the pixels a [DynamicTexture] is built from, on the IO dispatcher.
     *
     * The texture object itself is deliberately not built here: its constructor queues its GL
     * upload onto the render thread, and building it off that thread would leave a window in which
     * [release] can close the pixels the queued upload is about to read. Decoding and the ARGB to
     * ABGR conversion are the slow part and stay off the render thread.
     */
    private suspend fun loadImageFile(file: File): NativeImage? {
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
        return native
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    override fun localImage(file: File): ImageHolder {
        val now = System.nanoTime()
        // A hit refreshes the entry's age even when the answer is still "nothing to draw yet":
        // the page is asking for this file every frame, so it is in use.
        imageMap[file]?.let {
            it.lastUsedNanos = now
            return it.holder
        }
        val entry = CachedImage(ImageHolder.empty, null, now)
        imageMap[file] = entry
        scope.launch {
            val native = loadImageFile(file)
            if(native == null) {
                Minecraft.getInstance().execute {
                    if(imageMap[file] === entry) {
                        entry.holder = ImageHolder(ImageSource.Resource("missing"),16.px,16.px)
                        entry.lastUsedNanos = System.nanoTime()
                    }
                }
                return@launch
            }
            Minecraft.getInstance().execute {
                // The entry may have been reloaded or swept while the file was being read: nobody
                // would own this texture then, so the pixels are dropped instead of registered.
                if(imageMap[file] !== entry) {
                    native.close()
                    return@execute
                }
                // Built on the render thread, so the texture is complete - and therefore safe to
                // release - the moment it exists; see [loadImageFile].
                val location = Minecraft.getInstance().textureManager.register("dslimageid", DynamicTexture(native))
                entry.holder = ImageHolder(ImageSource.Resource(location.toString()), native.width.px, native.height.px)
                entry.texture = location
                entry.lastUsedNanos = System.nanoTime()
            }
        }
        return ImageHolder.empty
    }

    /**
     * Drop and release the images nobody has asked for within [IMAGE_CACHE_TTL_NANOS].
     *
     * [Root.frame] calls this once per frame, which is what keeps it on the render thread: the map
     * is a plain one, and every write to it - this sweep, a load finishing, a reload - happens on
     * that same thread.
     */
    internal fun sweepLocalImages() {
        if(imageMap.isEmpty()) return
        val now = System.nanoTime()
        val iterator = imageMap.values.iterator()
        while(iterator.hasNext()) {
            val entry = iterator.next()
            if(now - entry.lastUsedNanos < IMAGE_CACHE_TTL_NANOS) continue
            iterator.remove()
            release(entry)
        }
    }

    /** Release the texture an entry owns and forget it, so the entry cannot release it again. */
    private fun release(entry: CachedImage) {
        val location = entry.texture ?: return
        entry.texture = null
        release(location)
    }

    /**
     * Release one texture this cache registered, on the render thread.
     *
     * Releasing is idempotent at both ends: this cache forgets the location before the call, and
     * `TextureManager.release` removes the entry itself, so a second release of the same location
     * finds nothing and does nothing.
     */
    private fun release(location: ResourceLocation?) {
        if(location == null) return
        Minecraft.getInstance().textureManager.release(location)
    }
}
