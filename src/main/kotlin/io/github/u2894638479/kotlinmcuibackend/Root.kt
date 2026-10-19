package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.*
import io.github.u2894638479.kotlinmcui.container.DslChild
import io.github.u2894638479.kotlinmcui.container.DslDataStore
import io.github.u2894638479.kotlinmcui.container.DslRoot
import io.github.u2894638479.kotlinmcui.dsl.DslFunction
import io.github.u2894638479.kotlinmcui.math.px
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * This backend's root: the one output it owns.
 *
 * The runtime hands out no roots, so a backend builds its own from the pieces it already has. That
 * is what makes "draw another backend's tree" unrepresentable rather than merely discouraged, and
 * it is why nothing here has to ask who published the platform first.
 */
internal object Root {
    @Volatile
    private var instance: DslRoot? = null

    /** Whether this backend drives the game's own HUD. Set once, when the platform is published. */
    @Volatile
    private var driving = false

    /** Build this backend's root. Call it once the platform can create screens. */
    @JvmStatic
    fun create() {
        instance = DslRoot(Input, Renderer, ScreenFactory, Utils)
    }

    /** This root; fails loudly when [create] has not run. */
    @JvmStatic
    fun get(): DslRoot = instance ?: error("Root.create has not run")

    /** Null before [create], for the frame hook that may run first. */
    @JvmStatic
    fun orNull(): DslRoot? = instance

    /** Whether auto-driving the game's own HUD is this backend's policy. */
    @JvmStatic
    fun isDriving(): Boolean = driving

    @JvmStatic
    fun setDriving(value: Boolean) {
        driving = value
    }

    /**
     * One frame of this root, inside the render context the platform gives it.
     *
     * The root owns its viewport: writing the rect comes first, and the render pass copies it onto
     * every mounted screen, so the order between the two is the contract.
     */
    @JvmStatic
    fun frame(root: DslRoot, renderParam: GuiGraphics) {
        // Cache maintenance is not drawing, so it runs even while the guard below holds the frame
        // back: this is the one place that is known to happen every frame, on the render thread.
        Utils.sweepLocalImages()
        // The HUD draws through the game's own GUI rendering, and while the loading overlay is up
        // that state is not ready yet: drawing now submits against a pose the game has not set up,
        // and (on backends that compile pipelines eagerly) can poison their cache.
        if (mc.overlay != null) return
        val started = System.nanoTime()
        context(Renderer, renderParam, Input.mouse) {
            root.rect.apply {
                left = 0.px
                top = 0.px
                right = mc.window.screenWidth.px
                bottom = mc.window.screenHeight.px
            }
            root.component.render()
        }
        if(Renderer.tracingFlushes) {
            val count = Renderer.takeFrameFlushes()
            val nanos = Renderer.takeFrameFlushNanos()
            val frame = System.nanoTime() - started
            println("FLUSHFRAME count=$count us=${nanos / 1000} frame_us=${frame / 1000}")
        }
    }
}

/** Creates the screens this root holds. */
internal object ScreenFactory : DslRootScreenFactory<Screen> {
    override fun create(title: String, dslFunction: DslFunction): DslScreenHolder<Screen> =
        object : DslScreenHolder<Screen> {
            override fun show() { mc.execute { mc.setScreen(screen) } }
            override val screen = WrappedScreen(title, dslFunction)
            override val dslScreen get() = screen.dslScreen
        }
}

private inline val mc get() = Minecraft.getInstance()

private class WrappedScreen(title: String, dslFunction: DslFunction) : Screen(Component.literal(title)) {
    val parent = mc.screen
    val dataStore = DslDataStore(
        Utils, Root.get(), title,
        { mc.execute { mc.setScreen(parent) } }, dslFunction
    )
    val dslScreen = dataStore.dslScreen
    var child: DslChild? = null
    override fun added() { child = Root.get().children.collect(dslScreen) }
    override fun removed() { Root.get().children.remove(child!!) }
    override fun onClose() { dslScreen.close() }
    override fun isPauseScreen() = dataStore.pauseGame
    // The framework paints its own background (the defaultBackground decorator), so the
    // vanilla dirt overlay must not end up underneath it.
    override fun renderBackground(guiGraphics: GuiGraphics) {}
}
