package com.soulshade.hitindicator.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.soulshade.hitindicator.SoulshadeHitIndicator;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

@EventBusSubscriber(
        modid = SoulshadeHitIndicator.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(
                                SoulshadeHitIndicator.MOD_ID,
                                "stasis_desaturate"),
                        DefaultVertexFormat.POSITION_TEX),
                StasisDesaturationRenderer::setShader);
    }
}
