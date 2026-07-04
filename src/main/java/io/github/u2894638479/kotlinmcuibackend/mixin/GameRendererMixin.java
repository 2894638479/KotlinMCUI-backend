package io.github.u2894638479.kotlinmcuibackend.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static io.github.u2894638479.kotlinmcuibackend.DefaultBackendKt.getRenderOverlay;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V"))
    void kotlinmcuibackend$renderOverlay(GuiGraphics instance) {
        getRenderOverlay().invoke(instance);
        instance.flush();
    }
}
