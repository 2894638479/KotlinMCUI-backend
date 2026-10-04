package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.DslBackendInput
import io.github.u2894638479.kotlinmcui.backend.InternalBackend
import io.github.u2894638479.kotlinmcui.container.TopComponent
import io.github.u2894638479.kotlinmcui.input.Key
import io.github.u2894638479.kotlinmcui.input.KeyModifiers
import io.github.u2894638479.kotlinmcui.input.MouseButton
import io.github.u2894638479.kotlinmcui.logger.dslLogger
import io.github.u2894638479.kotlinmcui.math.Position
import io.github.u2894638479.kotlinmcui.math.px
import io.github.u2894638479.kotlinmcui.math.rect.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.*
import org.lwjgl.glfw.GLFW.*
import kotlin.io.path.Path

/**
 * GLFW key code -> framework key, for every key the framework names.
 *
 * Anything else is passed through in the framework's custom range: [Key.custom] only accepts
 * codes from [Key.CUSTOM_RANGE_START] up, so a raw GLFW code (space, punctuation, numpad …)
 * must not reach it.
 */
private val glfwToKey: Map<Int, Key> = buildMap {
    put(GLFW_KEY_ENTER, Key.ENTER)
    put(GLFW_KEY_ESCAPE, Key.ESCAPE)
    put(GLFW_KEY_BACKSPACE, Key.BACKSPACE)
    put(GLFW_KEY_DELETE, Key.DELETE)
    put(GLFW_KEY_TAB, Key.TAB)
    put(GLFW_KEY_LEFT, Key.LEFT)
    put(GLFW_KEY_RIGHT, Key.RIGHT)
    put(GLFW_KEY_UP, Key.UP)
    put(GLFW_KEY_DOWN, Key.DOWN)
    put(GLFW_KEY_HOME, Key.HOME)
    put(GLFW_KEY_END, Key.END)
    put(GLFW_KEY_PAGE_UP, Key.PAGE_UP)
    put(GLFW_KEY_PAGE_DOWN, Key.PAGE_DOWN)
    put(GLFW_KEY_INSERT, Key.INSERT)
    put(GLFW_KEY_LEFT_SHIFT, Key.LEFT_SHIFT)
    put(GLFW_KEY_RIGHT_SHIFT, Key.RIGHT_SHIFT)
    put(GLFW_KEY_LEFT_CONTROL, Key.LEFT_CTRL)
    put(GLFW_KEY_RIGHT_CONTROL, Key.RIGHT_CTRL)
    put(GLFW_KEY_LEFT_ALT, Key.LEFT_ALT)
    put(GLFW_KEY_RIGHT_ALT, Key.RIGHT_ALT)
    put(GLFW_KEY_LEFT_SUPER, Key.LEFT_SUPER)
    put(GLFW_KEY_RIGHT_SUPER, Key.RIGHT_SUPER)
    for (i in 0..25) put(GLFW_KEY_A + i, Key.custom(Key.A.code + i))
    for (i in 0..9) put(GLFW_KEY_0 + i, Key.custom(Key.N0.code + i))
    for (i in 0..24) put(GLFW_KEY_F1 + i, Key.custom(Key.F1.code + i))
}

/** The reverse of [glfwToKey], for [Input.isKeyDown]. */
private val keyToGlfw: Map<Int, Int> = glfwToKey.entries.associate { (glfw, key) -> key.code to glfw }

private fun glfwKeyToKey(glfwCode: Int) =
    glfwToKey[glfwCode] ?: Key.custom(Key.CUSTOM_RANGE_START + glfwCode)

/** Translate a GLFW modifier bitmask to [KeyModifiers]. */
private fun glfwModsToKeyModifiers(mods: Int) = KeyModifiers(
    shift = (mods and GLFW_MOD_SHIFT) != 0,
    ctrl = (mods and GLFW_MOD_CONTROL) != 0,
    alt = (mods and GLFW_MOD_ALT) != 0,
    superKey = (mods and GLFW_MOD_SUPER) != 0,
    capsLock = (mods and GLFW_MOD_CAPS_LOCK) != 0,
    numLock = (mods and GLFW_MOD_NUM_LOCK) != 0
)

@OptIn(InternalBackend::class)
internal object Input : DslBackendInput {
    override var mouse = Position(0.px,0.px)
        private set

    override fun isKeyDown(key: Key): Boolean {
        val glfwCode = keyToGlfw[key.code] ?: (key.code - Key.CUSTOM_RANGE_START)
        return GLFW.glfwGetKey(Minecraft.getInstance().window.window, glfwCode) == GLFW_PRESS
    }

    override fun isMouseDown(mouse: Int) =
        GLFW.glfwGetMouseButton(Minecraft.getInstance().window.window, mouse) == GLFW_PRESS

    override fun setPreeditArea(rect: Rect) {
        // GLFW 3.3 (1.20.1) has no preedit-cursor API: the candidate window is positioned by
        // the platform itself. A backend on a newer GLFW positions it here instead.
    }

    private inline val top get() = TopComponent
    // Taken from Utils rather than DefaultBackend: DefaultBackend's class initializer reads
    // Input's delegating fields, so going through it here would make the two classes
    // initialize each other and leave one of them half-built.
    private val scope = CoroutineScope(Utils.mainDispatcher)

    fun register(window: Long) {
        // Logged so a failed mixin application is visible: without it the game still starts,
        // the framework just never receives any input.
        dslLogger.info("input callbacks registered")
        var cursorPosCallback: GLFWCursorPosCallbackI? = null
        cursorPosCallback = GLFW.glfwSetCursorPosCallback(window) { window,x,y ->
            val mouse = Position(x.px,y.px)
            this.mouse = mouse
            context(mouse) { top.mouseMove() }
            cursorPosCallback!!.invoke(window,x,y)
        }!!

        var mouseButtonCallback: GLFWMouseButtonCallbackI? = null
        mouseButtonCallback = GLFW.glfwSetMouseButtonCallback(window) { window,button,action,mods ->
            scope.launch {
                val result = context(mouse, glfwModsToKeyModifiers(mods)) {
                    when(action) {
                        GLFW_PRESS -> top.mouseDown(MouseButton.from(button))
                        GLFW_RELEASE -> top.mouseUp(MouseButton.from(button))
                        else -> error("unknown mouse action $action")
                    }
                }
                if(!result) mouseButtonCallback!!(window,button,action,mods)
            }
        }

        var scrollCallback: GLFWScrollCallbackI? = null
        scrollCallback = GLFW.glfwSetScrollCallback(window) { window,x,y ->
            scope.launch {
                context(mouse) {
                    val remainX = top.mouseScrollHorizontal(x)
                    val remainY = top.mouseScrollVertical(y)
                    if(remainX != 0.0 || remainY != 0.0) {
                        scrollCallback!!(window,x,y)
                    }
                }
            }
        }

        var dropCallback: GLFWDropCallbackI? = null
        dropCallback = GLFW.glfwSetDropCallback(window) { window,n,ptr ->
            scope.launch {
                val paths = Array(n) { Path(GLFWDropCallback.getName(ptr,it)) }
                context(mouse) {
                    if(!top.dropFiles(paths)) {
                        dropCallback!!(window,n,ptr)
                    }
                }
            }
        }

        var keyCallback: GLFWKeyCallbackI? = null
        keyCallback = GLFW.glfwSetKeyCallback(window) { window,glfwKey,scancode,action,mods ->
            scope.launch {
                val key = glfwKeyToKey(glfwKey)
                val result = context(glfwModsToKeyModifiers(mods)) {
                    when(action) {
                        GLFW_PRESS,GLFW_REPEAT -> top.keyDown(key,scancode)
                        GLFW_RELEASE -> top.keyUp(key,scancode)
                        else -> error("unknown key action $action")
                    }
                }
                if(!result) keyCallback!!(window,glfwKey,scancode,action,mods)
            }
        }

        var charModsCallback: GLFWCharModsCallbackI? = null
        charModsCallback = GLFW.glfwSetCharModsCallback(window) { window,codepoint,mods ->
            scope.launch {
                context(glfwModsToKeyModifiers(mods)) {
                    if(!top.charTyped(Char(codepoint))) {
                        charModsCallback!!(window,codepoint,mods)
                    }
                }
            }
        }
    }
}
