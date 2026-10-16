package io.github.u2894638479.kotlinmcuibackend

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import io.github.u2894638479.kotlinmcui.backend.DslRuntime
import io.github.u2894638479.kotlinmcui.entry.DslEntryPage
import io.github.u2894638479.kotlinmcui.logger.dslLogger
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader

internal class Entry : ClientModInitializer {
    override fun onInitializeClient() {
        // Metadata is readable this early; the backend itself is published once the platform
        // can build screens (see MinecraftMixin), so nothing here may touch the client.
        DslRuntime.install { Metadata }
        if (ClientActivation.yieldsToWindowBackend()) {
            dslLogger.info("$WINDOW_MOD_ID is installed, so this backend yields the platform to it")
        }
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

/** The mod that renders into its own window and takes over from this one when present. */
internal const val WINDOW_MOD_ID = "kotlinmcuiwindow"

/**
 * Whether this backend stands down because the window backend owns the platform.
 *
 * The window mod depends on this one by id alone and both builds answer to that id, so the loader
 * installs either of them next to it and cannot tell them apart. When the window backend is the
 * one present, it publishes to the same global runtime and renders without the vanilla screen
 * stack, so this backend keeps its metadata installed and otherwise touches nothing - not even the
 * input callbacks, which would consult the shared component stack and swallow Minecraft's own keys
 * whenever a DSL screen is open in the window backend's window.
 *
 * Read from MinecraftMixin (Java), which runs once the window exists.
 */
internal object ClientActivation {
    @JvmStatic
    fun yieldsToWindowBackend(): Boolean = FabricLoader.getInstance().isModLoaded(WINDOW_MOD_ID)
}
