package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.DslScreenHolder
import io.github.u2894638479.kotlinmcui.backend.DslRootScreenFactory
import io.github.u2894638479.kotlinmcui.container.DslDataStore
import io.github.u2894638479.kotlinmcui.container.DslRoot
import io.github.u2894638479.kotlinmcui.dsl.DslFunction

/**
 * This backend's root: the one output it owns.
 *
 * The runtime hands out no roots, so a backend builds its own from the pieces it already has. This
 * one draws nothing, but it is still a root: text measures against [HeadlessRenderer]'s font, and
 * a null renderer there would take the font away with the drawing.
 */
internal object Root {
    @Volatile
    private var instance: DslRoot? = null

    /** Build this backend's root. */
    @JvmStatic
    fun create() {
        instance = DslRoot(HeadlessInput, HeadlessRenderer, HeadlessScreenFactory, HeadlessUtils)
    }

    /** This root; fails loudly when [create] has not run. */
    @JvmStatic
    fun get(): DslRoot = instance ?: error("Root.create has not run")

    /** Null before [create]. */
    @JvmStatic
    fun orNull(): DslRoot? = instance
}

/**
 * A screen that exists (the framework builds one whenever someone shows a DSL screen) but is never
 * displayed. Skipping the construction would leave no [io.github.u2894638479.kotlinmcui.container.DslScreen]
 * to hand back, and the holder has no way to express "none".
 */
internal object HeadlessScreenFactory : DslRootScreenFactory<Unit> {
    override fun create(title: String, dslFunction: DslFunction): DslScreenHolder<Unit> {
        val dataStore = DslDataStore(Platform, Root.get(), title, {}, dslFunction)
        return object : DslScreenHolder<Unit> {
            override fun show() {}
            override val screen = Unit
            override val dslScreen = dataStore.dslScreen
        }
    }
}
