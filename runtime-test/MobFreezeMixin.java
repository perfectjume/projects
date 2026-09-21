package com.misanthropy.hit_indicator.mixin.client;

import com.misanthropy.hit_indicator.client.WindupTracker;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Mob.class, priority = 500)
public abstract class MobFreezeMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void hit_indicator$freezeMobWindupBeforeAnimationLayers(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (self.level().isClientSide && WindupTracker.shouldFreeze(self)) {
            ci.cancel();
        }
    }
}
