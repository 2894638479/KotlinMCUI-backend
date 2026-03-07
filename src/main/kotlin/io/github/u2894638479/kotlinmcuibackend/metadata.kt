package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.DslBackendMetadata
import net.minecraftforge.fml.loading.FMLLoader
import net.minecraftforge.fml.loading.FMLPaths

internal val metadata = object: DslBackendMetadata {
    override val configDir get() = FMLPaths.CONFIGDIR.get()
    override val gameDir get() = FMLPaths.GAMEDIR.get()
    override val gameVersion get() = FMLLoader.versionInfo().mcVersion
    override val gameLoader get() = "forge"
}