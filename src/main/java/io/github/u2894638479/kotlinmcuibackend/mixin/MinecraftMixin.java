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
        // Registering the provider is a capability, not a policy: the main root is built from the
        // first registered provider, so this happens whatever publishes the platform.
        DslRuntime.INSTANCE.registerProvider(RootProvider.INSTANCE);
        // Driving the game's own HUD is a policy, and it stays with whoever owns the platform.
        if (ClientActivation.yieldsToWindowBackend()) {
            // Another backend owns the platform, so this one publishes nothing - but it still gets
            // a root of its own, so a caller driving a frame explicitly can work.
            RootProvider.INSTANCE.acquireOwnRoot();
            return;
        }
        Input.INSTANCE.register(window.getWindow());
        // The window exists now, so screens can be built: publish the platform. The lambda is
        // lazy on purpose - forcing Platform here would initialize it too early.
        DslRuntime.INSTANCE.platformReady(() -> Platform.INSTANCE);
        // The platform exists now, so this backend's root can be resolved.
        RootProvider.INSTANCE.acquireRoot();
    }
}
