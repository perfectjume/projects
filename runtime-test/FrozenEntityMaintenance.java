package com.misanthropy.hit_indicator.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class FrozenEntityMaintenance {
    private FrozenEntityMaintenance() {}

    public static void sync(Entity entity) {
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();

        entity.xOld = x;
        entity.yOld = y;
        entity.zOld = z;
        entity.xo = x;
        entity.yo = y;
        entity.zo = z;

        entity.xRotO = entity.getXRot();
        entity.yRotO = entity.getYRot();
        entity.walkDistO = entity.walkDist;

        if (entity instanceof LivingEntity living) {
            living.yBodyRotO = living.yBodyRot;
            living.yHeadRotO = living.yHeadRot;
            living.oAttackAnim = living.attackAnim;
        }
    }
}
