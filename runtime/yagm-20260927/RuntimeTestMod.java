package dev.yagm_runtime_test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

@Mod(RuntimeTestMod.MOD_ID)
public final class RuntimeTestMod {
    public static final String MOD_ID = "yagm_runtime_test";
    private static final UUID OWNER1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BUILDER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Path STAGE_FILE = Path.of("yagm-runtime-stage.properties");
    private static final Path CONFIG_FILE = Path.of("config", "yagm_orbit.properties");

    private MinecraftServer server;
    private ServerLevel level;
    private int phase = -1;
    private int ticks;
    private FakePlayer p1;
    private FakePlayer p2;
    private FakePlayer p3;
    private BlockPos grave1;
    private BlockPos grave2;
    private BlockPos death2;
    private BlockPos manualPos;

    public RuntimeTestMod() {
        NeoForge.EVENT_BUS.addListener(this::onStarted);
        NeoForge.EVENT_BUS.addListener(this::onTick);
    }

    private void onStarted(ServerStartedEvent event) {
        this.server = event.getServer();
        this.level = server.overworld();
        this.ticks = 0;
        try {
            if (Files.exists(STAGE_FILE)) {
                log("stage2 selected");
                phase = 100;
            } else {
                Files.deleteIfExists(CONFIG_FILE);
                log("stage1 selected");
                phase = 0;
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    private void onTick(ServerTickEvent.Post event) {
        if (phase < 0 || event.getServer() != server) return;
        ticks++;
        try {
            switch (phase) {
                case 0 -> stage1Setup();
                case 1 -> stage1VerifyAndStop();
                case 100 -> stage2LoadAndSetupOwner();
                case 101 -> verifyOutsideDefaultRadius();
                case 102 -> verifyInsideDefaultRadiusAndSetupSecondDeath();
                case 103 -> verifySecondGraveAndWaitForConfig();
                case 104 -> waitConfigThenReviveOutside();
                case 105 -> verifyOutsideConfiguredRadius();
                case 106 -> verifyInsideConfiguredRadiusAndTestManualPlacement();
                case 107 -> verifyCreativeBreakAndFinish();
                default -> {}
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    private void stage1Setup() throws Exception {
        log("stage1: preparing real server world");
        prepareArea(new BlockPos(0, 100, 0), 8);

        p1 = makePlayer(OWNER1, "YagmOwnerOne", new BlockPos(0, 100, 0), GameType.SURVIVAL);
        p1.getInventory().clearContent();
        p1.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));

        var zombie = EntityType.ZOMBIE.spawn(level, new BlockPos(0, 100, 0), MobSpawnType.COMMAND);
        require(zombie != null, "zombie failed to spawn at death location");
        zombie.setPos(0.5D, 100.0D, 0.5D);

        fireArchitecturyLivingDeath(p1);
        // The FakePlayer remains a live test actor; move it away immediately so
        // proximity auto-loot cannot consume the grave before persistence is checked.
        p1.setPos(100.5D, 100.0D, 0.5D);

        phase = 1;
        ticks = 0;
    }

    private void stage1VerifyAndStop() throws Exception {
        if (ticks < 3) return;

        grave1 = findGraveAround(new BlockPos(0, 100, 0), 5);
        require(grave1 != null, "no death grave found after actual LIVING_DEATH event");
        require(grave1.equals(new BlockPos(0, 100, 0)),
                "nearby mob changed exact grave placement: expected (0,100,0), got " + grave1);
        require(countItem(p1, Items.DIAMOND) == 0, "death inventory was not removed from player");

        Properties state = new Properties();
        state.setProperty("x", Integer.toString(grave1.getX()));
        state.setProperty("y", Integer.toString(grave1.getY()));
        state.setProperty("z", Integer.toString(grave1.getZ()));
        try (OutputStream out = Files.newOutputStream(STAGE_FILE)) {
            state.store(out, "YAGM runtime persistence stage");
        }

        server.saveEverything(false, true, true);
        log("YAGM_RUNTIME_STAGE1: PASS death-event + mob-independent exact placement; stopping for persistence restart");
        phase = -1;
        server.halt(false);
    }

    private void stage2LoadAndSetupOwner() throws Exception {
        Properties state = new Properties();
        try (InputStream in = Files.newInputStream(STAGE_FILE)) {
            state.load(in);
        }
        grave1 = new BlockPos(
                Integer.parseInt(state.getProperty("x")),
                Integer.parseInt(state.getProperty("y")),
                Integer.parseInt(state.getProperty("z")));

        BlockEntity persisted = level.getBlockEntity(grave1);
        require(isGraveEntity(persisted), "grave did not persist across real dedicated-server restart at " + grave1);
        log("persistence across restart: PASS at " + grave1);

        p1 = makePlayer(OWNER1, "YagmOwnerOne", grave1.offset(17, 0, 0), GameType.SURVIVAL);
        p1.getInventory().clearContent();
        p1.setHealth(p1.getMaxHealth());

        phase = 101;
        ticks = 0;
    }

    private void verifyOutsideDefaultRadius() throws Exception {
        if (ticks < 120) return;
        require(isGraveEntity(level.getBlockEntity(grave1)), "grave auto-looted/despawned while owner stayed at 17 blocks");
        log("grave survived restart plus 120 loaded server ticks without despawning");
        require(countItem(p1, Items.DIAMOND) == 0, "items returned outside default 16-block radius");

        p1.setPos(grave1.getX() + 15.5D, grave1.getY(), grave1.getZ() + 0.5D);
        phase = 102;
        ticks = 0;
    }

    private void verifyInsideDefaultRadiusAndSetupSecondDeath() throws Exception {
        if (ticks < 2) return;
        require(!isGraveEntity(level.getBlockEntity(grave1)), "grave was not removed after auto-loot inside default radius");
        require(countItem(p1, Items.DIAMOND) == 7, "default-radius auto-loot did not restore 7 diamonds");

        Properties cfg = loadProperties(CONFIG_FILE);
        double defaultRadius = Double.parseDouble(cfg.getProperty("autoLootRadius", "NaN"));
        require(Math.abs(defaultRadius - 16.0D) < 0.0001D, "generated config default autoLootRadius was not 16.0: " + defaultRadius);

        cfg.setProperty("autoLootRadius", "3.0");
        cfg.setProperty("placementSearchRadius", "4");
        Files.createDirectories(CONFIG_FILE.getParent());
        try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
            cfg.store(out, "runtime configured radius");
        }

        death2 = new BlockPos(40, 100, 0);
        prepareArea(death2, 8);
        level.setBlock(death2, Blocks.OBSIDIAN.defaultBlockState(), 3);

        p2 = makePlayer(OWNER2, "YagmOwnerTwo", death2, GameType.SURVIVAL);
        p2.getInventory().clearContent();
        p2.getInventory().setItem(0, new ItemStack(Items.EMERALD, 5));

        fireArchitecturyLivingDeath(p2);
        // Keep the live test actor outside even the cached default 16-block radius
        // while the modified config becomes eligible for reload.
        p2.setPos(death2.getX() + 100.5D, death2.getY(), death2.getZ() + 0.5D);

        phase = 103;
        ticks = 0;
    }

    private void verifySecondGraveAndWaitForConfig() throws Exception {
        if (ticks < 3) return;

        require(level.getBlockState(death2).is(Blocks.OBSIDIAN), "grave placement replaced the occupied death block");
        grave2 = findGraveAround(death2, 5);
        require(grave2 != null, "no nearby grave found when exact death block was occupied");
        require(!grave2.equals(death2), "grave used occupied death block instead of nearby free space");
        require(distanceSquared(grave2, death2) <= 16 * 16, "grave fallback moved unreasonably far from death location: " + grave2);
        require(countItem(p2, Items.EMERALD) == 0, "second death inventory was not removed");

        // Keep the owner far away for >1 second so the cached 16-block radius cannot auto-loot
        // before the modified 3-block config is eligible for reload.
        phase = 104;
        ticks = 0;
    }

    private void waitConfigThenReviveOutside() {
        if (ticks < 30) return;
        p2.setPos(grave2.getX() + 4.5D, grave2.getY(), grave2.getZ() + 0.5D);
        phase = 105;
        ticks = 0;
    }

    private void verifyOutsideConfiguredRadius() {
        if (ticks < 2) return;
        require(isGraveEntity(level.getBlockEntity(grave2)), "configured 3-block radius looted grave from 4 blocks");
        require(countItem(p2, Items.EMERALD) == 0, "configured-radius items returned from 4 blocks");

        p2.setPos(grave2.getX() + 2.5D, grave2.getY(), grave2.getZ() + 0.5D);
        phase = 106;
        ticks = 0;
    }

    private void verifyInsideConfiguredRadiusAndTestManualPlacement() throws Exception {
        if (ticks < 2) return;
        require(!isGraveEntity(level.getBlockEntity(grave2)), "configured-radius grave was not removed inside 3 blocks");
        require(countItem(p2, Items.EMERALD) == 5, "configured 3-block auto-loot did not restore 5 emeralds");

        Block graveBlock = findSingleGraveBlock();
        require(graveBlock != null, "could not find a registered YAGM grave block for manual placement");
        require(graveBlock.asItem() instanceof BlockItem, "grave block has no BlockItem");

        manualPos = new BlockPos(80, 100, 0);
        prepareArea(manualPos, 4);
        p3 = makePlayer(BUILDER, "YagmCreativeBuilder", manualPos.offset(0, 0, 2), GameType.CREATIVE);

        ItemStack stack = new ItemStack(graveBlock.asItem(), 1);
        p3.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(manualPos.below()),
                Direction.UP,
                manualPos.below(),
                false);
        InteractionResult placed = ((BlockItem) stack.getItem()).place(
                new BlockPlaceContext(new UseOnContext(p3, InteractionHand.MAIN_HAND, hit)));
        require(placed.consumesAction(), "real BlockItem placement did not succeed: " + placed);
        require(isGraveBlock(level.getBlockState(manualPos).getBlock()), "manual grave block was not placed at target");

        boolean destroyReturn = p3.gameMode.destroyBlock(manualPos);
        log("Creative destroyBlock return value=" + destroyReturn + "; verifying actual world state next");
        phase = 107;
        ticks = 0;
    }

    private void verifyCreativeBreakAndFinish() throws Exception {
        if (ticks < 2) return;
        require(isGraveBlock(level.getBlockState(manualPos).getBlock()), "manually placed grave disappeared after Creative break attempt");

        Properties cfg = loadProperties(CONFIG_FILE);
        require(Math.abs(Double.parseDouble(cfg.getProperty("autoLootRadius")) - 3.0D) < 0.0001D,
                "config change was not retained");

        Files.writeString(Path.of("yagm-runtime-complete.txt"),
                "PASS\n"
                + "registered Architectury LIVING_DEATH event: PASS\n"
                + "nearby mob does not block exact grave spawn: PASS\n"
                + "grave persistence across server restart + 120 loaded ticks: PASS\n"
                + "default auto-loot radius 16: PASS\n"
                + "configurable auto-loot radius 3: PASS\n"
                + "occupied death block not replaced; nearby placement: PASS\n"
                + "manual grave Creative break protection: PASS\n");
        server.saveEverything(false, true, true);
        log("YAGM_RUNTIME_TEST: PASS");
        phase = -1;
        server.halt(false);
    }


    private static void fireArchitecturyLivingDeath(ServerPlayer player) throws Exception {
        Class<?> entityEventClass = Class.forName("dev.architectury.event.events.common.EntityEvent");
        Object event = entityEventClass.getField("LIVING_DEATH").get(null);

        Class<?> eventClass = Class.forName("dev.architectury.event.Event");
        Object invoker = eventClass.getMethod("invoker").invoke(event);

        Class<?> livingDeathInterface = Class.forName("dev.architectury.event.events.common.EntityEvent$LivingDeath");
        java.lang.reflect.Method dieMethod = null;
        for (java.lang.reflect.Method method : livingDeathInterface.getMethods()) {
            if (method.getParameterCount() == 2) {
                dieMethod = method;
                break;
            }
        }
        require(dieMethod != null, "Architectury LIVING_DEATH callback method not found");
        dieMethod.invoke(invoker, player, player.level().damageSources().genericKill());
    }

    private FakePlayer makePlayer(UUID id, String name, BlockPos pos, GameType mode) {
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(id, name));
        p.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        p.setHealth(p.getMaxHealth());
        p.gameMode.changeGameModeForPlayer(mode);
        if (!level.players().contains(p)) {
            level.addNewPlayer(p);
        }
        return p;
    }

    private void prepareArea(BlockPos center, int radius) {
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                level.setBlock(new BlockPos(x, center.getY() - 1, z), Blocks.STONE.defaultBlockState(), 3);
                for (int y = center.getY(); y <= center.getY() + 3; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private BlockPos findGraveAround(BlockPos center, int radius) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = center.getY() - radius; y <= center.getY() + radius; y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockEntity be = level.getBlockEntity(p);
                    if (!isGraveEntity(be)) continue;
                    if (isDecorative(be)) continue;
                    double d = p.distSqr(center);
                    if (d < bestD) {
                        bestD = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    private static boolean isGraveEntity(BlockEntity be) {
        return be != null && be.getClass().getName().equals("it.hurts.sskirillss.yagm.block.entity.GraveStoneBlockEntity");
    }

    private static boolean isDecorative(BlockEntity be) {
        if (!isGraveEntity(be)) return false;
        try {
            return (boolean) be.getClass().getMethod("isDecorative").invoke(be);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isGraveBlock(Block block) {
        return block != null && block.getClass().getName().equals("it.hurts.sskirillss.yagm.block.GraveStoneBlock");
    }

    private static Block findSingleGraveBlock() {
        Block fallback = null;
        for (Block b : BuiltInRegistries.BLOCK) {
            if (!isGraveBlock(b)) continue;
            if (fallback == null) fallback = b;
            try {
                boolean dbl = (boolean) b.getClass().getMethod("isDoubleShape").invoke(b);
                if (!dbl) return b;
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
        return fallback;
    }

    private static int countItem(ServerPlayer player, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(item)) count += s.getCount();
        }
        return count;
    }

    private static int distanceSquared(BlockPos a, BlockPos b) {
        int dx = a.getX() - b.getX();
        int dy = a.getY() - b.getY();
        int dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static Properties loadProperties(Path path) throws Exception {
        require(Files.exists(path), "missing expected config file: " + path);
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            p.load(in);
        }
        return p;
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }

    private static void log(String message) {
        System.out.println("[YAGM-RUNTIME] " + message);
    }

    private void fail(Throwable t) {
        System.err.println("[YAGM-RUNTIME] YAGM_RUNTIME_TEST: FAIL");
        t.printStackTrace();
        try {
            Files.writeString(Path.of("yagm-runtime-failure.txt"), t.toString() + "\n");
        } catch (Throwable ignored) {}
        phase = -1;
        if (server != null) server.halt(false);
    }
}
