package com.misanthropy.hit_indicator.mixin.client;

import com.misanthropy.hit_indicator.client.WindupTracker;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityFreezeMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void hit_indicator$freezeWindup(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide && WindupTracker.shouldFreeze(self)) {
            ci.cancel();
        }
    }
}
