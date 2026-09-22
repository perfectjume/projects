package com.soulshade.runtime;

import com.soulshade.hitindicator.Settings;
import com.soulshade.hitindicator.server.AttackDelayManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CleanRuntimeTestMod.MOD_ID)
@EventBusSubscriber(modid = CleanRuntimeTestMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class CleanRuntimeTestMod {
    public static final String MOD_ID = "soulshade_hit_indicator_runtime_test";
    private static final Logger LOGGER = LoggerFactory.getLogger("CleanHitIndicatorRuntime");

    private static ServerPlayer player;
    private static Zombie attacker;
    private static int ticks;
    private static int triggerTick = -1;
    private static int frozenTickCount;
    private static float healthBefore;
    private static boolean completed; // Runtime matrix state.

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            player = serverPlayer;
            ticks = 0;
            completed = false;
            LOGGER.info("[CLEAN-HI] PLAYER_READY");
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (player == null || completed || player.isRemoved()) {
            return;
        }

        ticks++;

        if (ticks == 80) {
            buildArena(player.serverLevel());
            player.teleportTo(0.5D, 101.0D, 0.5D);
            player.setHealth(player.getMaxHealth());

            attacker = EntityType.ZOMBIE.create(player.serverLevel());
            if (attacker == null) {
                fail("zombie creation failed");
                return;
            }
            attacker.moveTo(2.0D, 101.0D, 0.5D, 90.0F, 0.0F);
            attacker.setNoAi(true);
            attacker.setPersistenceRequired();
            player.serverLevel().addFreshEntity(attacker);
            LOGGER.info("[CLEAN-HI] ATTACKER_READY id={}", attacker.getId());
        }

        if (ticks == 90 && attacker != null) {
            healthBefore = player.getHealth();
            frozenTickCount = attacker.tickCount;
            boolean directResult = player.hurt(player.damageSources().mobAttack(attacker), 4.0F);
            triggerTick = ticks;

            boolean frozen = AttackDelayManager.isFrozen(attacker);
            LOGGER.info(
                    "[CLEAN-HI] ATTACK_TRIGGER directResult={} frozen={} healthBefore={} healthNow={} duration={}",
                    directResult,
                    frozen,
                    healthBefore,
                    player.getHealth(),
                    Settings.TELEGRAPH_TICKS);

            if (!frozen || player.getHealth() != healthBefore) {
                fail("damage was not delayed");
            }
            return;
        }

        if (triggerTick < 0 || attacker == null) {
            return;
        }

        if (AttackDelayManager.isFrozen(attacker)) {
            if (player.getHealth() != healthBefore) {
                fail("health changed during telegraph");
                return;
            }
            if (ticks >= triggerTick + 5 && attacker.tickCount != frozenTickCount) {
                fail("server attacker tickCount advanced while frozen: start="
                        + frozenTickCount + " now=" + attacker.tickCount);
            }
            return;
        }

        if (ticks > triggerTick + 5) {
            float dealt = healthBefore - player.getHealth();
            int resumeDelta = attacker.tickCount - frozenTickCount;
            boolean resumeSane = resumeDelta >= 0 && resumeDelta <= 1;
            LOGGER.info(
                    "[CLEAN-HI] SERVER_RESULT dealt={} frozenStable=true resumeDelta={} startTick={} endTick={}",
                    dealt,
                    resumeDelta,
                    frozenTickCount,
                    attacker.tickCount);

            if (dealt < 3.9F || dealt > 4.1F) {
                fail("replayed damage mismatch: " + dealt);
                return;
            }
            if (!resumeSane) {
                fail("attacker tickCount jumped on release: delta=" + resumeDelta);
                return;
            }

            completed = true;
            LOGGER.info("[CLEAN-HI] SERVER_CLEAN_PASS");
        }
    }

    private static void buildArena(ServerLevel level) {
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 100, z), Blocks.STONE.defaultBlockState());
                for (int y = 101; y <= 105; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void fail(String reason) {
        completed = true;
        LOGGER.error("[CLEAN-HI] FAIL {}", reason);
    }
}
