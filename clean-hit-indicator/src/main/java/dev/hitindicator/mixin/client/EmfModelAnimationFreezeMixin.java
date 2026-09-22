package dev.hitindicator.mixin.client;

import dev.hitindicator.client.EmfPoseFreeze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartRoot", remap = false)
public abstract class EmfModelAnimationFreezeMixin {
    @Inject(
            method = "animate()V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void hit_indicator$restorePose(CallbackInfo ci) {
        if (EmfPoseFreeze.beforeAnimate(this)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "animate()V",
            at = @At("RETURN"),
            require = 0,
            remap = false)
    private void hit_indicator$capturePose(CallbackInfo ci) {
        EmfPoseFreeze.afterAnimate(this);
    }
}
