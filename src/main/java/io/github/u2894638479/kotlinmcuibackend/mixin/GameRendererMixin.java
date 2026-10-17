package io.github.u2894638479.kotlinmcuibackend.mixin;

import io.github.u2894638479.kotlinmcuibackend.ClientActivation;
import io.github.u2894638479.kotlinmcuibackend.RootProvider;
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
        // Driving the game's own HUD is a policy, and it stays with whoever owns the platform:
        // with the window backend installed this one registered its provider and draws nothing.
        if (ClientActivation.yieldsToWindowBackend()) { instance.flush(); return; }
        // The root is resolved once the platform exists; the hook may run before that.
        DslRoot root = RootProvider.INSTANCE.rootOrNull();
        if (root != null) {
            instance.pose().pushPose();
            float f = 1 / (float)Minecraft.getInstance().getWindow().getGuiScale();
            instance.pose().scale(f,f,1f);
            RootProvider.INSTANCE.frame(root, instance);
            instance.pose().popPose();
        }
        instance.flush();
    }
}
