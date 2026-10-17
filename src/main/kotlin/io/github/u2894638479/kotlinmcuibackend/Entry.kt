package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.DslRuntime
import io.github.u2894638479.kotlinmcui.logger.dslLogger
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader

/**
 * The headless backend's only entrypoint.
 *
 * It is registered as `main`, so the loader runs it on a client and on a dedicated server
 * alike. Metadata is readable this early, so it installs the runtime here, registers the one
 * provider and publishes the platform - unless the window backend is installed, in which case
 * that mod publishes instead (asking for its id needs nothing but the loader, which is the whole
 * point of this backend being version independent).
 */
internal class Entry : ModInitializer {
    override fun onInitialize() {
        DslRuntime.install { Metadata }
        if (FabricLoader.getInstance().isModLoaded(WINDOW_MOD_ID)) {
            dslLogger.info("$WINDOW_MOD_ID is installed, so this backend stays headless and silent")
            return
        }
        // The provider has to be known before a platform can build a root against it.
        DslRuntime.registerProvider(RootProvider)
        DslRuntime.platformReady { Platform }
        dslLogger.info("headless backend published on ${Metadata.environment}")
    }
}

/** The mod that renders into its own window and takes over from this one when present. */
internal const val WINDOW_MOD_ID = "kotlinmcuiwindow"
