package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.DslPlatformMetadata
import io.github.u2894638479.kotlinmcui.backend.DslPlatform
import io.github.u2894638479.kotlinmcui.backend.DslPlatformUtils
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The process-level half of this backend: the facts and helpers that belong to the platform rather
 * than to one output. The per-output half is [Root].
 *
 * Deliberately free of any Minecraft type: that is what lets one jar cover every game version.
 */
internal object Platform : DslPlatform {
    override val metadata: DslPlatformMetadata get() = Metadata
    override val utils: DslPlatformUtils get() = HeadlessUtils

    /** There is no game thread here, so the sessions run on the backend's own single thread. */
    override val dispatcher: CoroutineDispatcher get() = HeadlessUtils.sessionDispatcher
}
