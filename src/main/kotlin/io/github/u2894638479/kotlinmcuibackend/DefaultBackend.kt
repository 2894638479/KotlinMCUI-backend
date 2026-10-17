package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.*
import io.github.u2894638479.kotlinmcui.container.DslChild
import io.github.u2894638479.kotlinmcui.container.DslDataStore
import io.github.u2894638479.kotlinmcui.dsl.DslFunction
import io.github.u2894638479.kotlinmcui.math.px
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

internal object DefaultBackend : DslBackend<GuiGraphics, Screen>,
    DslBackendRenderer<GuiGraphics> by Renderer,
    DslBackendMetadata by Metadata,
    DslBackendUtils by Utils,
    DslBackendInput by Input
{
    private inline val mc get() = Minecraft.getInstance()

    fun render(guiGraphics: GuiGraphics) {
        // The HUD draws through the game's own GUI rendering, and while the loading overlay is
        // up that state is not ready yet: drawing now submits against a pose the game has not
        // set up, and (on backends that compile pipelines eagerly) can poison their cache.
        if (mc.overlay != null) return
        val root = DslRuntime.ownerRoot
        context(guiGraphics, Input.mouse) {
            // The root owns its viewport: writing the rect comes first, and the render pass copies
            // it onto every mounted screen, so the order between the two is the contract.
            root.rect.apply {
                left = 0.px
                top = 0.px
                right = mc.window.screenWidth.px
                bottom = mc.window.screenHeight.px
            }
            root.component.render()
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
        override fun added() { child = DslRuntime.ownerRoot.children.collect(dslScreen) }
        override fun removed() { DslRuntime.ownerRoot.children.remove(child!!) }
        override fun onClose() { dslScreen.close() }
        override fun isPauseScreen() = dataStore.pauseGame
        // The framework paints its own background (the defaultBackground decorator), so the
        // vanilla dirt overlay must not end up underneath it.
        override fun renderBackground(guiGraphics: GuiGraphics) {}
    }

    override fun create(title:String, dslFunction: DslFunction): DslBackendScreenHolder<Screen> = object: DslBackendScreenHolder<Screen> {
        override fun show() { mc.execute { mc.setScreen(screen) } }
        override val screen = WrappedScreen(title,dslFunction)
        override val dslScreen get() = screen.dslScreen
    }
}
