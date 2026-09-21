package com.misanthropy.hit_indicator.mixin.client;

import com.misanthropy.hit_indicator.client.FrozenRenderClock;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererTimeStopMixin {
    @Unique
    private Entity hit_indicator$currentRenderedEntity;

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
}
