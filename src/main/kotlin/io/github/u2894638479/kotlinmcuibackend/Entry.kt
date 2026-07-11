package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.InternalBackend
import io.github.u2894638479.kotlinmcui.backend.dslBackendProvider
import io.github.u2894638479.kotlinmcui.entry.DslEntryLoader
import io.github.u2894638479.kotlinmcui.entry.DslEntryPage
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.ModContainer
import net.neoforged.fml.ModList
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLLoader
import net.neoforged.neoforge.client.gui.IConfigScreenFactory

@OptIn(InternalBackend::class)
@Mod("kotlinmcuibackend")
internal class Entry {
    init {
        if(FMLLoader.getDist() == Dist.CLIENT) {
            dslBackendProvider = { DefaultBackend }
            DslEntryLoader.init(Metadata.environment)
            ModList.get().getModContainerById("kotlinmcuibackend").ifPresent { container: ModContainer ->
                container.registerExtensionPoint(IConfigScreenFactory::class.java) {
                    IConfigScreenFactory { _, _ -> DefaultBackend.create("DSL Entry") { DslEntryPage() }.screen }
                }
            }
        }
    }
}
