package io.github.u2894638479.kotlinmcuibackend.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    private double xpos;
    @Shadow
    private double ypos;

    @Inject(method = "onScroll",at = @At("HEAD"))
    void kotlinmcuibackend$mixinScroll(long l, double d, double e, CallbackInfo ci){
        if (l == Minecraft.getInstance().getWindow().getWindow()) {
            double f = (minecraft.options.discreteMouseScroll().get() ? Math.signum(d) : d) * minecraft.options.mouseWheelSensitivity().get();
            var func = io.github.u2894638479.kotlinmcuibackend.DefaultBackendKt.getHorizontalScroller();
            if (func != null && f != 0) func.invoke(xpos, ypos, f);
        }
    }

    @Inject(method = "onPress",at = @At("HEAD"))
    void kotlinmcuibackend$mixinPress(long l, int i, int j, int k, CallbackInfo ci) {
        io.github.u2894638479.kotlinmcuibackend.DefaultBackendKt.setEventModifier(k);
    }
}
