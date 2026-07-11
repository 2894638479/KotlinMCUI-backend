package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.*
import io.github.u2894638479.kotlinmcui.container.DslChild
import io.github.u2894638479.kotlinmcui.container.DslDataStore
import io.github.u2894638479.kotlinmcui.container.topComponent
import io.github.u2894638479.kotlinmcui.dsl.DslFunction
import io.github.u2894638479.kotlinmcui.math.px
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

@InternalBackend
object DefaultBackend : DslBackend<GuiGraphics, Screen>,
    DslBackendRenderer<GuiGraphics> by Renderer,
    DslBackendMetadata by Metadata,
    DslBackendUtils by Utils,
    DslBackendInput by Input
{
    private inline val mc get() = Minecraft.getInstance()

    fun render(guiGraphics: GuiGraphics) {
        context(guiGraphics, Input.mouse) {
            topComponent.rect.apply {
                left = 0.px
                top = 0.px
                right = mc.window.screenWidth.px
                bottom = mc.window.screenHeight.px
            }
            topComponent.render()
        }
    }

    private class WrappedScreen(title: String,dslFunction: DslFunction): Screen(Component.literal(title)) {
        val parent = mc.screen
        val dataStore = DslDataStore(
            DefaultBackend, title,
            { mc.execute { mc.setScreen(parent) } }, dslFunction
        )
        val dslScreen = dataStore.dslScreen
        var child: DslChild? = null
        override fun added() { child = topComponent.children.collect(dslScreen) }
        override fun removed() { topComponent.children.remove(child!!) }
        override fun onClose() { dslScreen.close() }
        override fun isPauseScreen() = dataStore.pauseGame
    }

    override fun create(title:String, dslFunction: DslFunction): DslBackendScreenHolder<Screen> = object: DslBackendScreenHolder<Screen> {
        override fun show() { mc.execute { mc.setScreen(screen) } }
        override val screen = WrappedScreen(title,dslFunction)
        override val dslScreen get() = screen.dslScreen
    }
}
