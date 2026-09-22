package com.misanthropy.hit_indicator.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.GlStateBackup;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class StasisDesaturationRenderer {
    private static ShaderInstance shader;
    private static TextureTarget entityTarget;

    private static boolean active;
    private static int oldTexture0;
    private static ShaderInstance oldShader;
    private static int beginCount;
    private static int passCount;
    private static final GlStateBackup GL_BACKUP = new GlStateBackup();

    // One-shot pixel-level proof used by the runtime matrix.
    private static boolean diagnosticCaptured;
    private static int entityPixelCount;
    private static int diagnosticBeforeR = -1;
    private static int diagnosticBeforeG = -1;
    private static int diagnosticBeforeB = -1;
    private static int diagnosticBeforeA = -1;
    private static int diagnosticAfterR = -1;
    private static int diagnosticAfterG = -1;
    private static int diagnosticAfterB = -1;
    private static int diagnosticX = -1;
    private static int diagnosticY = -1;

    private StasisDesaturationRenderer() {}

    public static void setShader(ShaderInstance loaded) {
        shader = loaded;
    }

    public static boolean isVisualStasis(Entity entity) {
        return entity instanceof LivingEntity living
                && (WindupTracker.shouldFreeze(living) || FrozenRenderClock.isReleaseBridge(entity));
    }

    /**
     * Redirect one frozen entity into a transparent framebuffer. We copy the
     * current world depth first, so the entity still respects walls/terrain.
     * The renderer itself is untouched: vanilla, PlayerAnimator, GeckoLib,
     * AzureLib, etc. all render through their normal code into this target.
     */
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
        beginCount++;
    }

    /**
     * Flush the entity while the offscreen framebuffer is bound, then composite
     * only its non-transparent pixels back onto the main framebuffer in
     * grayscale. Finally copy the updated depth back so later entities still
     * occlude correctly.
     */
    public static void end(Entity entity, MultiBufferSource bufferSource) {
        if (!active) {
            return;
        }

        active = false;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();

        // Force all deferred entity RenderTypes to land in entityTarget.
        flush(bufferSource);

        if (!diagnosticCaptured) {
            captureBeforeDiagnostic(entityTarget.width, entityTarget.height);
        }

        // entityTarget began with main depth and now also contains this entity's
        // depth. Preserve that for everything rendered afterward.
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

        if (!diagnosticCaptured && diagnosticX >= 0) {
            captureAfterDiagnostic();
            diagnosticCaptured = true;
        } else if (!diagnosticCaptured) {
            diagnosticCaptured = true;
        }

        RenderSystem.setShaderTexture(0, oldTexture0);
        RenderSystem.restoreGlState(GL_BACKUP);
        if (oldShader != null) {
            RenderSystem.setShader(() -> oldShader);
        }
        main.bindWrite(false);
    }

    public static boolean shaderReady() {
        return shader != null;
    }

    public static boolean targetReady() {
        return entityTarget != null;
    }

    public static int beginCount() {
        return beginCount;
    }

    public static int passCount() {
        return passCount;
    }

    public static int entityPixelCount() {
        return entityPixelCount;
    }

    public static int diagnosticBeforeSpread() {
        return spread(diagnosticBeforeR, diagnosticBeforeG, diagnosticBeforeB);
    }

    public static int diagnosticAfterSpread() {
        return spread(diagnosticAfterR, diagnosticAfterG, diagnosticAfterB);
    }

    public static int diagnosticBeforeAlpha() {
        return diagnosticBeforeA;
    }

    public static String diagnosticRgb() {
        return diagnosticBeforeR + "," + diagnosticBeforeG + "," + diagnosticBeforeB + "," + diagnosticBeforeA
                + "->" + diagnosticAfterR + "," + diagnosticAfterG + "," + diagnosticAfterB;
    }

    private static void ensureEntityTarget(int width, int height) {
        if (entityTarget == null) {
            entityTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            entityTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        } else if (entityTarget.width != width || entityTarget.height != height) {
            entityTarget.resize(width, height, Minecraft.ON_OSX);
        }
    }

    /**
     * The test mob is kept under the crosshair. Reading only a small central
     * region avoids the large synchronous full-frame readback that made the
     * earlier stencil diagnostic stall llvmpipe/Xvfb.
     */
    private static void captureBeforeDiagnostic(int width, int height) {
        int regionWidth = Math.min(220, width);
        int regionHeight = Math.min(280, height);
        int x0 = Math.max(0, width / 2 - regionWidth / 2);
        int y0 = Math.max(0, height / 2 - regionHeight / 2);

        ByteBuffer color = BufferUtils.createByteBuffer(regionWidth * regionHeight * 4);
        GL11.glReadPixels(
                x0,
                y0,
                regionWidth,
                regionHeight,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                color);

        int bestSpread = -1;
        int bestX = -1;
        int bestY = -1;
        int bestR = -1;
        int bestG = -1;
        int bestB = -1;
        int bestA = -1;
        int count = 0;

        for (int y = 0; y < regionHeight; y++) {
            for (int x = 0; x < regionWidth; x++) {
                int index = (y * regionWidth + x) * 4;
                int a = color.get(index + 3) & 0xFF;
                if (a < 245) {
                    continue;
                }
                count++;

                int r = color.get(index) & 0xFF;
                int g = color.get(index + 1) & 0xFF;
                int b = color.get(index + 2) & 0xFF;
                int spread = spread(r, g, b);
                if (spread > bestSpread) {
                    bestSpread = spread;
                    bestX = x0 + x;
                    bestY = y0 + y;
                    bestR = r;
                    bestG = g;
                    bestB = b;
                    bestA = a;
                }
            }
        }

        entityPixelCount = count;
        diagnosticX = bestX;
        diagnosticY = bestY;
        diagnosticBeforeR = bestR;
        diagnosticBeforeG = bestG;
        diagnosticBeforeB = bestB;
        diagnosticBeforeA = bestA;
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
}
