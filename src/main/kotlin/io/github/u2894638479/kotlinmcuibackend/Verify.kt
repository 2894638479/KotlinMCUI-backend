package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.InternalBackend
import io.github.u2894638479.kotlinmcui.context.DslContext
import io.github.u2894638479.kotlinmcui.logger.dslLogger
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-game smoke check for development.
 *
 * Enabled with `-Dkotlinmcui.verify=<page>`, which the `verifyClient` run configuration
 * sets. Once the game has a screen to show it opens a page on the render thread, lets it
 * render for a few seconds, writes a screenshot into `run/screenshots` and quits, so
 * "does the backend still work in game" is something a machine can answer without a human
 * at the window. A page that cannot be resolved, or that throws while drawing, exits with a
 * non-zero code so the run can be gated.
 *
 * The page is named by reference rather than looked up in a table, so nothing has to register
 * anything and the framework stays free of preview-only API:
 *
 * - the reference an IDE hands out for a top-level function, which is the file name plus the
 *   function (`a.b.TestColorPage` means `a.b.TestColorPageKt.TestColorPage`);
 * - `a.b.Class#method` for anything else;
 * - a class name, in which case its `content()` draws the page (an entry).
 *
 * The page function takes the DSL context as its first JVM argument, so it is called from
 * inside the lambda the factory hands a context to - never as a plain method call.
 */
@InternalBackend
internal object Verify {
    private const val PROPERTY = "kotlinmcui.verify"
    private const val READY_TIMEOUT_MS = 240_000L
    private const val POLL_MS = 500L
    private const val SETTLE_MS = 3_000L
    private const val RENDER_MS = 8_000L

    fun init() {
        val spec = System.getProperty(PROPERTY)?.takeIf { it.isNotBlank() } ?: return
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
                    val page = pageFor(spec)
                    // The page takes the DSL context, so it is called inside the lambda the
                    // factory hands the context to - not evaluated as an argument.
                    val holder = DefaultBackend.create("DSL Entry") { page.draw() }
                    mc.setScreen(holder.screen)
                    dslLogger.info("verify: opened '$spec' as ${page.describe()}")
                } catch (e: Throwable) {
                    // Leaving from the render thread is deliberate: a flag checked on this thread
                    // would race the task that sets it.
                    dslLogger.error("verify: FAILED for '$spec'", e)
                    System.exit(1)
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

    /**
     * Resolves [reference] to a page, or fails with every name that was tried.
     *
     * The first form is what an IDE gives for a top-level Kotlin function: the JVM class is the
     * file name with `Kt` appended, so `a.b.TestColorPage` names `a.b.TestColorPageKt.testColorPage`
     * (the file class keeps the capitalised name). The second is the explicit `Class#method` form.
     * The third treats the reference as a class and uses its `content()`, which is how an entry is
     * previewed.
     */
    private fun pageFor(reference: String): PreviewPage {
        val tried = mutableListOf<String>()
        val hash = reference.indexOf('#')
        if (hash > 0) {
            val type = reference.substring(0,hash)
            val method = reference.substring(hash + 1)
            tried += "$type#$method"
            staticPage(type,method)?.let { return it }
        } else {
            val simple = reference.substringAfterLast('.')
            val parent = reference.substringBeforeLast('.',"")
            val fileClass = if (parent.isEmpty()) "${simple}Kt" else "$parent.${simple}Kt"
            tried += "$fileClass#$simple"
            staticPage(fileClass,simple)?.let { return it }
            tried += "$reference#content"
            entryPage(reference)?.let { return it }
        }
        error("no preview page for '$reference' - tried ${tried.joinToString()}")
    }

    private fun staticPage(className: String, methodName: String): PreviewPage? =
        forName(className)?.declaredMethods
            ?.firstOrNull { it.name == methodName && Modifier.isStatic(it.modifiers) && isPage(it) }
            ?.let { PreviewPage(it,null) }

    private fun entryPage(className: String): PreviewPage? = forName(className)?.let { type ->
        val instance = runCatching { type.getField("INSTANCE").get(null) }.getOrNull()
            ?: runCatching { type.getDeclaredConstructor().newInstance() }.getOrNull()
        val method = type.declaredMethods.firstOrNull { it.name == "content" && isPage(it) }
        if (instance != null && method != null) PreviewPage(method,instance) else null
    }

    private fun forName(className: String): Class<*>? = runCatching { Class.forName(className) }.getOrNull()

    /** A page draws into the DSL context, so it takes exactly that one parameter. */
    private fun isPage(method: Method) =
        method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == DslContext::class.java &&
            !Modifier.isAbstract(method.modifiers)

    /** A resolved page: the method to call and, for an entry member, the object to call it on. */
    private class PreviewPage(private val method: Method, private val receiver: Any?) {
        context(ctx: DslContext)
        fun draw() {
            method.invoke(receiver,ctx)
        }

        fun describe() = "${method.declaringClass.name}#${method.name}"
    }
}
