package io.github.u2894638479.kotlinmcuibackend

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import io.github.u2894638479.kotlinmcui.backend.InternalBackend
import io.github.u2894638479.kotlinmcui.backend.dslBackendProvider
import io.github.u2894638479.kotlinmcui.entry.DslEntryLoader
import io.github.u2894638479.kotlinmcui.entry.DslEntryPage
import net.fabricmc.api.ClientModInitializer

@InternalBackend
class Entry : ClientModInitializer, ModMenuApi {
    override fun onInitializeClient() {
        dslBackendProvider = { DefaultBackend }
        DslEntryLoader.init(Metadata.environment)
    }

    override fun getModConfigScreenFactory() = ConfigScreenFactory {
        DefaultBackend.create("DSL Entry") { DslEntryPage() }.screen
    }
}