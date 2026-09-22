package com.soulshade.hitindicator.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.GlStateBackup;

public final class StasisDesaturationRenderer {
    private static ShaderInstance shader;
    private static TextureTarget entityTarget;
    private static boolean active;
    private static int oldTexture0;
    private static ShaderInstance oldShader;
    private static final GlStateBackup GL_BACKUP = new GlStateBackup();
    private static int passCount;

    private StasisDesaturationRenderer() {}

    public static void setShader(ShaderInstance loaded) {
        shader = loaded;
    }

    public static boolean isVisualStasis(Entity entity) {
        return entity instanceof LivingEntity
                && (TelegraphState.isFrozen(entity.getId())
                || FrozenRenderClock.isReleaseBridge(entity));
    }

    public static void begin(Entity entity, MultiBufferSource bufferSource) {
        active = false;
        if (!isVisualStasis(entity) || shader == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();

        flush(bufferSource);
        RenderSystem.backupGlState(GL_BACKUP);
        oldTexture0 = RenderSystem.getShaderTexture(0);
        oldShader = RenderSystem.getShader();

        ensureEntityTarget(main.width, main.height);
        entityTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        entityTarget.clear(Minecraft.ON_OSX);
        entityTarget.copyDepthFrom(main);
        entityTarget.bindWrite(false);
        active = true;
    }

    public static void end(Entity entity, MultiBufferSource bufferSource) {
        if (!active) {
            return;
        }

        active = false;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();

        flush(bufferSource);
        main.copyDepthFrom(entityTarget);
        main.bindWrite(false);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, entityTarget.getColorTextureId());

        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 0.0F);
        builder.addVertex( 1.0F, -1.0F, 0.0F).setUv(1.0F, 0.0F);
        builder.addVertex( 1.0F,  1.0F, 0.0F).setUv(1.0F, 1.0F);
        builder.addVertex(-1.0F,  1.0F, 0.0F).setUv(0.0F, 1.0F);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        passCount++;

        RenderSystem.setShaderTexture(0, oldTexture0);
        RenderSystem.restoreGlState(GL_BACKUP);
        if (oldShader != null) {
            RenderSystem.setShader(() -> oldShader);
        }
        main.bindWrite(false);
    }

    public static int passCount() {
        return passCount;
    }

    private static void ensureEntityTarget(int width, int height) {
        if (entityTarget == null) {
            entityTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            entityTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        } else if (entityTarget.width != width || entityTarget.height != height) {
            entityTarget.resize(width, height, Minecraft.ON_OSX);
        }
    }

    private static void flush(MultiBufferSource bufferSource) {
        if (bufferSource instanceof MultiBufferSource.BufferSource buffers) {
            buffers.endBatch();
        }
    }
}
