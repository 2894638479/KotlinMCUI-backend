package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.DslBackend
import io.github.u2894638479.kotlinmcui.backend.DslBackendInput
import io.github.u2894638479.kotlinmcui.backend.DslBackendMetadata
import io.github.u2894638479.kotlinmcui.backend.DslBackendRenderer
import io.github.u2894638479.kotlinmcui.backend.DslBackendScreenFactory
import io.github.u2894638479.kotlinmcui.backend.DslBackendScreenHolder
import io.github.u2894638479.kotlinmcui.backend.DslBackendUtils
import io.github.u2894638479.kotlinmcui.context.DslScaleContext
import io.github.u2894638479.kotlinmcui.container.DslDataStore
import io.github.u2894638479.kotlinmcui.dsl.DslFunction
import io.github.u2894638479.kotlinmcui.image.ImageHolder
import io.github.u2894638479.kotlinmcui.input.Key
import io.github.u2894638479.kotlinmcui.math.Color
import io.github.u2894638479.kotlinmcui.math.Measure
import io.github.u2894638479.kotlinmcui.math.Position
import io.github.u2894638479.kotlinmcui.math.px
import io.github.u2894638479.kotlinmcui.math.rect.Rect
import io.github.u2894638479.kotlinmcui.math.transform.Transform
import io.github.u2894638479.kotlinmcui.text.DslFont
import io.github.u2894638479.kotlinmcui.text.DslGlyph
import io.github.u2894638479.kotlinmcui.text.DslRenderableChar
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.util.concurrent.Executors

/**
 * The backend for a side that has no GUI at all.
 *
 * It exists so the framework can be installed on a dedicated server: entries are discovered by
 * the real environment, metadata and the coroutine dispatcher work, and every operation that
 * would draw or query input is a silent no-op instead of a crash. Nothing draws on such a side,
 * so those no-ops are a safety net rather than a feature - which is exactly why a headless
 * backend cannot be recognised from the outside: the same mod is headless on a server and hands
 * over to the window backend on a client.
 *
 * Deliberately free of any Minecraft type: that is what lets one jar cover every game version.
 */
internal object HeadlessBackend : DslBackend<Unit, Unit>,
    DslBackendRenderer<Unit>,
    DslBackendScreenFactory<Unit>,
    DslBackendMetadata by Metadata,
    DslBackendUtils,
    DslBackendInput
{
    /** One thread for everything the framework runs "on the UI thread"; there is no other. */
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kotlinmcui-headless").apply { isDaemon = true }
    }

    override val mainDispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    /** Advancing by nothing keeps layout of text deterministic without ever painting it. */
    private val font = object : DslFont<Unit> {
        override val lineHeight = 9.px
        override fun glyph(code: Int) = object : DslGlyph {
            override val normalAdvance = 0.px
            override val boldOffset = 0.px
            override val shadowOffset = 0.px
        }

        context(renderParam: Unit)
        override fun renderChar(char: DslRenderableChar, x: Measure, y: Measure, effectLeft: Measure, effectRight: Measure) {}
    }

    override val guiScale = 1.0

    context(renderParam: Unit)
    override fun flush() {}

    context(renderParam: Unit)
    override fun renderQuad(rect: Rect, lt: Color, rt: Color, lb: Color, rb: Color, image: ImageHolder?, uv: Rect?) {}

    context(renderParam: Unit)
    override fun renderTriangles(vertices: FloatArray, colors: IntArray, image: ImageHolder?) {}

    context(renderParam: Unit, ctx: DslScaleContext)
    override fun renderButton(rect: Rect, highlighted: Boolean, active: Boolean, color: Color) {}

    context(renderParam: Unit, ctx: DslScaleContext)
    override fun renderSlot(rect: Rect) {}

    context(renderParam: Unit, ctx: DslScaleContext)
    override fun renderContainer(rect: Rect) {}

    context(renderParam: Unit, ctx: DslScaleContext)
    override fun renderTooltip(rect: Rect) {}

    context(renderParam: Unit, ctx: DslScaleContext)
    override fun renderItem(rect: Rect, item: String, count: Int, damage: Double?, enchanted: Boolean) {}

    context(ctx: DslScaleContext, renderParam: Unit)
    override fun renderDefaultBackground(rect: Rect) {}

    context(renderParam: Unit)
    override fun withScissor(rect: Rect, block: () -> Unit) = block()

    context(renderParam: Unit)
    override fun withTransform(transform: Transform, block: () -> Unit) = block()

    override fun getFont(name: String?) = font

    /**
     * A screen that exists (the framework builds one whenever someone shows a DSL screen) but is
     * never displayed. Skipping the construction would leave no [io.github.u2894638479.kotlinmcui.container.DslScreen]
     * to hand back, and the holder has no way to express "none".
     */
    override fun create(title: String, dslFunction: DslFunction): DslBackendScreenHolder<Unit> {
        val dataStore = DslDataStore(this, title, {}, dslFunction)
        return object : DslBackendScreenHolder<Unit> {
            override fun show() {}
            override val screen = Unit
            override val dslScreen = dataStore.dslScreen
        }
    }

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

    override fun isKeyDown(key: Key) = false

    override fun isMouseDown(mouse: Int) = false

    override val mouse = Position(0.px, 0.px)

    override fun setPreeditArea(rect: Rect) {}
}
