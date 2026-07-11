package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.backend.DslBackendMetadata
import io.github.u2894638479.kotlinmcui.backend.Environment
import net.minecraftforge.fml.loading.FMLLoader
import net.minecraftforge.fml.loading.FMLPaths

object Metadata: DslBackendMetadata {
    override val configDir get() = FMLPaths.CONFIGDIR.get()
    override val gameDir get() = FMLPaths.GAMEDIR.get()
    override val gameVersion get() = FMLLoader.versionInfo().mcVersion
    override val gameLoader get() = "forge"
    override val environment get() = Environment.CLIENT
}