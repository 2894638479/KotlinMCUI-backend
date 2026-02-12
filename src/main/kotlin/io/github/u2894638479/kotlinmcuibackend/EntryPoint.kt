package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.InternalBackend
import io.github.u2894638479.kotlinmcui.backend.DslEntryPage
import io.github.u2894638479.kotlinmcui.backend.DslEntryService
import io.github.u2894638479.kotlinmcui.backend.createScreen
import io.github.u2894638479.kotlinmcui.dslBackendProvider
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.ConfigScreenHandler
import net.minecraftforge.fml.DistExecutor
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod

@OptIn(InternalBackend::class)
@Mod("kotlinmcuibackend")
internal class EntryPoint {
    init {
        dslBackendProvider = { defaultBackend }
        DslEntryService.loadServices()
        DslEntryService.services.forEach { it.initialize() }
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT){
            Runnable {
                ModLoadingContext.get().registerExtensionPoint(
                    ConfigScreenHandler.ConfigScreenFactory::class.java) {
                    ConfigScreenHandler.ConfigScreenFactory { _: Minecraft, _: Screen ->
                        defaultBackend.createScreen { DslEntryPage() }.screen
                    }
                }
            }
        }
    }
}