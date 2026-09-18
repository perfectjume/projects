package com.example.examplemod;

import com.misanthropy.hit_indicator.HitIndicatorConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(RuntimeTestMod.MODID)
@EventBusSubscriber(modid = RuntimeTestMod.MODID)
public final class RuntimeTestMod {
    public static final String MODID = "hitindicatortest";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ServerPlayer player;
    private static Zombie zombie;
    private static int loginTick = -1;
    private static float startHealth = -1.0F;
    private static boolean spawned;
    private static boolean done;
    private static boolean failed;

    public RuntimeTestMod(IEventBus modBus, ModContainer container) {
        LOGGER.info("[HI-TEST] HELPER_LOADED");
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            player = sp;
            loginTick = sp.getServer().getTickCount();
            spawned = false;
            done = false;
            failed = false;
            HitIndicatorConfig.WINDUP_TICKS.set(40);
            LOGGER.info("[HI-TEST] PLAYER_LOGGED_IN tick={} health={} windupTicks={}",
                    loginTick, sp.getHealth(), HitIndicatorConfig.WINDUP_TICKS.get());
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (player == null || player.isRemoved() || done || failed) return;
        int elapsed = event.getServer().getTickCount() - loginTick;

        if (!spawned && elapsed >= 80) {
            ServerLevel level = player.serverLevel();
            int bx = 0, by = 120, bz = 0;
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    level.setBlockAndUpdate(new BlockPos(bx + x, by, bz + z), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(new BlockPos(bx + x, by + 4, bz + z), Blocks.STONE.defaultBlockState());
                    for (int y = 1; y <= 3; y++) {
                        level.setBlockAndUpdate(new BlockPos(bx + x, by + y, bz + z), Blocks.AIR.defaultBlockState());
                    }
                }
            }

            player.teleportTo(bx + 0.5, by + 1.0, bz + 0.5);
            player.setHealth(player.getMaxHealth());
            startHealth = player.getHealth();

            Zombie z = EntityType.ZOMBIE.create(level);
            if (z == null) {
                failed = true;
                LOGGER.error("[HI-TEST] FAIL zombie creation returned null");
                return;
            }

            z.moveTo(bx + 2.0, by + 1.0, bz + 0.5, 90.0F, 0.0F);
            z.setPersistenceRequired();
            z.setTarget(player);
            level.addFreshEntity(z);
            zombie = z;
            spawned = true;
            LOGGER.info("[HI-TEST] ZOMBIE_SPAWNED id={} health={} distance={}",
                    z.getId(), startHealth, z.distanceTo(player));
            return;
        }

        if (spawned && player.getHealth() < startHealth) {
            done = true;
            LOGGER.info("[HI-TEST] PLAYER_DAMAGED before={} after={} elapsedSinceLogin={} zombieAlive={}",
                    startHealth, player.getHealth(), elapsed, zombie != null && zombie.isAlive());
            LOGGER.info("[HI-TEST] PASS");
            return;
        }

        if (spawned && elapsed > 500) {
            failed = true;
            LOGGER.error("[HI-TEST] FAIL no player health loss within timeout health={} startHealth={} zombieAlive={}",
                    player.getHealth(), startHealth, zombie != null && zombie.isAlive());
        }
    }
}
