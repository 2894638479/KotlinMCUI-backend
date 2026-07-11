package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.InternalBackend
import io.github.u2894638479.kotlinmcui.backend.dslBackendProvider
import io.github.u2894638479.kotlinmcui.entry.DslEntryLoader
import io.github.u2894638479.kotlinmcui.entry.DslEntryPage
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.ConfigScreenHandler
import net.minecraftforge.fml.DistExecutor
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod

@InternalBackend
@Mod("kotlinmcuibackend")
internal class EntryPoint {
    init {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT){
            Runnable {
                dslBackendProvider = { DefaultBackend }
                DslEntryLoader.init(Metadata.environment)
                ModLoadingContext.get().registerExtensionPoint(
                    ConfigScreenHandler.ConfigScreenFactory::class.java) {
                    ConfigScreenHandler.ConfigScreenFactory { _: Minecraft, _: Screen ->
                        DefaultBackend.create("DSL Entry") { DslEntryPage() }.screen
                    }
                }
            }
        }
    }
}