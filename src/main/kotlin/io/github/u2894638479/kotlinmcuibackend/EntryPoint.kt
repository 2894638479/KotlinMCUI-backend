package io.github.u2894638479.kotlinmcuibackend

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import io.github.u2894638479.kotlinmcui.InternalBackend
import io.github.u2894638479.kotlinmcui.backend.DslEntryPage
import io.github.u2894638479.kotlinmcui.backend.DslEntryService
import io.github.u2894638479.kotlinmcui.backend.createScreen
import io.github.u2894638479.kotlinmcui.dslBackendProvider
import net.fabricmc.api.ModInitializer

@OptIn(InternalBackend::class)
internal class EntryPoint : ModInitializer, ModMenuApi {
    override fun onInitialize() {
        dslBackendProvider = { defaultBackend }
        DslEntryService.loadServices()
        DslEntryService.services.forEach { it.initialize() }
    }
    override fun getModConfigScreenFactory() = ConfigScreenFactory {
        defaultBackend.createScreen { DslEntryPage() }.screen
    }
}