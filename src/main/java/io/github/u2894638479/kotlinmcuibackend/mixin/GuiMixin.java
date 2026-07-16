package io.github.u2894638479.kotlinmcuibackend.mixin;

import com.mojang.blaze3d.platform.Window;
import io.github.u2894638479.kotlinmcuibackend.DefaultBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
public class GuiMixin {
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;applyCursor(Lcom/mojang/blaze3d/platform/Window;)V"))
    void kotlinmcuibackend$render(GuiGraphicsExtractor instance, Window window) {
        instance.pose().pushMatrix();
        float f = 1 / (float)Minecraft.getInstance().getWindow().getGuiScale();
        instance.pose().scale(f,f);
        DefaultBackend.INSTANCE.render(instance);
        instance.pose().popMatrix();
        instance.nextStratum();
    }
}
