package io.github.u2894638479.kotlinmcuibackend.mixin;

import com.mojang.blaze3d.platform.Window;
import io.github.u2894638479.kotlinmcuibackend.Input;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow
    @Final
    private Window window;

    @Inject(method = "<init>", at = @At("RETURN"))
    void kotlinmcuibackend$registerEventCallbacks(GameConfig gameConfig, CallbackInfo ci) {
        Input.INSTANCE.register(window.handle());
    }
}
