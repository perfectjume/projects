package com.misanthropy.hit_indicator.mixin.client;

import com.misanthropy.hit_indicator.client.EmfAnimationClockCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "traben.entity_model_features.models.animation.EMFAnimationEntityContext", remap = false)
public abstract class EmfAnimationClockMixin {
    @Inject(method = "getTickDelta", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void hit_indicator$freezeTickDelta(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(EmfAnimationClockCompat.adjustTickDelta(cir.getReturnValue()));
    }

    @Inject(method = "getFrameTime", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void hit_indicator$freezeFrameTime(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(EmfAnimationClockCompat.adjustFrameTime(cir.getReturnValue()));
    }

    @Inject(method = "getTime", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void hit_indicator$freezeTime(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(EmfAnimationClockCompat.adjustTime(cir.getReturnValue()));
    }

    @Inject(method = "getDayTime", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void hit_indicator$freezeDayTime(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(EmfAnimationClockCompat.adjustDayTime(cir.getReturnValue()));
    }

    @Inject(method = "getDayCount", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void hit_indicator$freezeDayCount(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(EmfAnimationClockCompat.adjustDayCount(cir.getReturnValue()));
    }

    @Inject(method = "getFrameCounter", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void hit_indicator$freezeFrameCounter(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(EmfAnimationClockCompat.adjustFrameCounter(cir.getReturnValue()));
    }
}
