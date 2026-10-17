package io.github.u2894638479.kotlinmcuibackend.mixin;

import io.github.u2894638479.kotlinmcuibackend.Root;
import io.github.u2894638479.kotlinmcui.container.DslRoot;
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
        // Auto-driving the game's own HUD is this backend's policy, decided once when the platform
        // was published; a backend that did not publish draws nothing here.
        if (!Root.isDriving()) { instance.flush(); return; }
        // The root is built right after the platform is published; the hook may run before that.
        DslRoot root = Root.orNull();
        if (root != null) {
            instance.pose().pushPose();
            float f = 1 / (float)Minecraft.getInstance().getWindow().getGuiScale();
            instance.pose().scale(f,f,1f);
            Root.frame(root, instance);
            instance.pose().popPose();
        }
        instance.flush();
    }
}
