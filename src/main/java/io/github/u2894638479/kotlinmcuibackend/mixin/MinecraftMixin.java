package io.github.u2894638479.kotlinmcuibackend.mixin;

import com.mojang.blaze3d.platform.Window;
import io.github.u2894638479.kotlinmcuibackend.ClientActivation;
import io.github.u2894638479.kotlinmcuibackend.Input;
import io.github.u2894638479.kotlinmcuibackend.Platform;
import io.github.u2894638479.kotlinmcuibackend.RootProvider;
import io.github.u2894638479.kotlinmcui.backend.DslRuntime;
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
        // The window backend owns the platform and the input callbacks when it is installed.
        if (ClientActivation.yieldsToWindowBackend()) return;
        Input.INSTANCE.register(window.getWindow());
        // The main root is built from the first registered provider, so registering comes first:
        // publishing the platform can already build a root, because a preview asks for a page.
        DslRuntime.INSTANCE.registerProvider(RootProvider.INSTANCE);
        // The window exists now, so screens can be built: publish the platform. The lambda is
        // lazy on purpose - forcing Platform here would initialize it too early.
        DslRuntime.INSTANCE.platformReady(() -> Platform.INSTANCE);
    }
}
