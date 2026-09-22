package com.misanthropy.hit_indicator.client;

import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

@EventBusSubscriber(modid = "hit_indicator", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class StasisDesaturationClientEvents {
    private StasisDesaturationClientEvents() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> Minecraft.getInstance().getMainRenderTarget().enableStencil());
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath("hit_indicator", "stasis_desaturate"),
                        DefaultVertexFormat.POSITION_TEX),
                StasisDesaturationRenderer::setShader);
    }
}
