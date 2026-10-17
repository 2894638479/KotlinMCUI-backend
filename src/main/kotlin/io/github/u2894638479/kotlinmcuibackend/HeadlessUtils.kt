package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.DslPlatformUtils
import io.github.u2894638479.kotlinmcui.backend.DslRootUtils
import io.github.u2894638479.kotlinmcui.image.ImageHolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.util.concurrent.Executors

/**
 * The helpers of a side that has no output: every operation that would draw, query or interact is
 * a silent no-op instead of a crash.
 *
 * Nothing draws on such a side, so those no-ops are a safety net rather than a feature. One object
 * answers both halves because on a server both halves are equally empty; the platform half and the
 * root half stay separate types so a side that does have output can answer them differently.
 */
internal object HeadlessUtils : DslPlatformUtils, DslRootUtils {
    /** One thread for everything the framework runs "on the UI thread"; there is no other. */
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kotlinmcui-headless").apply { isDaemon = true }
    }

    override val sessionDispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    override var clipboard: String = ""

    override fun narrate(string: String) {}

    /** No language system on this side: answering null makes callers fall back to the key. */
    override fun translate(key: String, vararg args: Any?): String? = null

    override val isInWorld = false

    override fun loadLocalImage(file: File) = ImageHolder.empty

    override fun forceLoadLocalImage(file: File) = ImageHolder.empty

    override fun playButtonSound() {}

    override fun openUri(uri: String) {}

    override suspend fun capture(fileName: String): File? = null

    override fun quit(exitCode: Int) {}
}
