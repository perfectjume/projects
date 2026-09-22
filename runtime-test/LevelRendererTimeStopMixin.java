package com.misanthropy.hit_indicator.mixin.client;

import com.misanthropy.hit_indicator.client.EntityRenderContext;
import com.misanthropy.hit_indicator.client.FrozenRenderClock;
import com.misanthropy.hit_indicator.client.StasisDesaturationRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererTimeStopMixin {
    @Unique
    private Entity hit_indicator$currentRenderedEntity;

    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void hit_indicator$beginStasisDesaturation(
            Entity entity,
            double cameraX,
            double cameraY,
            double cameraZ,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            CallbackInfo ci) {
        EntityRenderContext.enter(entity);
        StasisDesaturationRenderer.begin(entity, bufferSource);
    }

    @ModifyArg(
            method = "renderEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
            index = 0)
    private Entity hit_indicator$captureRenderedEntity(Entity entity) {
        this.hit_indicator$currentRenderedEntity = entity;
        return entity;
    }

    @ModifyArg(
            method = "renderEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
            index = 5)
    private float hit_indicator$freezeEntityRenderTime(float partialTick) {
        Entity entity = this.hit_indicator$currentRenderedEntity;
        return entity == null ? partialTick : FrozenRenderClock.partialFor(entity, partialTick);
    }

    @Inject(method = "renderEntity", at = @At("RETURN"))
    private void hit_indicator$endStasisDesaturation(
            Entity entity,
            double cameraX,
            double cameraY,
            double cameraZ,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            CallbackInfo ci) {
        StasisDesaturationRenderer.end(entity, bufferSource);
        EntityRenderContext.exit(entity);
    }
}
