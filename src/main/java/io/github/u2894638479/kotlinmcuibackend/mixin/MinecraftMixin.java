package io.github.u2894638479.kotlinmcuibackend.mixin;

import com.mojang.blaze3d.platform.Window;
import io.github.u2894638479.kotlinmcuibackend.ClientActivation;
import io.github.u2894638479.kotlinmcuibackend.Input;
import io.github.u2894638479.kotlinmcuibackend.Platform;
import io.github.u2894638479.kotlinmcuibackend.Root;
import io.github.u2894638479.kotlinmcui.backend.DslPreview;
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
        // The window exists now, so screens can be built: publish the platform. The lambda is lazy
        // on purpose - forcing Platform here would initialize it too early.
        boolean published = DslRuntime.INSTANCE.start(() -> Platform.INSTANCE);
        // The launch hooks belong to whoever published the platform, and `start` deliberately does
        // not run them: a test or a standalone tool publishes a platform without a game to launch.
        if (published) DslRuntime.INSTANCE.launch();
        // The root belongs to the backend that built it, so it is built whatever published the
        // platform: another backend owning the platform no longer makes this one unable to work.
        Root.INSTANCE.create();
        // Driving the game's own HUD is a policy, and it needs this backend to have published the
        // platform. This build also stands down when the window backend is installed, which is a
        // property of the installation rather than of the ordering.
        Root.setDriving(published && !ClientActivation.yieldsToWindowBackend());
        if (!Root.isDriving()) return;
        // The preview is a tool rather than a process fact: it draws into the root it is handed and
        // reads no global. It starts only on the branch that drives - a preview on a root nobody
        // renders would wait for a frame that never comes - and does nothing unless the launch
        // properties asked for a page.
        DslPreview.INSTANCE.start(Root.INSTANCE.get());
        Input.INSTANCE.register(window.getWindow());
    }
}
