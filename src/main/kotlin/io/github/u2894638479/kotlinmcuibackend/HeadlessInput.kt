package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.DslBackendInput
import io.github.u2894638479.kotlinmcui.input.Key
import io.github.u2894638479.kotlinmcui.math.Position
import io.github.u2894638479.kotlinmcui.math.px
import io.github.u2894638479.kotlinmcui.math.rect.Rect

/** Nothing is ever pressed on a side with no keyboard or pointer, so every answer is "no". */
internal object HeadlessInput : DslBackendInput {
    override fun isKeyDown(key: Key) = false

    override fun isMouseDown(mouse: Int) = false

    override val mouse = Position(0.px, 0.px)

    override fun setPreeditArea(rect: Rect) {}
}
