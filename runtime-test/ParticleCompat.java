package com.misanthropy.hit_indicator.server;

import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;

/** 1.21.1 particle-option syntax adapter for the original {state} config contract. */
public final class ParticleCompat {
    private ParticleCompat() {}

    public static String groundParticleSpec(String configured, BlockState state) {
        if (configured == null || !configured.contains("{state}")) return configured;

        String stateSnbt = NbtUtils.writeBlockState(state).toString();
        String trimmed = configured.trim();
        int placeholder = trimmed.indexOf("{state}");
        if (placeholder >= 0) {
            String prefix = trimmed.substring(0, placeholder);
            String particleId = prefix.stripTrailing();
            boolean legacyWhitespaceForm = particleId.length() < prefix.length() && particleId.indexOf('{') < 0;
            if (legacyWhitespaceForm && !particleId.isEmpty()) {
                String suffix = trimmed.substring(placeholder + "{state}".length()).trim();
                String modern = particleId + "{block_state:" + stateSnbt + "}";
                return suffix.isEmpty() ? modern : modern + " " + suffix;
            }
        }

        return configured.replace("{state}", stateSnbt);
    }
}
