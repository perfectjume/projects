package com.misanthropy.hit_indicator.client;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class EmfAnimationClockCompat {
    private static final float TIME_CYCLE = 27720.0F;
    private static final float DAY_TIME_CYCLE = 31415.0F;
    private static final float FRAME_COUNTER_CYCLE = 27720.0F;

    private static final Map<Entity, State> STATES = new WeakHashMap<>();

    private static int tickDeltaOverrides;
    private static int frameTimeOverrides;
    private static int absoluteOverrides;
    private static float maxFrozenTimeDrift;
    private static float maxFrozenFrameCounterDrift;
    private static float maxFrozenFrameTime;

    private EmfAnimationClockCompat() {}

    private static final class Channel {
        float offset;
        float held;
        boolean holding;
        boolean initialized;
    }

    private static final class State {
        final Channel time = new Channel();
        final Channel dayTime = new Channel();
        final Channel dayCount = new Channel();
        final Channel frameCounter = new Channel();

        float firstFrozenTime = Float.NaN;
        float firstFrozenFrameCounter = Float.NaN;
    }

    private static LivingEntity currentLiving() {
        Entity entity = EntityRenderContext.current();
        return entity instanceof LivingEntity living ? living : null;
    }

    private static boolean visualStasis(LivingEntity living) {
        return WindupTracker.shouldFreeze(living)
                || FrozenRenderClock.isReleaseBridge(living);
    }

    public static float adjustTickDelta(float raw) {
        LivingEntity living = currentLiving();
        if (living == null || !visualStasis(living)) {
            return raw;
        }
        tickDeltaOverrides++;
        return FrozenRenderClock.partialFor(living, raw);
    }

    public static float adjustFrameTime(float raw) {
        LivingEntity living = currentLiving();
        if (living == null || !visualStasis(living)) {
            return raw;
        }
        frameTimeOverrides++;
        maxFrozenFrameTime = Math.max(maxFrozenFrameTime, Math.abs(0.0F));
        return 0.0F;
    }

    public static float adjustTime(float raw) {
        LivingEntity living = currentLiving();
        if (living == null) return raw;
        State state = STATES.computeIfAbsent(living, ignored -> new State());
        float out = adjustAbsolute(state.time, raw, TIME_CYCLE, visualStasis(living));
        absoluteOverrides++;
        if (visualStasis(living)) {
            if (Float.isNaN(state.firstFrozenTime)) {
                state.firstFrozenTime = out;
            } else {
                maxFrozenTimeDrift = Math.max(maxFrozenTimeDrift,
                        cyclicDistance(out, state.firstFrozenTime, TIME_CYCLE));
            }
        } else {
            state.firstFrozenTime = Float.NaN;
        }
        return out;
    }

    public static float adjustDayTime(float raw) {
        LivingEntity living = currentLiving();
        if (living == null) return raw;
        State state = STATES.computeIfAbsent(living, ignored -> new State());
        absoluteOverrides++;
        return adjustAbsolute(state.dayTime, raw, DAY_TIME_CYCLE, visualStasis(living));
    }

    public static float adjustDayCount(float raw) {
        LivingEntity living = currentLiving();
        if (living == null) return raw;
        State state = STATES.computeIfAbsent(living, ignored -> new State());
        absoluteOverrides++;
        return adjustAbsolute(state.dayCount, raw, 0.0F, visualStasis(living));
    }

    public static float adjustFrameCounter(float raw) {
        LivingEntity living = currentLiving();
        if (living == null) return raw;
        State state = STATES.computeIfAbsent(living, ignored -> new State());
        float out = adjustAbsolute(state.frameCounter, raw, FRAME_COUNTER_CYCLE, visualStasis(living));
        absoluteOverrides++;
        if (visualStasis(living)) {
            if (Float.isNaN(state.firstFrozenFrameCounter)) {
                state.firstFrozenFrameCounter = out;
            } else {
                maxFrozenFrameCounterDrift = Math.max(maxFrozenFrameCounterDrift,
                        cyclicDistance(out, state.firstFrozenFrameCounter, FRAME_COUNTER_CYCLE));
            }
        } else {
            state.firstFrozenFrameCounter = Float.NaN;
        }
        return out;
    }

    private static float adjustAbsolute(Channel channel, float raw, float cycle, boolean hold) {
        float normalizedRaw = normalize(raw, cycle);

        if (hold) {
            if (!channel.holding) {
                channel.held = channel.initialized
                        ? normalize(normalizedRaw - channel.offset, cycle)
                        : normalizedRaw;
                channel.holding = true;
                channel.initialized = true;
            }
            return channel.held;
        }

        if (channel.holding) {
            channel.offset = difference(normalizedRaw, channel.held, cycle);
            channel.holding = false;
            channel.initialized = true;
            return channel.held;
        }

        if (!channel.initialized) {
            channel.initialized = true;
            return normalizedRaw;
        }

        return normalize(normalizedRaw - channel.offset, cycle);
    }

    private static float difference(float raw, float desired, float cycle) {
        if (cycle <= 0.0F) {
            return raw - desired;
        }
        return normalize(raw - desired, cycle);
    }

    private static float normalize(float value, float cycle) {
        if (cycle <= 0.0F) {
            return value;
        }
        float result = value % cycle;
        return result < 0.0F ? result + cycle : result;
    }

    private static float cyclicDistance(float a, float b, float cycle) {
        float d = Math.abs(a - b);
        return Math.min(d, cycle - d);
    }

    public static int tickDeltaOverrides() { return tickDeltaOverrides; }
    public static int frameTimeOverrides() { return frameTimeOverrides; }
    public static int absoluteOverrides() { return absoluteOverrides; }
    public static float maxFrozenTimeDrift() { return maxFrozenTimeDrift; }
    public static float maxFrozenFrameCounterDrift() { return maxFrozenFrameCounterDrift; }
    public static float maxFrozenFrameTime() { return maxFrozenFrameTime; }
}
