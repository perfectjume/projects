package dev.hitindicator.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hitindicator.Settings;
import dev.hitindicator.HitIndicator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(
        modid = HitIndicator.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class TelegraphRenderer {
    private static final int SEGMENTS = 72;
    private static int renderedFrames;

    private TelegraphRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        if (pose == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        for (var entry : TelegraphState.activeView().entrySet()) {
            Entity entity = mc.level.getEntity(entry.getKey());
            if (entity == null || entity.isRemoved()) {
                continue;
            }

            double x = Mth.lerp(partialTick, entity.xo, entity.getX());
            double y = Mth.lerp(partialTick, entity.yo, entity.getY())
                    + entity.getBbHeight() * 0.55;
            double z = Mth.lerp(partialTick, entity.zo, entity.getZ());

            float progress = TelegraphState.progress(
                    entry.getValue(),
                    mc.level.getGameTime(),
                    partialTick);

            float outerRadius = Settings.CIRCLE_DIAMETER_BLOCKS * 0.5F;
            float timingRadius = outerRadius * Math.max(0.04F, 1.0F - progress);

            pose.pushPose();
            pose.translate(x - camera.x, y - camera.y, z - camera.z);
            pose.mulPose(event.getCamera().rotation());

            drawCircle(lines, pose, outerRadius, 1.0F, 0.72F, 0.12F, 1.0F);
            drawCircle(lines, pose, timingRadius, 1.0F, 1.0F, 1.0F, 0.95F);

            pose.popPose();
        }

        buffers.endBatch(RenderType.lines());
        if (!TelegraphState.activeView().isEmpty()) {
            renderedFrames++;
        }
    }

    public static int renderedFrames() {
        return renderedFrames;
    }

    private static void drawCircle(
            VertexConsumer consumer,
            PoseStack pose,
            float radius,
            float r,
            float g,
            float b,
            float a) {
        Matrix4f matrix = pose.last().pose();

        for (int i = 0; i < SEGMENTS; i++) {
            double angle1 = Math.PI * 2.0 * i / SEGMENTS;
            double angle2 = Math.PI * 2.0 * (i + 1) / SEGMENTS;

            float x1 = (float) Math.cos(angle1) * radius;
            float y1 = (float) Math.sin(angle1) * radius;
            float x2 = (float) Math.cos(angle2) * radius;
            float y2 = (float) Math.sin(angle2) * radius;

            float dx = x2 - x1;
            float dy = y2 - y1;
            float length = Mth.sqrt(dx * dx + dy * dy);
            if (length <= 0.0001F) {
                continue;
            }

            consumer.addVertex(matrix, x1, y1, 0.0F)
                    .setColor(r, g, b, a)
                    .setNormal(pose.last(), dx / length, dy / length, 0.0F);
            consumer.addVertex(matrix, x2, y2, 0.0F)
                    .setColor(r, g, b, a)
                    .setNormal(pose.last(), dx / length, dy / length, 0.0F);
        }
    }
}
