package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.DslBackendInput
import io.github.u2894638479.kotlinmcui.backend.InternalBackend
import io.github.u2894638479.kotlinmcui.container.topComponent
import io.github.u2894638479.kotlinmcui.input.Key
import io.github.u2894638479.kotlinmcui.input.KeyModifiers
import io.github.u2894638479.kotlinmcui.input.MouseButton
import io.github.u2894638479.kotlinmcui.math.Position
import io.github.u2894638479.kotlinmcui.math.px
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.*
import org.lwjgl.glfw.GLFW.*
import kotlin.io.path.Path

/** Translate a GLFW key code to the framework's platform-neutral [Key]. */
private fun glfwKeyToKey(glfwCode: Int): Key = when (glfwCode) {
    GLFW_KEY_ENTER -> Key.ENTER
    GLFW_KEY_ESCAPE -> Key.ESCAPE
    GLFW_KEY_BACKSPACE -> Key.BACKSPACE
    GLFW_KEY_DELETE -> Key.DELETE
    GLFW_KEY_TAB -> Key.TAB
    GLFW_KEY_LEFT -> Key.LEFT
    GLFW_KEY_RIGHT -> Key.RIGHT
    GLFW_KEY_UP -> Key.UP
    GLFW_KEY_DOWN -> Key.DOWN
    GLFW_KEY_HOME -> Key.HOME
    GLFW_KEY_END -> Key.END
    GLFW_KEY_PAGE_UP -> Key.PAGE_UP
    GLFW_KEY_PAGE_DOWN -> Key.PAGE_DOWN
    GLFW_KEY_INSERT -> Key.INSERT
    GLFW_KEY_LEFT_SHIFT -> Key.LEFT_SHIFT
    GLFW_KEY_RIGHT_SHIFT -> Key.RIGHT_SHIFT
    GLFW_KEY_LEFT_CONTROL -> Key.LEFT_CTRL
    GLFW_KEY_RIGHT_CONTROL -> Key.RIGHT_CTRL
    GLFW_KEY_LEFT_ALT -> Key.LEFT_ALT
    GLFW_KEY_RIGHT_ALT -> Key.RIGHT_ALT
    GLFW_KEY_LEFT_SUPER -> Key.LEFT_SUPER
    GLFW_KEY_RIGHT_SUPER -> Key.RIGHT_SUPER
    in GLFW_KEY_A..GLFW_KEY_Z -> Key.custom(Key.A.code + (glfwCode - GLFW_KEY_A))
    in GLFW_KEY_0..GLFW_KEY_9 -> Key.custom(Key.N0.code + (glfwCode - GLFW_KEY_0))
    in GLFW_KEY_F1..GLFW_KEY_F25 -> Key.custom(Key.F1.code + (glfwCode - GLFW_KEY_F1))
    else -> Key.custom(glfwCode)
}

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
        // Reverse-map Key back to a GLFW code for the query. Most keys map
        // to a single GLFW code; letters/numbers/F-keys use offset mapping.
        val glfwCode = when {
            key.code in 30..55 -> GLFW_KEY_A + (key.code - Key.A.code)
            key.code in 60..69 -> GLFW_KEY_0 + (key.code - Key.N0.code)
            key.code in 70..94 -> GLFW_KEY_F1 + (key.code - Key.F1.code)
            else -> key.code // Pass custom codes directly to GLFW
        }
        return GLFW.glfwGetKey(Minecraft.getInstance().window.window, glfwCode) == GLFW_PRESS
    }

    override fun isMouseDown(mouse: Int) =
        GLFW.glfwGetMouseButton(Minecraft.getInstance().window.window, mouse) == GLFW_PRESS

    private inline val top get() = topComponent
    private val scope = CoroutineScope(DefaultBackend.mainDispatcher)

    fun register(window: Long) {
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
