package com.example.examplemod.mixin;

import com.example.examplemod.MobAnimationProbe;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Mob.class, priority = 1000)
public abstract class MobAnimationProbeMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void hitindicatortest$simulateExternalAnimationHeadTick(CallbackInfo ci) {
        MobAnimationProbe.hit();
    }
}
