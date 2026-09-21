package com.misanthropy.hit_indicator.mixin.client;

import com.misanthropy.hit_indicator.client.FrozenEntityMaintenance;
import com.misanthropy.hit_indicator.client.FrozenRenderClock;
import com.misanthropy.hit_indicator.client.WindupTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientLevel.class, priority = 500)
public abstract class ClientLevelTimeStopMixin {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void hit_indicator$stableTimeStop(Entity entity, CallbackInfo ci) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }

        if (WindupTracker.shouldFreeze(living)) {
            FrozenEntityMaintenance.sync(entity);
            ci.cancel();
            return;
        }

        if (FrozenRenderClock.shouldHoldReleaseTick(living)) {
            FrozenEntityMaintenance.sync(entity);
            ci.cancel();
        }
    }
}
