package io.github.u2894638479.kotlinmcuibackend

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import io.github.u2894638479.kotlinmcui.InternalBackend
import io.github.u2894638479.kotlinmcui.backend.createScreen
import io.github.u2894638479.kotlinmcui.dslBackendProvider
import io.github.u2894638479.kotlinmcui.entry.DslEntryLoader
import io.github.u2894638479.kotlinmcui.entry.DslEntryPage
import net.fabricmc.api.ClientModInitializer

@OptIn(InternalBackend::class)
class Entry : ClientModInitializer, ModMenuApi {
    override fun onInitializeClient() = DslEntryLoader.run {
        initCommon()
        initClient()
        initGui()
        dslBackendProvider = { DefaultBackend }
    }

    override fun getModConfigScreenFactory() = ConfigScreenFactory {
        DefaultBackend.createScreen { DslEntryPage() }.screen
    }
}