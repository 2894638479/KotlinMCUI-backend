package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.DslBackendInput
import io.github.u2894638479.kotlinmcui.backend.InternalBackend
import io.github.u2894638479.kotlinmcui.container.topComponent
import io.github.u2894638479.kotlinmcui.glfw.EventModifier
import io.github.u2894638479.kotlinmcui.glfw.MouseButton
import io.github.u2894638479.kotlinmcui.math.Position
import io.github.u2894638479.kotlinmcui.math.px
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.*
import org.lwjgl.glfw.GLFW.*
import kotlin.io.path.Path

@OptIn(InternalBackend::class)
internal object Input: DslBackendInput {
    override var mouse = Position(0.px,0.px)
        private set
    override fun isKeyDown(key: Int) = GLFW.glfwGetKey(Minecraft.getInstance().window.window,key) == GLFW_PRESS
    override fun isMouseDown(mouse: Int) = GLFW.glfwGetMouseButton(Minecraft.getInstance().window.window,mouse) == GLFW_PRESS

    private inline val top get() = topComponent
    private val scope = CoroutineScope(Utils.mainDispatcher)
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
                val result = context(mouse, EventModifier(mods)) {
                    when(action) {
                        GLFW_PRESS -> top.mouseDown(MouseButton.from(button))
                        GLFW_RELEASE -> top.mouseUp(MouseButton.from(button))
                        else -> error("unknown mouse action$action")
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
        keyCallback = GLFW.glfwSetKeyCallback(window) { window,key,scancode,action,mods ->
            scope.launch {
                val result = context(EventModifier(mods)) {
                    when(action) {
                        GLFW_PRESS,GLFW_REPEAT -> top.keyDown(key,scancode)
                        GLFW_RELEASE -> top.keyUp(key,scancode)
                        else -> error("unknown key action$action")
                    }
                }
                if(!result) keyCallback!!(window,key,scancode,action,mods)
            }
        }

        var charModsCallback: GLFWCharModsCallbackI? = null
        charModsCallback = GLFW.glfwSetCharModsCallback(window) { window,codepoint,mods ->
            scope.launch {
                context(EventModifier(mods)) {
                    if(!top.charTyped(Char(codepoint))) {
                        charModsCallback!!(window,codepoint,mods)
                    }
                }
            }
        }
    }
}