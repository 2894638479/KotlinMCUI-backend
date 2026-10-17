package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.DslBackendInput
import io.github.u2894638479.kotlinmcui.backend.DslRuntime
import io.github.u2894638479.kotlinmcui.input.Key
import io.github.u2894638479.kotlinmcui.input.KeyModifiers
import io.github.u2894638479.kotlinmcui.input.MouseButton
import io.github.u2894638479.kotlinmcui.logger.dslLogger
import io.github.u2894638479.kotlinmcui.math.Position
import io.github.u2894638479.kotlinmcui.math.px
import io.github.u2894638479.kotlinmcui.math.rect.Rect
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.*
import org.lwjgl.glfw.GLFW.*
import kotlin.io.path.Path

/**
 * GLFW key code -> framework [Key] code, for the keys the framework names.
 *
 * A code the framework does not name passes through unchanged: that is what its custom range
 * is for, and `Key(code)` performs no validation. Building the mapped entries with `Key.custom`
 * would be wrong twice over - its argument is a framework code, not a GLFW code, and it
 * rejects anything below the custom range.
 */
private val glfwToKey = Int2IntOpenHashMap().apply {
    put(GLFW_KEY_ENTER, Key.ENTER.code)
    put(GLFW_KEY_ESCAPE, Key.ESCAPE.code)
    put(GLFW_KEY_BACKSPACE, Key.BACKSPACE.code)
    put(GLFW_KEY_DELETE, Key.DELETE.code)
    put(GLFW_KEY_TAB, Key.TAB.code)
    put(GLFW_KEY_LEFT, Key.LEFT.code)
    put(GLFW_KEY_RIGHT, Key.RIGHT.code)
    put(GLFW_KEY_UP, Key.UP.code)
    put(GLFW_KEY_DOWN, Key.DOWN.code)
    put(GLFW_KEY_HOME, Key.HOME.code)
    put(GLFW_KEY_END, Key.END.code)
    put(GLFW_KEY_PAGE_UP, Key.PAGE_UP.code)
    put(GLFW_KEY_PAGE_DOWN, Key.PAGE_DOWN.code)
    put(GLFW_KEY_INSERT, Key.INSERT.code)
    put(GLFW_KEY_LEFT_SHIFT, Key.LEFT_SHIFT.code)
    put(GLFW_KEY_RIGHT_SHIFT, Key.RIGHT_SHIFT.code)
    put(GLFW_KEY_LEFT_CONTROL, Key.LEFT_CTRL.code)
    put(GLFW_KEY_RIGHT_CONTROL, Key.RIGHT_CTRL.code)
    put(GLFW_KEY_LEFT_ALT, Key.LEFT_ALT.code)
    put(GLFW_KEY_RIGHT_ALT, Key.RIGHT_ALT.code)
    put(GLFW_KEY_LEFT_SUPER, Key.LEFT_SUPER.code)
    put(GLFW_KEY_RIGHT_SUPER, Key.RIGHT_SUPER.code)
    for (c in GLFW_KEY_A..GLFW_KEY_Z) put(c, Key.A.code + (c - GLFW_KEY_A))
    for (c in GLFW_KEY_0..GLFW_KEY_9) put(c, Key.N0.code + (c - GLFW_KEY_0))
    for (c in GLFW_KEY_F1..GLFW_KEY_F25) put(c, Key.F1.code + (c - GLFW_KEY_F1))
}

/** [Key] code -> GLFW key code, for [Input.isKeyDown]. */
private val keyToGlfw = Int2IntOpenHashMap().also { reverse -> glfwToKey.forEach { k, v -> reverse.put(v, k) } }

/** Translate a GLFW key code to the framework's platform-neutral [Key]. */
private fun glfwKeyToKey(glfwCode: Int) = Key(glfwToKey.getOrDefault(glfwCode,glfwCode))

/** Translate a GLFW modifier bitmask to [KeyModifiers]. */
private fun glfwModsToKeyModifiers(mods: Int) = KeyModifiers(
    shift = (mods and GLFW_MOD_SHIFT) != 0,
    ctrl = (mods and GLFW_MOD_CONTROL) != 0,
    alt = (mods and GLFW_MOD_ALT) != 0,
    superKey = (mods and GLFW_MOD_SUPER) != 0,
    capsLock = (mods and GLFW_MOD_CAPS_LOCK) != 0,
    numLock = (mods and GLFW_MOD_NUM_LOCK) != 0
)

/**
 * Run the framework's handling of one platform event and report whether it consumed the event.
 *
 * The callbacks are already on the thread the framework builds its tree on, so there is nothing to
 * defer and no coroutine is involved. What needs protecting is the hand-off to the platform's own
 * callback below: without this, one failure in the framework takes the whole of that hand-off with
 * it, and the event disappears. A failure is logged and reported as "not handled", so the platform
 * still receives the event.
 */
private inline fun <T> guarded(what: String, fallback: T, block: () -> T): T =
    try {
        block()
    } catch(e: Exception) {
        dslLogger.error("kotlinmcui failed to handle $what", e)
        fallback
    }

internal object Input : DslBackendInput {
    override var mouse = Position(0.px,0.px)
        private set

    override fun isKeyDown(key: Key): Boolean {
        val glfwCode = keyToGlfw.getOrDefault(key.code,key.code)
        return GLFW.glfwGetKey(Minecraft.getInstance().window.window, glfwCode) == GLFW_PRESS
    }

    override fun isMouseDown(mouse: Int) =
        GLFW.glfwGetMouseButton(Minecraft.getInstance().window.window, mouse) == GLFW_PRESS

    override fun setPreeditArea(rect: Rect) {
        // GLFW 3.3 (1.20.1) has no preedit-cursor API: the candidate window is positioned by
        // the platform itself. A backend on a newer GLFW positions it here instead.
    }

    private inline val top get() = Root.get().component

    // Called from MinecraftMixin (Java) once the window exists. Kotlin visibility does not reach
    // Java, so this member is the deliberate entry point of the input path.
    fun register(window: Long) {
        // Touch the key tables here on purpose: a malformed table then fails at startup, where
        // the verification run sees it, instead of inside a callback on the first key press.
        dslLogger.info("input callbacks registered (${glfwToKey.size} keys mapped)")
        var cursorPosCallback: GLFWCursorPosCallbackI? = null
        cursorPosCallback = GLFW.glfwSetCursorPosCallback(window) { window,x,y ->
            val mouse = Position(x.px,y.px)
            this.mouse = mouse
            guarded("a cursor move", Unit) { context(mouse) { top.mouseMove() } }
            cursorPosCallback!!.invoke(window,x,y)
        }!!

        var mouseButtonCallback: GLFWMouseButtonCallbackI? = null
        mouseButtonCallback = GLFW.glfwSetMouseButtonCallback(window) { window,button,action,mods ->
            val result = guarded("a mouse button", false) {
                context(mouse, glfwModsToKeyModifiers(mods)) {
                    when(action) {
                        GLFW_PRESS -> top.mouseDown(MouseButton.from(button))
                        GLFW_RELEASE -> top.mouseUp(MouseButton.from(button))
                        else -> error("unknown mouse action $action")
                    }
                }
            }
            if(!result) mouseButtonCallback!!(window,button,action,mods)
        }

        var scrollCallback: GLFWScrollCallbackI? = null
        scrollCallback = GLFW.glfwSetScrollCallback(window) { window,x,y ->
            val consumed = guarded("a scroll", false) {
                context(mouse) {
                    val remainX = top.mouseScrollHorizontal(x)
                    val remainY = top.mouseScrollVertical(y)
                    remainX == 0.0 && remainY == 0.0
                }
            }
            if(!consumed) scrollCallback!!(window,x,y)
        }

        var dropCallback: GLFWDropCallbackI? = null
        dropCallback = GLFW.glfwSetDropCallback(window) { window,n,ptr ->
            val paths = Array(n) { Path(GLFWDropCallback.getName(ptr,it)) }
            val handled = guarded("a file drop", false) { context(mouse) { top.dropFiles(paths) } }
            if(!handled) dropCallback!!(window,n,ptr)
        }

        var keyCallback: GLFWKeyCallbackI? = null
        keyCallback = GLFW.glfwSetKeyCallback(window) { window,glfwKey,scancode,action,mods ->
            val key = glfwKeyToKey(glfwKey)
            val result = guarded("a key", false) {
                context(glfwModsToKeyModifiers(mods)) {
                    when(action) {
                        GLFW_PRESS,GLFW_REPEAT -> top.keyDown(key,scancode)
                        GLFW_RELEASE -> top.keyUp(key,scancode)
                        else -> error("unknown key action $action")
                    }
                }
            }
            if(!result) keyCallback!!(window,glfwKey,scancode,action,mods)
        }

        var charModsCallback: GLFWCharModsCallbackI? = null
        charModsCallback = GLFW.glfwSetCharModsCallback(window) { window,codepoint,mods ->
            val handled = guarded("a typed character", false) {
                context(glfwModsToKeyModifiers(mods)) { top.charTyped(Char(codepoint)) }
            }
            if(!handled) charModsCallback!!(window,codepoint,mods)
        }
    }
}
