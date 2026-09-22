package dev.hitindicator.client;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

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

    private EmfPoseFreeze() {}

    public static boolean beforeAnimate(Object rootObject) {
        LivingEntity entity = currentLivingEntity();
        if (entity == null || !(rootObject instanceof ModelPart root)) {
            return false;
        }

        UUID uuid = entity.getUUID();
        if (!isFrozen(entity)) {
            Map<UUID, PoseSnapshot> perEntity = SNAPSHOTS.get(rootObject);
            if (perEntity != null) {
                perEntity.remove(uuid);
                if (perEntity.isEmpty()) {
                    SNAPSHOTS.remove(rootObject);
                }
            }
            return false;
        }

        Map<UUID, PoseSnapshot> perEntity = SNAPSHOTS.get(rootObject);
        PoseSnapshot snapshot = perEntity == null ? null : perEntity.get(uuid);
        if (snapshot == null) {
            return false;
        }

        List<ModelPart> parts = root.getAllParts().toList();
        if (parts.size() != snapshot.states.size()) {
            perEntity.remove(uuid);
            return false;
        }

        for (int i = 0; i < parts.size(); i++) {
            snapshot.states.get(i).restore(parts.get(i));
        }
        return true;
    }

    public static void afterAnimate(Object rootObject) {
        LivingEntity entity = currentLivingEntity();
        if (entity == null || !(rootObject instanceof ModelPart root) || !isFrozen(entity)) {
            return;
        }

        UUID uuid = entity.getUUID();
        Map<UUID, PoseSnapshot> perEntity =
                SNAPSHOTS.computeIfAbsent(rootObject, ignored -> new HashMap<>());
        if (perEntity.containsKey(uuid)) {
            return;
        }

        List<ModelPart> parts = root.getAllParts().toList();
        List<PartState> states = new ArrayList<>(parts.size());
        for (ModelPart part : parts) {
            states.add(PartState.capture(part));
        }
        perEntity.put(uuid, new PoseSnapshot(List.copyOf(states)));
    }

    private static boolean isFrozen(LivingEntity entity) {
        return TelegraphState.isFrozen(entity.getId())
                || FrozenRenderClock.isReleaseBridge(entity);
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
}
