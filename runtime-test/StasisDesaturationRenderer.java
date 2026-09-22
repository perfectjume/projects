package com.misanthropy.hit_indicator.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
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
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;

public final class StasisDesaturationRenderer {
    private static ShaderInstance shader;
    private static int snapshotTexture = -1;
    private static int snapshotWidth = -1;
    private static int snapshotHeight = -1;

    private static boolean active;
    private static boolean stencilWasEnabled;
    private static int oldTexture0;
    private static ShaderInstance oldShader;
    private static int beginCount;
    private static int passCount;
    private static boolean diagnosticCaptured;
    private static int stencilPixelCount;
    private static int stencilOnePixelCount;
    private static int diagnosticBeforeR = -1;
    private static int diagnosticBeforeG = -1;
    private static int diagnosticBeforeB = -1;
    private static int diagnosticAfterR = -1;
    private static int diagnosticAfterG = -1;
    private static int diagnosticAfterB = -1;
    private static int diagnosticX = -1;
    private static int diagnosticY = -1;
    private static final GlStateBackup GL_BACKUP = new GlStateBackup();

    private StasisDesaturationRenderer() {}

    public static void setShader(ShaderInstance loaded) {
        shader = loaded;
    }

    public static boolean isVisualStasis(Entity entity) {
        return entity instanceof LivingEntity living
                && (WindupTracker.shouldFreeze(living) || FrozenRenderClock.isReleaseBridge(entity));
    }

    public static void begin(Entity entity, MultiBufferSource bufferSource) {
        active = false;
        if (!isVisualStasis(entity) || shader == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (!main.isStencilEnabled()) {
            main.enableStencil();
        }

        flush(bufferSource);

        RenderSystem.backupGlState(GL_BACKUP);
        stencilWasEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        oldTexture0 = RenderSystem.getShaderTexture(0);
        oldShader = RenderSystem.getShader();

        main.bindWrite(false);
        RenderSystem.clearStencil(0);
        RenderSystem.stencilMask(0xFF);
        RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, Minecraft.ON_OSX);

        GL11.glEnable(GL11.GL_STENCIL_TEST);
        RenderSystem.stencilMask(0xFF);
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        active = true;
        beginCount++;
    }

    public static void end(Entity entity, MultiBufferSource bufferSource) {
        if (!active) {
            return;
        }

        active = false;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();

        // Entity vertices are normally batched. Flush them while stencil writes
        // are still active so every ordinary entity RenderType contributes to
        // the mask without needing renderer-specific compatibility.
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        RenderSystem.stencilMask(0xFF);
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        flush(bufferSource);

        ensureSnapshot(main.width, main.height);
        main.bindWrite(false);

        // Snapshot the already-rendered color buffer. This captures whatever
        // vanilla/modded renderer produced, including PlayerAnimator/GeckoLib.
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshotTexture);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, main.width, main.height);

        // One-time runtime diagnostic: prove the entity actually populated the
        // stencil buffer and choose the most saturated marked pixel so the test
        // can verify that this exact pixel becomes desaturated after the pass.
        if (!diagnosticCaptured) {
            captureBeforeDiagnostic(main.width, main.height);
        }

        // Replace only stencil-marked pixels with a grayscale sample from the
        // snapshot. No entity model/texture knowledge is required here.
        RenderSystem.stencilMask(0x00);
        RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();

        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, snapshotTexture);

        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 0.0F);
        builder.addVertex( 1.0F, -1.0F, 0.0F).setUv(1.0F, 0.0F);
        builder.addVertex( 1.0F,  1.0F, 0.0F).setUv(1.0F, 1.0F);
        builder.addVertex(-1.0F,  1.0F, 0.0F).setUv(0.0F, 1.0F);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        passCount++;

        if (!diagnosticCaptured && diagnosticX >= 0) {
            captureAfterDiagnostic();
            diagnosticCaptured = true;
        } else if (!diagnosticCaptured) {
            diagnosticCaptured = true;
        }

        RenderSystem.setShaderTexture(0, oldTexture0);

        // Clear our temporary entity mask before restoring the caller's exact
        // stencil parameters. GlStateBackup stores the parameters but not the
        // enabled/disabled bit, so that bit is restored separately below.
        RenderSystem.stencilMask(0xFF);
        RenderSystem.clearStencil(0);
        RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, Minecraft.ON_OSX);

        RenderSystem.restoreGlState(GL_BACKUP);
        if (oldShader != null) {
            RenderSystem.setShader(() -> oldShader);
        }
        if (stencilWasEnabled) {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
        } else {
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }

        main.bindWrite(false);
    }

    public static boolean shaderReady() {
        return shader != null;
    }

    public static boolean stencilReady() {
        return Minecraft.getInstance().getMainRenderTarget().isStencilEnabled();
    }

    public static int beginCount() {
        return beginCount;
    }

    public static int passCount() {
        return passCount;
    }

    public static int stencilPixelCount() {
        return stencilPixelCount;
    }

    public static int stencilOnePixelCount() {
        return stencilOnePixelCount;
    }

    public static int diagnosticBeforeSpread() {
        return spread(diagnosticBeforeR, diagnosticBeforeG, diagnosticBeforeB);
    }

    public static int diagnosticAfterSpread() {
        return spread(diagnosticAfterR, diagnosticAfterG, diagnosticAfterB);
    }

    public static String diagnosticRgb() {
        return diagnosticBeforeR + "," + diagnosticBeforeG + "," + diagnosticBeforeB
                + "->" + diagnosticAfterR + "," + diagnosticAfterG + "," + diagnosticAfterB;
    }

    private static void captureBeforeDiagnostic(int width, int height) {
        ByteBuffer stencil = BufferUtils.createByteBuffer(width * height);
        ByteBuffer color = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_STENCIL_INDEX, GL11.GL_UNSIGNED_BYTE, stencil);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, color);

        int bestSpread = -1;
        int count = 0;
        int bestX = -1;
        int bestY = -1;
        int bestR = -1;
        int bestG = -1;
        int bestB = -1;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int stencilValue = stencil.get(index) & 0xFF;
                if (stencilValue == 0) {
                    continue;
                }
                count++;
                if (stencilValue != 1) {
                    continue;
                }
                stencilOnePixelCount++;

                int colorIndex = index * 4;
                int r = color.get(colorIndex) & 0xFF;
                int g = color.get(colorIndex + 1) & 0xFF;
                int b = color.get(colorIndex + 2) & 0xFF;
                int spread = spread(r, g, b);
                if (spread > bestSpread) {
                    bestSpread = spread;
                    bestX = x;
                    bestY = y;
                    bestR = r;
                    bestG = g;
                    bestB = b;
                }
            }
        }

        stencilPixelCount = count;
        diagnosticX = bestX;
        diagnosticY = bestY;
        diagnosticBeforeR = bestR;
        diagnosticBeforeG = bestG;
        diagnosticBeforeB = bestB;
    }

    private static void captureAfterDiagnostic() {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(
                diagnosticX,
                diagnosticY,
                1,
                1,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                pixel);
        diagnosticAfterR = pixel.get(0) & 0xFF;
        diagnosticAfterG = pixel.get(1) & 0xFF;
        diagnosticAfterB = pixel.get(2) & 0xFF;
    }

    private static int spread(int r, int g, int b) {
        if (r < 0 || g < 0 || b < 0) return -1;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
    }

    private static void flush(MultiBufferSource bufferSource) {
        if (bufferSource instanceof MultiBufferSource.BufferSource buffers) {
            buffers.endBatch();
        }
    }

    private static void ensureSnapshot(int width, int height) {
        if (snapshotTexture != -1 && snapshotWidth == width && snapshotHeight == height) {
            return;
        }

        if (snapshotTexture == -1) {
            snapshotTexture = GL11.glGenTextures();
        }

        snapshotWidth = width;
        snapshotHeight = height;

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshotTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                width,
                height,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                (java.nio.ByteBuffer) null);
    }
}
