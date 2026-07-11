package io.github.u2894638479.kotlinmcuibackend.mixin;

import io.github.u2894638479.kotlinmcuibackend.DefaultBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V"))
    void kotlinmcuibackend$render(GuiGraphics instance) {
        instance.pose().pushPose();
        float f = 1 / (float)Minecraft.getInstance().getWindow().getGuiScale();
        instance.pose().scale(f,f,1f);
        DefaultBackend.INSTANCE.render(instance);
        instance.pose().popPose();
        instance.flush();
    }
}
