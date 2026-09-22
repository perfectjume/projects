package dev.hitindicator.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class TelegraphState {
    public record Active(long token, long startGameTime, int durationTicks) {}

    private static final Map<Integer, Active> ACTIVE = new HashMap<>();
    private static long nextToken = 1L;

    private TelegraphState() {}

    public static void start(int entityId, int durationTicks, long gameTime) {
        ACTIVE.put(entityId, new Active(nextToken++, gameTime, Math.max(1, durationTicks)));
    }

    public static void end(int entityId) {
        ACTIVE.remove(entityId);
    }

    public static boolean isFrozen(int entityId) {
        return ACTIVE.containsKey(entityId);
    }

    public static Active get(int entityId) {
        return ACTIVE.get(entityId);
    }

    public static Map<Integer, Active> activeView() {
        return Collections.unmodifiableMap(ACTIVE);
    }

    public static float progress(Active active, long gameTime, float partialTick) {
        if (active == null) return 1.0F;
        float elapsed = (gameTime - active.startGameTime()) + partialTick;
        return Math.max(0.0F, Math.min(1.0F, elapsed / active.durationTicks()));
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
