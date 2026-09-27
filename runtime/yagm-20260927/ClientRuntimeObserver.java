package dev.yagm_runtime_test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.nio.file.Files;
import java.nio.file.Path;

@EventBusSubscriber(modid = RuntimeTestMod.MOD_ID, value = Dist.CLIENT)
public final class ClientRuntimeObserver {
    private static boolean passed;

    private ClientRuntimeObserver() {}

    @SubscribeEvent
    static void onScreenOpening(ScreenEvent.Opening event) {
        if (passed || !(event.getNewScreen() instanceof TitleScreen)) return;
        passed = true;
        try {
            Files.writeString(Path.of("yagm-client-title-pass.txt"), "PASS\n");
            System.out.println("[YAGM-CLIENT-RUNTIME] YAGM_CLIENT_RUNTIME: PASS title screen opened");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Minecraft.getInstance().execute(Minecraft.getInstance()::stop);
    }
}
