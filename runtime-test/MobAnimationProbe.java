package com.example.examplemod;

public final class MobAnimationProbe {
    private static int headTicks;

    private MobAnimationProbe() {}

    public static void hit() {
        headTicks++;
    }

    public static int count() {
        return headTicks;
    }
}
