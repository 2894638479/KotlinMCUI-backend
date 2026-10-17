package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.DslBackendInput
import io.github.u2894638479.kotlinmcui.backend.DslBackendRenderer
import io.github.u2894638479.kotlinmcui.backend.DslBackendScreenHolder
import io.github.u2894638479.kotlinmcui.backend.DslRootProvider
import io.github.u2894638479.kotlinmcui.backend.DslRootScreenFactory
import io.github.u2894638479.kotlinmcui.backend.DslRootUtils
import io.github.u2894638479.kotlinmcui.container.DslDataStore
import io.github.u2894638479.kotlinmcui.container.DslRoot
import io.github.u2894638479.kotlinmcui.dsl.DslFunction

/**
 * The per-output half of this backend: what a root needs in order to be drawn, fed input and shown
 * screens. The process-level half is [Platform].
 */
internal object RootProvider : DslRootProvider<Unit, Unit> {
    override val renderer: DslBackendRenderer<Unit>? get() = HeadlessRenderer
    override val input: DslBackendInput get() = HeadlessInput
    override val screenFactory: DslRootScreenFactory<Unit>? get() = HeadlessScreenFactory
    override val rootUtils: DslRootUtils get() = HeadlessUtils

    /**
     * A frame on a side with no output draws nothing, and owns no viewport to hand the root: there
     * is no output size to write into [DslRoot.rect], so writing one would be inventing a size.
     */
    override fun frame(root: DslRoot, renderParam: Unit) {}
}

/**
 * A screen that exists (the framework builds one whenever someone shows a DSL screen) but is never
 * displayed. Skipping the construction would leave no [io.github.u2894638479.kotlinmcui.container.DslScreen]
 * to hand back, and the holder has no way to express "none".
 */
internal object HeadlessScreenFactory : DslRootScreenFactory<Unit> {
    override fun create(title: String, dslFunction: DslFunction): DslBackendScreenHolder<Unit> {
        val dataStore = DslDataStore(Platform, RootProvider, title, {}, dslFunction)
        return object : DslBackendScreenHolder<Unit> {
            override fun show() {}
            override val screen = Unit
            override val dslScreen = dataStore.dslScreen
        }
    }
}
