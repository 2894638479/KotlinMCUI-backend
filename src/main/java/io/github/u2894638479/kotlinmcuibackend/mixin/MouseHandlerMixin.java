package io.github.u2894638479.kotlinmcuibackend.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onPress",at = @At("HEAD"))
    void kotlinmcuibackend$mixinPress(long l, int i, int j, int k, CallbackInfo ci) {
        io.github.u2894638479.kotlinmcuibackend.DefaultBackendKt.setEventModifier(k);
    }
}
