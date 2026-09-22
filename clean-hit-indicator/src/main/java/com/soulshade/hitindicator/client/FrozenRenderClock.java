package com.soulshade.hitindicator.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class FrozenRenderClock {
    private record Sample(int tickCount, float partialTick) {}
    private record Frozen(long token, float partialTick) {}
    private record Release(float partialTick, long bridgeGameTime) {}

    private static final Map<Integer, Sample> LAST_NORMAL = new HashMap<>();
    private static final Map<Integer, Frozen> FROZEN = new HashMap<>();
    private static final Map<Integer, Release> RELEASE = new HashMap<>();

    private FrozenRenderClock() {}

    public static float partialFor(Entity entity, float originalPartialTick) {
        if (!(entity instanceof LivingEntity)) {
            return originalPartialTick;
        }

        int id = entity.getId();
        TelegraphState.Active active = TelegraphState.get(id);

        if (active != null) {
            RELEASE.remove(id);
            Frozen existing = FROZEN.get(id);
            if (existing != null && existing.token == active.token()) {
                return existing.partialTick;
            }

            Sample last = LAST_NORMAL.get(id);
            float captured = originalPartialTick;
            if (last != null && last.tickCount == entity.tickCount) {
                captured = last.partialTick;
            }

            FROZEN.put(id, new Frozen(active.token(), captured));
            return captured;
        }

        Release release = RELEASE.get(id);
        if (release != null) {
            float live = Math.max(0.0F, Math.min(1.0F, originalPartialTick));
            return release.partialTick + (1.0F - release.partialTick) * live;
        }

        Frozen frozen = FROZEN.get(id);
        if (frozen != null) {
            return frozen.partialTick;
        }

        LAST_NORMAL.put(id, new Sample(entity.tickCount, originalPartialTick));
        return originalPartialTick;
    }

    public static boolean shouldHoldReleaseTick(LivingEntity entity) {
        int id = entity.getId();

        if (TelegraphState.isFrozen(id)) {
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

        RELEASE.put(id, new Release(frozen.partialTick, gameTime));
        return true;
    }

    public static boolean isReleaseBridge(Entity entity) {
        return entity != null && RELEASE.containsKey(entity.getId());
    }

    public static void clear(Entity entity) {
        if (entity == null) return;
        int id = entity.getId();
        LAST_NORMAL.remove(id);
        FROZEN.remove(id);
        RELEASE.remove(id);
    }

    public static void clearAll() {
        LAST_NORMAL.clear();
        FROZEN.clear();
        RELEASE.clear();
    }
}
