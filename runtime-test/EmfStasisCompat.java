package com.misanthropy.hit_indicator.client;

import java.lang.reflect.Method;
import java.util.function.Function;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

/**
 * Optional Entity Model Features integration.
 *
 * EMF exposes an official registerPauseCondition(Function<EMFEntity, Boolean>)
 * API. We use reflection so Hit Indicator does not gain a hard dependency on
 * EMF/ETF; when EMF is absent this class is inert.
 */
public final class EmfStasisCompat {
    private static boolean attempted;
    private static boolean registered;
    private static int evaluations;

    private static final Function<Object, Boolean> PAUSE_CONDITION = emfEntity -> {
        evaluations++;
        if (emfEntity instanceof LivingEntity living) {
            return WindupTracker.shouldFreeze(living)
                    || FrozenRenderClock.isReleaseBridge(living);
        }
        return false;
    };

    private EmfStasisCompat() {}

    public static void registerIfPresent() {
        if (attempted) {
            return;
        }
        attempted = true;

        if (!ModList.get().isLoaded("entity_model_features")) {
            return;
        }

        try {
            Class<?> api = Class.forName("traben.entity_model_features.EMFAnimationApi");
            Method register = api.getMethod("registerPauseCondition", Function.class);
            Object result = register.invoke(null, PAUSE_CONDITION);
            registered = Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            registered = false;
        }
    }

    public static boolean isRegistered() {
        return registered;
    }

    public static int evaluations() {
        return evaluations;
    }

    public static boolean evaluateForTest(Object entity) {
        return PAUSE_CONDITION.apply(entity);
    }
}
