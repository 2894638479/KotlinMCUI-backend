package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.DslBackendMetadata
import net.fabricmc.loader.api.FabricLoader

internal val metadata = object: DslBackendMetadata {
    override val configDir get() = FabricLoader.getInstance().configDir
    override val gameDir get() = FabricLoader.getInstance().gameDir
    override val gameVersion get() = FabricLoader.getInstance().rawGameVersion
    override val gameLoader get() = "fabric"
}