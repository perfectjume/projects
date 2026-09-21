package com.misanthropy.hit_indicator.mixin.client;

import com.misanthropy.hit_indicator.client.AnimationFreezeContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRenderContextMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void hit_indicator$beginLivingRender(
            LivingEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci) {
        AnimationFreezeContext.push(entity);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void hit_indicator$endLivingRender(
            LivingEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci) {
        AnimationFreezeContext.pop();
    }
}
