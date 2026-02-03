package io.github.u2894638479.kotlinmcui.backend

import io.github.u2894638479.kotlinmcui.InternalBackend
import io.github.u2894638479.kotlinmcui.dslBackendProvider
import net.fabricmc.api.ModInitializer

class EntryPoint : ModInitializer {
    override fun onInitialize() {
        @OptIn(InternalBackend::class)
        dslBackendProvider = { defaultBackend }
    }
}