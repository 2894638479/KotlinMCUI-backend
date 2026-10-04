package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.InternalBackend
import io.github.u2894638479.kotlinmcui.logger.dslLogger
import io.github.u2894638479.kotlinmcui.test.verifyPage
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-game smoke check for development.
 *
 * Enabled with `-Dkotlinmcui.verify=<page>`, which the `verifyClient` run configuration
 * sets. Once the game has a screen to show it opens a page on the render thread, lets it
 * render for a few seconds, writes a screenshot into `run/screenshots` and quits, so
 * "does the backend still work in game" is something a machine can answer without a human
 * at the window.
 *
 * The pages themselves live in the framework (`verifyPage`): they are plain DSL and check
 * the framework's contracts, while this file only drives the platform - wait for a screen,
 * open, shoot, quit. `-Dkotlinmcui.verify=1` opens the framework's own test page; any other
 * value names one of its demo pages (`transform`, `translate`, `polygon`, `debug`).
 */
@InternalBackend
internal object Verify {
    private const val PROPERTY = "kotlinmcui.verify"
    private const val READY_TIMEOUT_MS = 240_000L
    private const val POLL_MS = 500L
    private const val SETTLE_MS = 3_000L
    private const val RENDER_MS = 8_000L

    fun init() {
        val spec = System.getProperty(PROPERTY) ?: return
        val mc = Minecraft.getInstance()
        val ready = AtomicBoolean(false)
        Thread({
            val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
            while (!ready.get() && System.currentTimeMillis() < deadline) {
                mc.execute { ready.set(mc.screen != null) }
                Thread.sleep(POLL_MS)
            }
            if (!ready.get()) {
                dslLogger.warn("verify: the game never reached a screen, giving up")
                mc.execute { mc.stop() }
                return@Thread
            }

            Thread.sleep(SETTLE_MS)
            mc.execute {
                try {
                    // verifyPage takes the DSL context, so it is called inside the lambda the
                    // factory hands the context to - not evaluated as an argument.
                    val holder = DefaultBackend.create("DSL Entry") { verifyPage(spec) }
                    mc.setScreen(holder.screen)
                    dslLogger.info("verify: opened page '$spec'")
                } catch (e: Throwable) {
                    dslLogger.error("verify: opening page '$spec' failed", e)
                }
            }

            Thread.sleep(RENDER_MS)
            mc.execute {
                // The first argument is the game directory: the platform appends screenshots/
                // itself and creates it.
                Screenshot.grab(mc.gameDirectory,"kotlinmcui-verify.png",mc.mainRenderTarget) {
                    dslLogger.info("verify: screenshot written")
                }
            }

            Thread.sleep(SETTLE_MS)
            mc.execute { mc.stop() }
        }, "kotlinmcui-verify").apply { isDaemon = true }.start()
    }
}
