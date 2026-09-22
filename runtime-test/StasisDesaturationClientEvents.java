package com.misanthropy.hit_indicator.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

@EventBusSubscriber(modid = "hit_indicator", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class StasisDesaturationClientEvents {
    private StasisDesaturationClientEvents() {}

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
