package dev.kosmx.playerAnim.core.impl;

public class AnimationProcessor {
    private float lastTickDelta = -1.0F;

    public void setTickDelta(float tickDelta) {
        this.lastTickDelta = tickDelta;
    }

    public float hitindicatortest$getLastTickDelta() {
        return lastTickDelta;
    }
}
