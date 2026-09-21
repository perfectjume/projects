package com.example.examplemod;

import com.misanthropy.hit_indicator.HitIndicatorConfig;
import com.misanthropy.hit_indicator.api.HitIndicatorApi;
import com.misanthropy.hit_indicator.api.HitIndicatorApi.HeavyKind;
import com.misanthropy.hit_indicator.api.HitIndicatorApi.HeavyListener;
import com.misanthropy.hit_indicator.server.AttackInterceptor;
import com.misanthropy.hit_indicator.server.ShotInterceptor;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(RuntimeTestMod.MODID)
@EventBusSubscriber(modid = RuntimeTestMod.MODID)
public final class RuntimeTestMod {
    public static final String MODID = "hitindicatortest";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BX = 0;
    private static final int BY = 120;
    private static final int BZ = 0;

    private enum Scenario {
        MELEE,
        CANCEL,
        FREEZE,
        LUNGE,
        DODGE,
        SLAM,
        SURE,
        PARRY,
        WHIFF,
        RANGED,
        CROWD,
        UNIVERSAL_REPLAY
    }

    private static final Scenario[] ORDER = Scenario.values();
    private static ServerPlayer player;
    private static LivingEntity attacker;
    private static final List<Entity> spawned = new ArrayList<>();
    private static int loginTick = -1;
    private static int scenarioIndex = -1;
    private static int scenarioStartTick = -1;
    private static int finishTick = -1;
    private static float startHealth;
    private static boolean actionStarted;
    private static boolean done;
    private static float universalReplayHealthBefore = -1.0F;
    private static volatile String currentScenario = "WAITING";

    public RuntimeTestMod(IEventBus modBus, ModContainer container) {
        HitIndicatorApi.addHeavyListener(new HeavyListener() {
            @Override
            public void onHeavyLanded(LivingEntity source, ServerPlayer victim, HeavyKind kind) {
                LOGGER.info("[HI-MATRIX] API_HEAVY_LANDED kind={} source={} victim={}",
                        kind, source.getType(), victim.getGameProfile().getName());
            }

            @Override
            public void onHeavyDodged(LivingEntity source, ServerPlayer victim, HeavyKind kind) {
                LOGGER.info("[HI-MATRIX] API_HEAVY_DODGED kind={} source={} victim={}",
                        kind, source.getType(), victim.getGameProfile().getName());
            }

            @Override
            public void onHeavyParried(LivingEntity source, ServerPlayer victim, HeavyKind kind) {
                LOGGER.info("[HI-MATRIX] API_HEAVY_PARRIED kind={} source={} victim={}",
                        kind, source.getType(), victim.getGameProfile().getName());
            }
        });
        LOGGER.info("[HI-MATRIX] HELPER_LOADED");
    }

    public static String currentScenario() {
        return currentScenario;
    }

    @SubscribeEvent
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (player != null && event.getEntity() == player) {
            HeavyKind kind = HitIndicatorApi.getDeliveringHeavy(player);
            if (kind != null) {
                LOGGER.info("[HI-MATRIX] API_DELIVERING_HEAVY kind={} amount={}", kind, event.getNewDamage());
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        player = sp;
        loginTick = sp.getServer().getTickCount();
        scenarioIndex = -1;
        scenarioStartTick = -1;
        finishTick = -1;
        actionStarted = false;
        done = false;
        currentScenario = "WAITING";
        learnedAttackCount = 0;
        learnedPredictedBeforeFourth = false;
        learnedFourthHealthBefore = -1.0F;
        AttackInterceptor.setDodgeCheck(p -> false);
        configureBase();
        LOGGER.info("[HI-MATRIX] PLAYER_LOGGED_IN tick={} health={}", loginTick, sp.getHealth());
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (player == null || player.isRemoved() || done) return;
        int now = event.getServer().getTickCount();

        if (scenarioIndex < 0) {
            if (now - loginTick < 60) return;
            buildArena(player.serverLevel());
            startNext(now);
            return;
        }

        if (finishTick >= 0) {
            if (now - finishTick >= 25) startNext(now);
            return;
        }

        Scenario s = ORDER[scenarioIndex];
        int t = now - scenarioStartTick;
        try {
            switch (s) {
                case MELEE -> testMelee(t, now);
                case CANCEL -> testCancel(t, now);
                case FREEZE -> testFreeze(t, now);
                case LUNGE -> testLunge(t, now);
                case DODGE -> testDodge(t, now);
                case SLAM -> testSlam(t, now);
                case SURE -> testSure(t, now);
                case PARRY -> testParry(t, now);
                case WHIFF -> testWhiff(t, now);
                case RANGED -> testRanged(t, now);
                case CROWD -> testCrowd(t, now);
                case UNIVERSAL_REPLAY -> testUniversalReplay(t, now);
            }
            if (t > 180) fail("timeout scenario=" + s + " pending=" + pending());
        } catch (Throwable error) {
            LOGGER.error("[HI-MATRIX] FAIL exception scenario={}", s, error);
            done = true;
        }
    }

    private static void startNext(int now) {
        cleanup();
        scenarioIndex++;
        if (scenarioIndex >= ORDER.length) {
            currentScenario = "DONE";
            done = true;
            AttackInterceptor.setDodgeCheck(p -> false);
            LOGGER.info("[HI-MATRIX] SERVER_ALL_SCENARIOS_PASS");
            return;
        }

        configureBase();
        player.setInvulnerable(false);
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.teleportTo(BX + 0.5, BY + 1.0, BZ + 0.5);
        startHealth = player.getHealth();
        scenarioStartTick = now;
        finishTick = -1;
        actionStarted = false;
        currentScenario = ORDER[scenarioIndex].name();
        LOGGER.info("[HI-MATRIX] SCENARIO_START {} health={}", currentScenario, startHealth);
    }

    private static void configureBase() {
        HitIndicatorConfig.ENABLED.set(true);
        HitIndicatorConfig.WINDUP_TICKS.set(16);
        HitIndicatorConfig.REACH_DISTANCE.set(4.5D);
        HitIndicatorConfig.LAND_IF_OUT_OF_RANGE.set(false);
        HitIndicatorConfig.CROWD_ENABLED.set(false);
        HitIndicatorConfig.RANGED_ENABLED.set(true);
        HitIndicatorConfig.RANGED_WINDUP_TICKS.set(16);
        HitIndicatorConfig.HEAVY_ENABLED.set(false);
        HitIndicatorConfig.HEAVY_BASE_CHANCE.set(0.0D);
        HitIndicatorConfig.HEAVY_CHANCE_CAP.set(1.0D);
        HitIndicatorConfig.HEAVY_VICTIM_COOLDOWN_TICKS.set(0);
        HitIndicatorConfig.HEAVY_SLAM_SHARE.set(0.0D);
        HitIndicatorConfig.LUNGE_WINDUP_TICKS.set(20);
        HitIndicatorConfig.LUNGE_MOVE_TICKS.set(6);
        HitIndicatorConfig.SLAM_WINDUP_TICKS.set(24);
        HitIndicatorConfig.SLAM_HOP_TICKS.set(8);
        HitIndicatorConfig.SURE_ENABLED.set(false);
        HitIndicatorConfig.SURE_BASE_CHANCE.set(0.0D);
        HitIndicatorConfig.SURE_CHANCE_CAP.set(1.0D);
        HitIndicatorConfig.SURE_WINDUP_TICKS.set(18);
        HitIndicatorConfig.SURE_MOVE_TICKS.set(4);
        HitIndicatorConfig.HEAVY_WHIFF_RECOVERY_TICKS.set(20);
        HitIndicatorConfig.HEAVY_DODGE_STUN_TICKS.set(20);
        HitIndicatorConfig.SURE_PARRY_STUN_TICKS.set(20);
        AttackInterceptor.setDodgeCheck(p -> false);
    }

    private static void testMelee(int t, int now) {
        if (!actionStarted && t >= 5) {
            Zombie z = zombie(1.5D);
            triggerMelee(z, null);
        }
        if (actionStarted && player.getHealth() < startHealth) {
            pass(now, "MELEE_DAMAGE before=" + startHealth + " after=" + player.getHealth());
        }
    }

    private static void testCancel(int t, int now) {
        if (!actionStarted && t >= 5) {
            HitIndicatorConfig.WINDUP_TICKS.set(30);
            Zombie z = zombie(1.5D);
            triggerMelee(z, null);
        }
        if (actionStarted && t >= 10 && AttackInterceptor.isWindingUp(attacker)) {
            boolean canceled = AttackInterceptor.cancelPendingHit(attacker);
            LOGGER.info("[HI-MATRIX] CANCEL_REQUEST result={} pendingAfter={}",
                    canceled, AttackInterceptor.isWindingUp(attacker));
            if (!canceled || AttackInterceptor.isWindingUp(attacker)) {
                fail("cancelPendingHit did not clear pending");
                return;
            }
            pass(now, "CANCEL_PENDING_CLEARED");
        }
    }

    private static void testFreeze(int t, int now) {
        if (!actionStarted && t >= 5) {
            HitIndicatorConfig.WINDUP_TICKS.set(80);
            Zombie z = zombie(1.5D);
            triggerMelee(z, null);
        }
        if (actionStarted && t >= 50 && AttackInterceptor.isWindingUp(attacker)) {
            boolean canceled = AttackInterceptor.cancelPendingHit(attacker);
            if (!canceled || AttackInterceptor.isWindingUp(attacker)) {
                fail("freeze scenario could not cancel long windup");
                return;
            }
            pass(now, "LONG_MELEE_WINDUP_EXERCISED");
        }
    }

    private static void testLunge(int t, int now) {
        if (!actionStarted && t >= 5) {
            forceLunge();
            Zombie z = zombie(1.5D);
            triggerMelee(z, HeavyKind.LUNGE);
        }
        if (actionStarted && player.getHealth() < startHealth) {
            pass(now, "LUNGE_DAMAGE before=" + startHealth + " after=" + player.getHealth());
        }
    }

    private static void testDodge(int t, int now) {
        if (!actionStarted && t >= 5) {
            forceLunge();
            AttackInterceptor.setDodgeCheck(p -> true);
            Zombie z = zombie(1.5D);
            triggerMelee(z, HeavyKind.LUNGE);
        }
        if (actionStarted && !AttackInterceptor.isWindingUp(attacker) && t > 20) {
            if (player.getHealth() != startHealth) {
                fail("dodged lunge damaged player before=" + startHealth + " after=" + player.getHealth());
                return;
            }
            if (!AttackInterceptor.isHeld(attacker)) {
                fail("dodged lunge did not hold attacker");
                return;
            }
            pass(now, "DODGE_NO_DAMAGE_AND_HELD");
        }
    }

    private static void testSlam(int t, int now) {
        if (!actionStarted && t >= 5) {
            forceSlam();
            Zombie z = zombie(1.5D);
            triggerMelee(z, HeavyKind.SLAM);
        }
        if (actionStarted && player.getHealth() < startHealth) {
            pass(now, "SLAM_DAMAGE before=" + startHealth + " after=" + player.getHealth());
        }
    }

    private static void testSure(int t, int now) {
        if (!actionStarted && t >= 5) {
            forceSure();
            Zombie z = zombie(1.5D);
            triggerMelee(z, HeavyKind.SURE_HIT);
        }
        if (actionStarted && player.getHealth() < startHealth) {
            pass(now, "SURE_DAMAGE before=" + startHealth + " after=" + player.getHealth());
        }
    }

    private static void testParry(int t, int now) {
        if (!actionStarted && t >= 5) {
            forceSure();
            Zombie z = zombie(1.5D);
            triggerMelee(z, HeavyKind.SURE_HIT);
        }
        if (actionStarted && t >= 9 && AttackInterceptor.isWindingUp(attacker)) {
            player.setInvulnerable(true);
        }
        if (actionStarted && !AttackInterceptor.isWindingUp(attacker) && t > 22) {
            player.setInvulnerable(false);
            if (player.getHealth() != startHealth) {
                fail("parried sure hit damaged player before=" + startHealth + " after=" + player.getHealth());
                return;
            }
            if (!AttackInterceptor.isHeld(attacker)) {
                fail("parried sure hit did not hold attacker");
                return;
            }
            pass(now, "PARRY_NO_DAMAGE_AND_HELD");
        }
    }

    private static void testWhiff(int t, int now) {
        if (!actionStarted && t >= 5) {
            forceLunge();
            Zombie z = zombie(1.5D);
            triggerMelee(z, HeavyKind.LUNGE);
        }
        if (actionStarted && t == 10) {
            player.teleportTo(BX + 25.5D, BY + 1.0D, BZ + 0.5D);
            LOGGER.info("[HI-MATRIX] WHIFF_PLAYER_MOVED distance={}", attacker.distanceTo(player));
        }
        if (actionStarted && !AttackInterceptor.isWindingUp(attacker) && t > 22) {
            if (player.getHealth() != startHealth) {
                fail("whiff damaged player before=" + startHealth + " after=" + player.getHealth());
                return;
            }
            if (!AttackInterceptor.isHeld(attacker)) {
                fail("whiff did not hold attacker");
                return;
            }
            pass(now, "WHIFF_NO_DAMAGE_AND_HELD");
        }
    }

    private static void testRanged(int t, int now) {
        if (!actionStarted && t >= 5) {
            Skeleton skeleton = skeleton(5.0D);
            skeleton.performRangedAttack(player, 1.0F);
            skeleton.setNoAi(true);
            attacker = skeleton;
            actionStarted = true;
            boolean pending = ShotInterceptor.isWindingUp(skeleton.getId());
            LOGGER.info("[HI-MATRIX] RANGED_TRIGGER pending={} id={}", pending, skeleton.getId());
            if (!pending) {
                fail("ranged projectile was not intercepted");
                return;
            }
        }
        if (actionStarted && !ShotInterceptor.isWindingUp(attacker.getId()) && t > 18) {
            ServerLevel level = player.serverLevel();
            AABB box = new AABB(BX - 15, BY - 5, BZ - 15, BX + 15, BY + 10, BZ + 15);
            boolean released = level.getEntitiesOfClass(Projectile.class, box,
                    p -> p.getOwner() == attacker).stream().findAny().isPresent();
            LOGGER.info("[HI-MATRIX] RANGED_RELEASED projectilePresent={}", released);
            if (!released) {
                fail("ranged pending ended without released projectile");
                return;
            }
            pass(now, "RANGED_HELD_THEN_RELEASED");
        }
    }

    private static void testCrowd(int t, int now) {
        if (actionStarted || t < 5) return;
        HitIndicatorConfig.CROWD_ENABLED.set(true);
        HitIndicatorConfig.CROWD_MAX_ATTACKERS.set(1);
        HitIndicatorConfig.CROWD_SPACING_TICKS.set(20);
        HitIndicatorConfig.WINDUP_TICKS.set(30);

        List<Zombie> zombies = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Zombie z = zombie(1.5D + i * 0.1D);
            zombies.add(z);
            z.doHurtTarget(player);
            z.setNoAi(true);
        }
        int winding = 0;
        for (Zombie z : zombies) if (AttackInterceptor.isWindingUp(z)) winding++;
        LOGGER.info("[HI-MATRIX] CROWD_WINDING count={} expected=1", winding);
        if (winding != 1) {
            fail("crowd maxAttackers=1 allowed winding=" + winding);
            return;
        }
        for (Zombie z : zombies) AttackInterceptor.cancelPendingHit(z);
        actionStarted = true;
        pass(now, "CROWD_MAX_ONE_ENFORCED");
    }

    private static void testUniversalReplay(int t, int now) {
        if (!actionStarted && t >= 5) {
            HitIndicatorConfig.WINDUP_TICKS.set(10);
            Zombie z = zombie(1.5D);
            z.setNoAi(true);
            attacker = z;
            actionStarted = true;
            player.setHealth(player.getMaxHealth());
            universalReplayHealthBefore = player.getHealth();

            boolean directResult = player.hurt(player.damageSources().mobAttack(z), 7.0F);
            boolean pendingNow = AttackInterceptor.isWindingUp(z);
            int ticksLeft = AttackInterceptor.getWindupTicksLeft(z);
            LOGGER.info("[HI-MATRIX] UNIVERSAL_TRIGGER result={} pending={} ticksLeft={} healthBefore={} healthNow={}",
                    directResult, pendingNow, ticksLeft, universalReplayHealthBefore, player.getHealth());

            if (!pendingNow || ticksLeft != 10) {
                fail("universal replay did not queue fixed 10-tick windup; ticksLeft=" + ticksLeft);
                return;
            }
            if (player.getHealth() != universalReplayHealthBefore) {
                fail("universal replay applied damage immediately");
                return;
            }
        }

        if (!actionStarted || !(attacker instanceof Zombie z)) return;

        if (AttackInterceptor.isWindingUp(z)) {
            if (player.getHealth() != universalReplayHealthBefore) {
                fail("universal replay damaged player before ring completion");
            }
            return;
        }

        if (t > 5) {
            float dealt = universalReplayHealthBefore - player.getHealth();
            LOGGER.info("[HI-MATRIX] UNIVERSAL_DELIVERED dealt={} healthBefore={} healthAfter={}",
                    dealt, universalReplayHealthBefore, player.getHealth());
            if (dealt < 6.5F || dealt > 7.5F) {
                fail("captured damage was not replayed; expected about 7.0 dealt=" + dealt);
                return;
            }
            pass(now, "UNIVERSAL_CAPTURED_DAMAGE_DELAYED_AND_REPLAYED");
        }
    }

    private static void forceLunge() {
        HitIndicatorConfig.HEAVY_ENABLED.set(true);
        HitIndicatorConfig.HEAVY_BASE_CHANCE.set(1.0D);
        HitIndicatorConfig.HEAVY_CHANCE_CAP.set(1.0D);
        HitIndicatorConfig.HEAVY_SLAM_SHARE.set(0.0D);
        HitIndicatorConfig.SURE_ENABLED.set(false);
    }

    private static void forceSlam() {
        HitIndicatorConfig.HEAVY_ENABLED.set(true);
        HitIndicatorConfig.HEAVY_BASE_CHANCE.set(1.0D);
        HitIndicatorConfig.HEAVY_CHANCE_CAP.set(1.0D);
        HitIndicatorConfig.HEAVY_SLAM_SHARE.set(1.0D);
        HitIndicatorConfig.SURE_ENABLED.set(false);
    }

    private static void forceSure() {
        HitIndicatorConfig.HEAVY_ENABLED.set(true);
        HitIndicatorConfig.HEAVY_BASE_CHANCE.set(0.0D);
        HitIndicatorConfig.HEAVY_CHANCE_CAP.set(1.0D);
        HitIndicatorConfig.SURE_ENABLED.set(true);
        HitIndicatorConfig.SURE_BASE_CHANCE.set(1.0D);
        HitIndicatorConfig.SURE_CHANCE_CAP.set(1.0D);
    }

    private static void triggerMelee(Zombie z, HeavyKind expected) {
        boolean directResult = z.doHurtTarget(player);
        z.setNoAi(true);
        attacker = z;
        actionStarted = true;
        boolean pending = AttackInterceptor.isWindingUp(z);
        HeavyKind actual = AttackInterceptor.getPendingHeavy(z);
        LOGGER.info("[HI-MATRIX] MELEE_TRIGGER scenario={} directResult={} pending={} expectedHeavy={} actualHeavy={} ticksLeft={}",
                currentScenario, directResult, pending, expected, actual, AttackInterceptor.getWindupTicksLeft(z));
        if (!pending) {
            fail("melee attack was not intercepted scenario=" + currentScenario);
            return;
        }
        if (actual != expected) {
            fail("wrong heavy kind scenario=" + currentScenario + " expected=" + expected + " actual=" + actual);
        }
    }

    private static Zombie zombie(double distance) {
        ServerLevel level = player.serverLevel();
        Zombie z = EntityType.ZOMBIE.create(level);
        if (z == null) throw new IllegalStateException("zombie creation returned null");
        z.moveTo(BX + 0.5D + distance, BY + 1.0D, BZ + 0.5D, 90.0F, 0.0F);
        z.setPersistenceRequired();
        z.addTag("hit_indicator_no_learn");
        z.setTarget(player);
        level.addFreshEntity(z);
        spawned.add(z);
        return z;
    }

    private static Zombie learningZombie(double distance) {
        ServerLevel level = player.serverLevel();
        Zombie z = EntityType.ZOMBIE.create(level);
        if (z == null) throw new IllegalStateException("learning zombie creation returned null");
        z.moveTo(BX + 0.5D + distance, BY + 1.0D, BZ + 0.5D, 90.0F, 0.0F);
        z.setPersistenceRequired();
        z.setTarget(player);
        level.addFreshEntity(z);
        spawned.add(z);
        return z;
    }

    private static Skeleton skeleton(double distance) {
        ServerLevel level = player.serverLevel();
        Skeleton s = EntityType.SKELETON.create(level);
        if (s == null) throw new IllegalStateException("skeleton creation returned null");
        s.moveTo(BX + 0.5D + distance, BY + 1.0D, BZ + 0.5D, 90.0F, 0.0F);
        s.setPersistenceRequired();
        s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        s.setTarget(player);
        level.addFreshEntity(s);
        spawned.add(s);
        return s;
    }

    private static void buildArena(ServerLevel level) {
        for (int x = -30; x <= 30; x++) {
            for (int z = -8; z <= 8; z++) {
                level.setBlockAndUpdate(new BlockPos(BX + x, BY, BZ + z), Blocks.STONE.defaultBlockState());
                for (int y = 1; y <= 5; y++) {
                    level.setBlockAndUpdate(new BlockPos(BX + x, BY + y, BZ + z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        LOGGER.info("[HI-MATRIX] ARENA_READY");
    }

    private static boolean pending() {
        return attacker != null && (AttackInterceptor.isWindingUp(attacker)
                || ShotInterceptor.isWindingUp(attacker.getId()));
    }

    private static void pass(int now, String detail) {
        if (finishTick >= 0) return;
        finishTick = now;
        LOGGER.info("[HI-MATRIX] SCENARIO_PASS {} {}", currentScenario, detail);
    }

    private static void fail(String detail) {
        if (done) return;
        done = true;
        LOGGER.error("[HI-MATRIX] FAIL {}", detail);
    }

    private static void cleanup() {
        player.setInvulnerable(false);
        AttackInterceptor.setDodgeCheck(p -> false);
        for (Entity entity : spawned) {
            if (entity instanceof LivingEntity living) AttackInterceptor.cancelPendingHit(living);
            if (!entity.isRemoved()) entity.discard();
        }
        spawned.clear();
        attacker = null;
        actionStarted = false;
    }
}
