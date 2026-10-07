package io.github.u2894638479.kotlinmcuibackend

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import io.github.u2894638479.kotlinmcui.backend.DslRuntime
import io.github.u2894638479.kotlinmcui.entry.DslEntryPage
import net.fabricmc.api.ClientModInitializer

internal class Entry : ClientModInitializer {
    override fun onInitializeClient() {
        // Metadata is readable this early; the backend itself is published once the platform
        // can build screens (see MinecraftMixin), so nothing here may touch the client.
        DslRuntime.install { Metadata }
    }

    /**
     * Mod Menu's config-screen hook. It is a class of its own so that Mod Menu stays optional: the
     * loader resolves the `modmenu` entrypoint only when Mod Menu is installed, while [Entry]
     * itself is loaded on every client start and must not mention a class that may be absent.
     */
    class ModMenu : ModMenuApi {
        override fun getModConfigScreenFactory() = ConfigScreenFactory {
            DefaultBackend.create("DSL Entry") { DslEntryPage() }.screen
        }
    }
}
