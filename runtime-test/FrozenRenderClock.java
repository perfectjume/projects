package com.misanthropy.hit_indicator.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Holds the render-side fractional clock while an entity is frozen and hands
 * ownership of the Frozen -> Release transition to the client entity tick.
 */
public final class FrozenRenderClock {
    private record Sample(int tickCount, float partialTick) {}
    private record Frozen(long token, float partialTick) {}
    private record Release(long token, float partialTick, long bridgeGameTime) {}

    private static final Map<Integer, Sample> LAST_NORMAL = new HashMap<>();
    private static final Map<Integer, Frozen> FROZEN = new HashMap<>();
    private static final Map<Integer, Release> RELEASE = new HashMap<>();

    private FrozenRenderClock() {}

    public static float partialFor(Entity entity, float originalPartialTick) {
        if (!(entity instanceof LivingEntity living)) {
            return originalPartialTick;
        }

        int id = entity.getId();
        WindupTracker.Windup windup = WindupTracker.active().get(id);
        boolean freeze = windup != null && WindupTracker.shouldFreeze(living);

        if (freeze) {
            RELEASE.remove(id);
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

        Release release = RELEASE.get(id);
        if (release != null) {
            long gameTime = entity.level().getGameTime();
            if (gameTime == release.bridgeGameTime) {
                return Math.min(1.0F, release.partialTick + originalPartialTick);
            }
            if (gameTime > release.bridgeGameTime) {
                RELEASE.remove(id);
                FROZEN.remove(id);
                LAST_NORMAL.put(id, new Sample(entity.tickCount, originalPartialTick));
                return originalPartialTick;
            }
        }

        Frozen frozen = FROZEN.get(id);
        if (frozen != null) {
            // Render can observe shouldFreeze=false before ClientLevel gets its
            // next tickNonPassenger call. Keep the exact frozen fraction here;
            // tick-side shouldHoldReleaseTick() owns Frozen -> Release.
            return frozen.partialTick;
        }

        LAST_NORMAL.put(id, new Sample(entity.tickCount, originalPartialTick));
        return originalPartialTick;
    }

    /**
     * Called before the entity's normal client tick. If a fractional frozen pose
     * was rendered, hold one final entity tick so render time can advance from
     * that fraction to the next integer boundary without a discontinuity.
     */
    public static boolean shouldHoldReleaseTick(LivingEntity entity) {
        int id = entity.getId();

        if (WindupTracker.shouldFreeze(entity)) {
            return false;
        }

        long gameTime = entity.level().getGameTime();
        Release release = RELEASE.get(id);
        if (release != null) {
            if (gameTime == release.bridgeGameTime) {
                return true;
            }
            if (gameTime > release.bridgeGameTime) {
                RELEASE.remove(id);
                FROZEN.remove(id);
                return false;
            }
        }

        Frozen frozen = FROZEN.get(id);
        if (frozen == null) {
            return false;
        }

        RELEASE.put(id, new Release(frozen.token, frozen.partialTick, gameTime));
        return true;
    }

    public static boolean isReleaseBridge(Entity entity) {
        if (entity == null) return false;
        Release release = RELEASE.get(entity.getId());
        return release != null && entity.level().getGameTime() == release.bridgeGameTime;
    }

    public static float frozenPartial(Entity entity) {
        if (entity == null) return Float.NaN;
        Frozen frozen = FROZEN.get(entity.getId());
        return frozen == null ? Float.NaN : frozen.partialTick;
    }

    public static void clear(Entity entity) {
        if (entity == null) return;
        int id = entity.getId();
        LAST_NORMAL.remove(id);
        FROZEN.remove(id);
        RELEASE.remove(id);
    }
}
