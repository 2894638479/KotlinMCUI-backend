package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.*
import io.github.u2894638479.kotlinmcui.container.DslChild
import io.github.u2894638479.kotlinmcui.container.DslDataStore
import io.github.u2894638479.kotlinmcui.container.DslRoot
import io.github.u2894638479.kotlinmcui.container.ViewerLifetime
import io.github.u2894638479.kotlinmcui.dsl.DslFunction
import io.github.u2894638479.kotlinmcui.math.px
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * The per-output half of this backend: what a root needs in order to be drawn, fed input and shown
 * screens. The process-level half is [Platform].
 */
internal object RootProvider : DslRootProvider<GuiGraphics, Screen> {
    override val renderer: DslBackendRenderer<GuiGraphics>? get() = Renderer
    override val input: DslBackendInput get() = Input
    override val screenFactory: DslRootScreenFactory<Screen>? get() = ScreenFactory
    override val rootUtils: DslRootUtils get() = Utils

    /**
     * The root this backend drives.
     *
     * The process owner root belongs to whichever backend registered first, so a backend that
     * registers later must not drive it - it would render another backend's tree. Which root is
     * this one's is decided by the runtime from the registration order: deciding it here would
     * mean reimplementing the same ordering test in every backend, and getting it wrong is silent.
     */
    @Volatile
    private var acquiredRoot: DslRoot? = null

    /**
     * Resolve this backend's root: the process root when this provider registered first, one of
     * its own otherwise.
     *
     * Call it after [DslRuntime.registerProvider] - the runtime asks whether this provider is the
     * first registered one - and after the platform is published.
     */
    @JvmStatic
    fun acquireRoot() {
        acquiredRoot = DslRuntime.rootFor(this, ViewerLifetime.PROCESS)
    }

    /**
     * Resolve a root of this backend's own, for when another backend holds the process root.
     *
     * Never asks for the process root: on the yielding path that root is not this backend's to
     * drive, and it may not even be buildable yet.
     */
    @JvmStatic
    fun acquireOwnRoot() {
        acquiredRoot = DslRuntime.createRoot(this, ViewerLifetime.PROCESS)
    }

    /** This backend's root; fails loudly when [acquireRoot] has not run. */
    @JvmStatic
    fun root(): DslRoot = acquiredRoot ?: error("RootProvider.acquireRoot has not run")

    /** Null until [acquireRoot] ran, for the frame hook that may run first. */
    @JvmStatic
    fun rootOrNull(): DslRoot? = acquiredRoot

    override fun frame(root: DslRoot, renderParam: GuiGraphics) {
        // The HUD draws through the game's own GUI rendering, and while the loading overlay is
        // up that state is not ready yet: drawing now submits against a pose the game has not
        // set up, and (on backends that compile pipelines eagerly) can poison their cache.
        if (mc.overlay != null) return
        // A provider without a renderer still has a well-defined frame: it draws nothing.
        val renderer = renderer ?: return
        context(renderer, renderParam, Input.mouse) {
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
}

internal object ScreenFactory : DslRootScreenFactory<Screen> {
    override fun create(title: String, dslFunction: DslFunction): DslBackendScreenHolder<Screen> =
        object : DslBackendScreenHolder<Screen> {
            override fun show() { mc.execute { mc.setScreen(screen) } }
            override val screen = WrappedScreen(title, dslFunction)
            override val dslScreen get() = screen.dslScreen
        }
}

private inline val mc get() = Minecraft.getInstance()

private class WrappedScreen(title: String, dslFunction: DslFunction) : Screen(Component.literal(title)) {
    val parent = mc.screen
    val dataStore = DslDataStore(
        Platform, RootProvider, title,
        { mc.execute { mc.setScreen(parent) } }, dslFunction
    )
    val dslScreen = dataStore.dslScreen
    var child: DslChild? = null
    override fun added() { child = RootProvider.root().children.collect(dslScreen) }
    override fun removed() { RootProvider.root().children.remove(child!!) }
    override fun onClose() { dslScreen.close() }
    override fun isPauseScreen() = dataStore.pauseGame
    // The framework paints its own background (the defaultBackground decorator), so the
    // vanilla dirt overlay must not end up underneath it.
    override fun renderBackground(guiGraphics: GuiGraphics) {}
}
