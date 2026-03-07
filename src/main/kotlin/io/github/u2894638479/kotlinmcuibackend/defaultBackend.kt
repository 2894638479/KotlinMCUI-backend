package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.DslDataStore
import io.github.u2894638479.kotlinmcui.InternalBackend
import io.github.u2894638479.kotlinmcui.backend.DslBackend
import io.github.u2894638479.kotlinmcui.backend.DslBackendMetadata
import io.github.u2894638479.kotlinmcui.backend.DslBackendRenderer
import io.github.u2894638479.kotlinmcui.backend.DslBackendScreenHolder
import io.github.u2894638479.kotlinmcui.backend.DslBackendUtils
import io.github.u2894638479.kotlinmcui.context.DslScaleContext
import io.github.u2894638479.kotlinmcui.context.scaled
import io.github.u2894638479.kotlinmcui.functions.DslTopFunction
import io.github.u2894638479.kotlinmcui.glfw.EventModifier
import io.github.u2894638479.kotlinmcui.glfw.MouseButton
import io.github.u2894638479.kotlinmcui.math.Position
import io.github.u2894638479.kotlinmcui.math.px
import io.github.u2894638479.kotlinmcui.math.rect.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.nio.file.Path

internal var eventModifier:Int = 0

internal var horizontalScroller:((Double,Double,Double)-> Unit)? = null

@InternalBackend
val defaultBackend: DslBackend<GuiGraphics, Screen> = object : DslBackend<GuiGraphics, Screen>,
    DslBackendRenderer<GuiGraphics> by renderer,
    DslBackendMetadata by metadata,
    DslBackendUtils by utils
{
    override fun create(title:String, dslFunction: DslTopFunction): DslBackendScreenHolder<Screen> = object: DslBackendScreenHolder<Screen> {
        override fun show(){
            Minecraft.getInstance().execute {
                Minecraft.getInstance().setScreen(screen)
            }
        }
        override val screen = object : Screen(Component.literal(title)), DslScaleContext {
            override val scale get() = guiScale
            val parent = Minecraft.getInstance().screen
            fun DslBackend<*,*>.createDataStore() = DslDataStore(this,title, {
                Minecraft.getInstance().execute { Minecraft.getInstance().setScreen(parent) }
            },dslFunction)
            val dataStore = createDataStore()
            val dslScreen = dataStore.dslScreen
            override fun onClose() {
                horizontalScroller = null
                dslScreen.close()
            }

            override fun isPauseScreen() = dataStore.pauseGame
            override fun keyPressed(i: Int, j: Int, k: Int): Boolean {
                if(context(EventModifier(k)) { dslScreen.keyDown(i, j) }) return true
                return super.keyPressed(i, j, k)
            }
            override fun keyReleased(i: Int, j: Int, k: Int): Boolean {
                if(context(EventModifier(k)) { dslScreen.keyUp(i, j) }) return true
                return super.keyReleased(i, j, k)
            }
            override fun mouseClicked(d: Double, e: Double, i: Int): Boolean {
                if(context(EventModifier(eventModifier),Position(d.scaled, e.scaled)) {
                    dslScreen.mouseDown(MouseButton.from(i))
                }) return true
                return super.mouseClicked(d, e, i)
            }
            override fun mouseReleased(d: Double, e: Double, i: Int): Boolean {
                if(context(EventModifier(eventModifier),Position(d.scaled, e.scaled)) {
                    dslScreen.mouseUp(MouseButton.from(i))
                }) return true
                return super.mouseReleased(d, e, i)
            }
            override fun mouseMoved(d: Double, e: Double) {
                context(Position(d.scaled, e.scaled)) { dslScreen.mouseMove() }
                super.mouseMoved(d, e)
            }
            override fun mouseScrolled(d: Double, e: Double, f: Double): Boolean {
                val remain = context(Position(d.scaled, e.scaled)) {
                    dslScreen.mouseScrollVertical(f)
                }
                if(remain == 0.0) return true
                return super.mouseScrolled(d, e, remain)
            }
            override fun charTyped(c: Char, i: Int): Boolean {
                if(context(EventModifier(i)) { dslScreen.charTyped(c) }) return true
                return super.charTyped(c, i)
            }
            override fun render(guiGraphics: GuiGraphics, i: Int, j: Int, f: Float) {
                context(guiGraphics,Position(i.scaled, j.scaled)) {
                    guiGraphics.pose().pushPose()
                    guiGraphics.pose().scale(1/guiScale.toFloat(),1/guiScale.toFloat(),1f)
                    dslScreen.render()
                    guiGraphics.pose().popPose()
                }
            }
            override fun onFilesDrop(list: List<Path>) {
                if(context(dataStore.mouse) { dslScreen.dropFiles(list) }) return
                return super.onFilesDrop(list)
            }

            override fun init() {
                super.init()
                horizontalScroller = { x,y,f ->
                    context(Position(x.px, y.px)) {
                        dslScreen.mouseScrollHorizontal(f)
                    }
                }
                dslScreen.init(Rect(right = width.scaled, bottom = height.scaled))
            }
        }
    }
}