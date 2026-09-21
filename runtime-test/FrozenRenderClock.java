package com.misanthropy.hit_indicator.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class FrozenRenderClock {
    private record Sample(int tickCount, float partialTick) {}
    private record Frozen(long token, float partialTick) {}

    private static final Map<Integer, Sample> LAST_NORMAL = new HashMap<>();
    private static final Map<Integer, Frozen> FROZEN = new HashMap<>();

    private FrozenRenderClock() {}

    public static float partialFor(Entity entity, float originalPartialTick) {
        if (!(entity instanceof LivingEntity living)) {
            return originalPartialTick;
        }

        int id = entity.getId();
        WindupTracker.Windup windup = WindupTracker.active().get(id);
        boolean freeze = windup != null && WindupTracker.shouldFreeze(living);

        if (!freeze) {
            FROZEN.remove(id);
            LAST_NORMAL.put(id, new Sample(entity.tickCount, originalPartialTick));
            return originalPartialTick;
        }

        long token = windup.startGameTime;
        Frozen existing = FROZEN.get(id);
        if (existing != null && existing.token == token) {
            return existing.partialTick;
        }

        Sample last = LAST_NORMAL.get(id);
        float captured = originalPartialTick;
        if (last != null && last.tickCount == entity.tickCount) {
            captured = last.partialTick;
        }

        FROZEN.put(id, new Frozen(token, captured));
        return captured;
    }

    public static void clear(Entity entity) {
        if (entity == null) return;
        int id = entity.getId();
        LAST_NORMAL.remove(id);
        FROZEN.remove(id);
    }
}
