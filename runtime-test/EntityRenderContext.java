package com.misanthropy.hit_indicator.client;

import java.util.ArrayDeque;
import net.minecraft.world.entity.Entity;

public final class EntityRenderContext {
    private static final ThreadLocal<ArrayDeque<Entity>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private EntityRenderContext() {}

    public static void enter(Entity entity) {
        if (entity != null) {
            STACK.get().push(entity);
        }
    }

    public static void exit(Entity entity) {
        ArrayDeque<Entity> stack = STACK.get();
        if (stack.isEmpty()) {
            return;
        }
        if (stack.peek() == entity) {
            stack.pop();
        } else {
            stack.removeFirstOccurrence(entity);
        }
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    public static Entity current() {
        ArrayDeque<Entity> stack = STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }
}
