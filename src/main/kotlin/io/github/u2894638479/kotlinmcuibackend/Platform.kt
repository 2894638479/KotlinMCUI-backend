package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.DslBackendMetadata
import io.github.u2894638479.kotlinmcui.backend.DslPlatform
import io.github.u2894638479.kotlinmcui.backend.DslPlatformUtils
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The process-level half of this backend: the facts and helpers that belong to the platform rather
 * than to one output. The per-output half is [Root].
 */
internal object Platform : DslPlatform {
    override val metadata: DslBackendMetadata get() = Metadata
    override val utils: DslPlatformUtils get() = Utils

    /** The game's render thread; a session here runs on the same thread that draws it. */
    override val dispatcher: CoroutineDispatcher get() = Utils.sessionDispatcher
}
