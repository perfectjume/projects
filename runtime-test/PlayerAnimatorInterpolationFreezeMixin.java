package com.misanthropy.hit_indicator.mixin.client;

import com.misanthropy.hit_indicator.client.AnimationFreezeContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "dev.kosmx.playerAnim.core.impl.AnimationProcessor", remap = false)
public abstract class PlayerAnimatorInterpolationFreezeMixin {
    @ModifyVariable(
            method = "setTickDelta(F)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0,
            remap = false)
    private float hit_indicator$freezePlayerAnimatorPartialTick(float partialTick) {
        return AnimationFreezeContext.shouldFreezeCurrent() ? 0.0F : partialTick;
    }
}
