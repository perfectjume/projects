package com.misanthropy.hit_indicator.client;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/**
 * Freezes the already-evaluated EMF model pose instead of pausing EMF's
 * animation engine. This preserves custom part visibility/scale/position.
 */
public final class EmfPoseFreeze {
    private record PartState(
            float x, float y, float z,
            float xRot, float yRot, float zRot,
            float xScale, float yScale, float zScale,
            boolean visible, boolean skipDraw) {

        static PartState capture(ModelPart part) {
            return new PartState(
                    part.x, part.y, part.z,
                    part.xRot, part.yRot, part.zRot,
                    part.xScale, part.yScale, part.zScale,
                    part.visible, part.skipDraw);
        }

        void restore(ModelPart part) {
            part.x = x;
            part.y = y;
            part.z = z;
            part.xRot = xRot;
            part.yRot = yRot;
            part.zRot = zRot;
            part.xScale = xScale;
            part.yScale = yScale;
            part.zScale = zScale;
            part.visible = visible;
            part.skipDraw = skipDraw;
        }
    }

    private record PoseSnapshot(List<PartState> states) {}

    private static final Map<Object, Map<UUID, PoseSnapshot>> SNAPSHOTS = new WeakHashMap<>();

    private static Method getCurrentEntityMethod;
    private static boolean reflectionResolved;

    private static int captures;
    private static int restores;
    private static int clears;
    private static int mismatches;

    private EmfPoseFreeze() {}

    /**
     * Called at EMFModelPartRoot.animate() HEAD.
     * @return true when animate() should be cancelled because the stored pose
     *         was restored.
     */
    public static boolean beforeAnimate(Object rootObject) {
        LivingEntity entity = currentLivingEntity();
        if (entity == null || !(rootObject instanceof ModelPart root)) {
            return false;
        }

        UUID uuid = entity.getUUID();
        if (!WindupTracker.shouldFreeze(entity)) {
            Map<UUID, PoseSnapshot> perEntity = SNAPSHOTS.get(rootObject);
            if (perEntity != null && perEntity.remove(uuid) != null) {
                clears++;
                if (perEntity.isEmpty()) {
                    SNAPSHOTS.remove(rootObject);
                }
            }
            return false;
        }

        Map<UUID, PoseSnapshot> perEntity = SNAPSHOTS.get(rootObject);
        PoseSnapshot snapshot = perEntity == null ? null : perEntity.get(uuid);
        if (snapshot == null) {
            // First frozen evaluation must run once so we capture EMF's fully
            // evaluated custom pose, rather than its reset/default state.
            return false;
        }

        List<ModelPart> parts = root.getAllParts().toList();
        if (parts.size() != snapshot.states.size()) {
            // Variant structure changed unexpectedly. Re-evaluate once and
            // replace the snapshot rather than applying the wrong hierarchy.
            perEntity.remove(uuid);
            mismatches++;
            return false;
        }

        for (int i = 0; i < parts.size(); i++) {
            snapshot.states.get(i).restore(parts.get(i));
        }
        restores++;
        return true;
    }

    /**
     * Called at EMFModelPartRoot.animate() RETURN.
     */
    public static void afterAnimate(Object rootObject) {
        LivingEntity entity = currentLivingEntity();
        if (entity == null || !(rootObject instanceof ModelPart root)) {
            return;
        }
        if (!WindupTracker.shouldFreeze(entity)) {
            return;
        }

        UUID uuid = entity.getUUID();
        Map<UUID, PoseSnapshot> perEntity =
                SNAPSHOTS.computeIfAbsent(rootObject, ignored -> new java.util.HashMap<>());
        if (perEntity.containsKey(uuid)) {
            return;
        }

        List<ModelPart> parts = root.getAllParts().toList();
        List<PartState> states = new ArrayList<>(parts.size());
        for (ModelPart part : parts) {
            states.add(PartState.capture(part));
        }
        perEntity.put(uuid, new PoseSnapshot(List.copyOf(states)));
        captures++;
    }

    private static LivingEntity currentLivingEntity() {
        try {
            if (!reflectionResolved) {
                reflectionResolved = true;
                Class<?> api = Class.forName("traben.entity_model_features.EMFAnimationApi");
                getCurrentEntityMethod = api.getMethod("getCurrentEntity");
            }
            if (getCurrentEntityMethod == null) {
                return null;
            }
            Object current = getCurrentEntityMethod.invoke(null);
            return current instanceof LivingEntity living ? living : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            getCurrentEntityMethod = null;
            return null;
        }
    }

    public static int captures() { return captures; }
    public static int restores() { return restores; }
    public static int clears() { return clears; }
    public static int mismatches() { return mismatches; }
}
