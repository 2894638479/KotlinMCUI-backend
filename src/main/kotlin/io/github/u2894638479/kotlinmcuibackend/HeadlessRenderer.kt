package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.DslBackendRenderer
import io.github.u2894638479.kotlinmcui.context.DslScaleContext
import io.github.u2894638479.kotlinmcui.image.ImageHolder
import io.github.u2894638479.kotlinmcui.math.Color
import io.github.u2894638479.kotlinmcui.math.Measure
import io.github.u2894638479.kotlinmcui.math.px
import io.github.u2894638479.kotlinmcui.math.rect.Rect
import io.github.u2894638479.kotlinmcui.math.transform.Transform
import io.github.u2894638479.kotlinmcui.text.DslFont
import io.github.u2894638479.kotlinmcui.text.DslGlyph
import io.github.u2894638479.kotlinmcui.text.DslRenderableChar

/**
 * Draws nothing, but still answers the two questions a layout asks of a renderer.
 *
 * The font advances by nothing, which keeps text measurement deterministic without ever painting
 * it - measurement is the only thing that still asks on a side that has no output. Keeping the
 * renderer non-null is what keeps [getFont] reachable: text reads its font from the renderer, so a
 * null renderer would take the font away with the drawing.
 */
internal object HeadlessRenderer : DslBackendRenderer<Unit> {
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
}
