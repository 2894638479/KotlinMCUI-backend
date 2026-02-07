package io.github.u2894638479.kotlinmcui.backend

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import io.github.u2894638479.kotlinmcui.test.TestPage

class ModMenuEntry : ModMenuApi {
    override fun getModConfigScreenFactory() = ConfigScreenFactory {
        defaultBackend.createScreen { TestPage() }.screen
    }
}