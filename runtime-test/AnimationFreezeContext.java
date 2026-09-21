package com.misanthropy.hit_indicator.client;

import java.util.ArrayDeque;
import net.minecraft.world.entity.LivingEntity;

public final class AnimationFreezeContext {
    private static final ThreadLocal<ArrayDeque<LivingEntity>> CURRENT =
            ThreadLocal.withInitial(ArrayDeque::new);

    private AnimationFreezeContext() {}

    public static void push(LivingEntity entity) {
        CURRENT.get().push(entity);
    }

    public static void pop() {
        ArrayDeque<LivingEntity> stack = CURRENT.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) CURRENT.remove();
    }

    public static LivingEntity current() {
        ArrayDeque<LivingEntity> stack = CURRENT.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    public static boolean shouldFreezeCurrent() {
        LivingEntity entity = current();
        return entity != null && WindupTracker.shouldFreeze(entity);
    }
}
