package com.soulshade.hitindicator.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.soulshade.hitindicator.client.FrozenRenderClock;
import com.soulshade.hitindicator.client.StasisDesaturationRenderer;
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
public abstract class LevelRendererMixin {
    @Unique
    private Entity soulshade_hit_indicator$currentRenderedEntity;

    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void soulshade_hit_indicator$beginStasis(
            Entity entity,
            double cameraX,
            double cameraY,
            double cameraZ,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            CallbackInfo ci) {
        StasisDesaturationRenderer.begin(entity, bufferSource);
    }

    @ModifyArg(
            method = "renderEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
            index = 0)
    private Entity soulshade_hit_indicator$captureEntity(Entity entity) {
        this.soulshade_hit_indicator$currentRenderedEntity = entity;
        return entity;
    }

    @ModifyArg(
            method = "renderEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
            index = 5)
    private float soulshade_hit_indicator$freezePartialTick(float partialTick) {
        Entity entity = this.soulshade_hit_indicator$currentRenderedEntity;
        return entity == null ? partialTick : FrozenRenderClock.partialFor(entity, partialTick);
    }

    @Inject(method = "renderEntity", at = @At("RETURN"))
    private void soulshade_hit_indicator$endStasis(
            Entity entity,
            double cameraX,
            double cameraY,
            double cameraZ,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            CallbackInfo ci) {
        StasisDesaturationRenderer.end(entity, bufferSource);
    }
}
