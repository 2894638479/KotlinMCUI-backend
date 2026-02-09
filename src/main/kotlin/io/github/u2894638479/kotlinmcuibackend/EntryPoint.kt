package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.InternalBackend
import io.github.u2894638479.kotlinmcui.backend.DslEntryPage
import io.github.u2894638479.kotlinmcui.backend.DslEntryService
import io.github.u2894638479.kotlinmcui.backend.createScreen
import io.github.u2894638479.kotlinmcui.dslBackendProvider
import net.neoforged.fml.ModContainer
import net.neoforged.fml.ModList
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.gui.IConfigScreenFactory

@OptIn(InternalBackend::class)
@Mod("kotlinmcuibackend")
internal class EntryPoint {
    init {
        dslBackendProvider = { defaultBackend }
        DslEntryService.loadServices()
        DslEntryService.services.forEach { it.initialize() }
        ModList.get().getModContainerById("kotlinmcuibackend").ifPresent { container: ModContainer ->
            container.registerExtensionPoint(IConfigScreenFactory::class.java) {
                IConfigScreenFactory { _, _ -> defaultBackend.createScreen { DslEntryPage() }.screen }
            }
        }
    }
}