package io.github.u2894638479.kotlinmcui.backend

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import io.github.u2894638479.kotlinmcui.test.TestPage
import net.minecraft.client.gui.screens.Screen

class ModMenuEntry : ModMenuApi {
    override fun getModConfigScreenFactory() = ConfigScreenFactory {
        defaultBackend.createScreen { TestPage() }.screen as Screen
    }
}