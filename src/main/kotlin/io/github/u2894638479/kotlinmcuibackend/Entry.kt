package io.github.u2894638479.kotlinmcuibackend

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import io.github.u2894638479.kotlinmcui.backend.DslRuntime
import io.github.u2894638479.kotlinmcui.backend.InternalBackend
import io.github.u2894638479.kotlinmcui.entry.DslEntryPage
import net.fabricmc.api.ClientModInitializer

@InternalBackend
class Entry : ClientModInitializer, ModMenuApi {
    override fun onInitializeClient() {
        // Metadata is readable this early; the backend itself is published once the platform
        // can build screens (see MinecraftMixin), so nothing here may touch the client.
        DslRuntime.install({ Metadata })
        Verify.init()
    }

    override fun getModConfigScreenFactory() = ConfigScreenFactory {
        DefaultBackend.create("DSL Entry") { DslEntryPage() }.screen
    }
}
