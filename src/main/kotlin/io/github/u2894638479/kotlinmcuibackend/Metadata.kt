package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.DslPlatformMetadata
import io.github.u2894638479.kotlinmcui.backend.Environment
import net.fabricmc.loader.api.FabricLoader

internal object Metadata: DslPlatformMetadata {
    override val configDir get() = FabricLoader.getInstance().configDir
    override val gameDir get() = FabricLoader.getInstance().gameDir
    override val gameVersion get() = FabricLoader.getInstance().rawGameVersion
    override val gameLoader get() = "fabric"

    /**
     * The real side, not a guess: the entry system filters its hooks by this, so a dedicated
     * server must report [Environment.SERVER] or the server-only entries never run.
     */
    override val environment get() = when (FabricLoader.getInstance().environmentType) {
        net.fabricmc.api.EnvType.CLIENT -> Environment.CLIENT
        else -> Environment.SERVER
    }
}
