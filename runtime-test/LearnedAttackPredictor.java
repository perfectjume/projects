package com.misanthropy.hit_indicator.server;

import com.misanthropy.hit_indicator.network.HitIndicatorNetwork;
import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Universal, animation-agnostic melee predictor.
 *
 * Ground truth is still the real LivingIncomingDamageEvent.  This class only
 * starts the visual windup early.  If the real hit arrives before the ring
 * closes, AttackInterceptor keeps holding the damage for the remaining ticks.
 */
public final class LearnedAttackPredictor {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int HISTORY_TICKS = 20;
    private static final int MAX_SAMPLES_PER_TYPE = 384;
    private static final int MIN_CONFIRMED_HITS = 3;
    private static final int K_NEIGHBORS = 9;
    private static final int MAX_PREDICT_LEAD = 16;
    private static final int MIN_RING_TICKS = 6;
    private static final int SAFETY_TICKS = 2;
    private static final int FALSE_POSITIVE_GRACE = 4;
    private static final double SEARCH_RADIUS = 12.0D;
    private static final double MAX_MODEL_WIDTH = 4.0D;
    private static final double MAX_MODEL_HEIGHT = 6.0D;

    private static final Map<String, Profile> PROFILES = new HashMap<>();
    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    private static final Map<Integer, Prediction> ACTIVE = new HashMap<>();
    private static int preparedRemaining = -1;

    private LearnedAttackPredictor() {}

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();

        ACTIVE.entrySet().removeIf(e -> {
            Prediction p = e.getValue();
            if (now <= p.endTick + FALSE_POSITIVE_GRACE) return false;
            Track tr = TRACKS.get(e.getKey());
            if (tr != null) tr.cooldownUntil = now + 4;
            LOGGER.info("[HI-LEARN] PREDICTION_EXPIRED id={} type={} predictedEnd={} now={}",
                    e.getKey(), p.typeKey, p.endTick, now);
            return true;
        });

        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                for (Mob mob : level.getEntitiesOfClass(
                        Mob.class,
                        player.getBoundingBox().inflate(SEARCH_RADIUS),
                        m -> m.isAlive() && m.getTarget() == player)) {
                    observe(now, mob, player);
                }
            }
        }

        TRACKS.entrySet().removeIf(e -> now - e.getValue().lastSeenTick > 200);
    }

    private static void observe(long now, Mob mob, ServerPlayer player) {
        if (mob.getTags().contains("hit_indicator_no_learn")) return;
        if (mob.getBbWidth() > MAX_MODEL_WIDTH || mob.getBbHeight() > MAX_MODEL_HEIGHT) return;

        String typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        Profile profile = PROFILES.computeIfAbsent(typeKey, k -> new Profile());
        Track track = TRACKS.computeIfAbsent(mob.getId(), id -> new Track(mob.getUUID(), typeKey));

        if (!track.uuid.equals(mob.getUUID())) {
            track = new Track(mob.getUUID(), typeKey);
            TRACKS.put(mob.getId(), track);
        }

        double distance = mob.distanceTo(player);
        double closing = Double.isNaN(track.lastDistance) ? 0.0D : track.lastDistance - distance;
        Vec3 toTarget = player.position().subtract(mob.position());
        double facing = 0.0D;
        if (toTarget.lengthSqr() > 1.0E-6D) {
            facing = mob.getLookAngle().dot(toTarget.normalize());
        }

        boolean los = mob.getSensing().hasLineOfSight(player);
        boolean aggressive = mob.isAggressive();
        boolean swinging = mob.swinging;
        int sinceHit = track.lastHitTick < 0 ? 999 : (int)Math.min(999L, now - track.lastHitTick);

        Frame frame = new Frame(now, distance, closing, facing, los, aggressive, swinging, sinceHit);
        track.frames.addLast(frame);
        while (track.frames.size() > HISTORY_TICKS + 2) track.frames.removeFirst();

        track.lastDistance = distance;
        track.lastSeenTick = now;
        track.victim = player.getUUID();

        if (ACTIVE.containsKey(mob.getId())) return;
        if (AttackInterceptor.isWindingUp(mob)) return;
        if (now < track.cooldownUntil) return;
        if (profile.confirmedHits < MIN_CONFIRMED_HITS || profile.samples.size() < 24) return;

        PredictionEstimate estimate = estimate(profile, frame);
        if (estimate == null) return;
        if (estimate.leadTicks < 2 || estimate.leadTicks > MAX_PREDICT_LEAD) return;
        if (estimate.avgDistance > 1.15D) return;

        int duration = Math.max(MIN_RING_TICKS, Math.min(MAX_PREDICT_LEAD + SAFETY_TICKS,
                estimate.leadTicks + SAFETY_TICKS));

        HitIndicatorNetwork.sendWindup(player, mob.getId(), duration, 0, 0);
        ACTIVE.put(mob.getId(), new Prediction(
                mob.getUUID(), player.getUUID(), typeKey, now, now + duration, estimate.leadTicks));
        track.cooldownUntil = now + duration + 4;

        LOGGER.info("[HI-LEARN] PREDICT type={} id={} lead={} duration={} confidence={} neighbors={} distance={}",
                typeKey, mob.getId(), estimate.leadTicks, duration,
                String.format(java.util.Locale.ROOT, "%.3f", 1.0D / (1.0D + estimate.avgDistance)),
                estimate.neighbors,
                String.format(java.util.Locale.ROOT, "%.3f", estimate.avgDistance));
    }

    /**
     * Called from AttackInterceptor for every eligible direct melee damage attempt.
     * Returns remaining ticks on an already-visible learned ring, or -1 when there
     * was no learned prediction and the normal windup should be created.
     */
    public static void prepareIncoming(LivingEntity attacker, ServerPlayer victim) {
        preparedRemaining = -1;
        if (attacker.getTags().contains("hit_indicator_no_learn")) return;
        preparedRemaining = computeIncoming(attacker, victim);
    }

    private static int computeIncoming(LivingEntity attacker, ServerPlayer victim) {
        long now = victim.getServer().getTickCount();
        String typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()).toString();

        Track track = TRACKS.get(attacker.getId());
        if (track != null && track.uuid.equals(attacker.getUUID())) {
            train(typeKey, track, now);
            track.lastHitTick = now;
        } else {
            Profile p = PROFILES.computeIfAbsent(typeKey, k -> new Profile());
            p.confirmedHits++;
            LOGGER.info("[HI-LEARN] HIT_NO_HISTORY type={} hits={}", typeKey, p.confirmedHits);
        }

        Prediction prediction = ACTIVE.remove(attacker.getId());
        if (prediction == null
                || !prediction.attacker.equals(attacker.getUUID())
                || !prediction.victim.equals(victim.getUUID())) {
            return -1;
        }

        int remaining = (int)Math.max(1L, prediction.endTick - now);
        int error = (int)(now - (prediction.startTick + prediction.estimatedLead));

        LOGGER.info("[HI-LEARN] PREDICTION_MATCH type={} id={} remaining={} timingError={}",
                typeKey, attacker.getId(), remaining, error);
        return remaining;
    }

    public static int adjustDuration(int configuredTicks) {
        return preparedRemaining >= 0 ? Math.max(1, preparedRemaining) : configuredTicks;
    }

    public static void sendWindupMaybe(ServerPlayer player, int attackerId, int durationTicks,
                                       int kind, int moveTicks) {
        try {
            if (preparedRemaining < 0) {
                HitIndicatorNetwork.sendWindup(player, attackerId, durationTicks, kind, moveTicks);
            }
        } finally {
            preparedRemaining = -1;
        }
    }

    public static boolean isPredictionActive(LivingEntity attacker) {
        Prediction p = ACTIVE.get(attacker.getId());
        return p != null && p.attacker.equals(attacker.getUUID());
    }

    public static int confirmedHits(LivingEntity attacker) {
        String key = BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()).toString();
        Profile p = PROFILES.get(key);
        return p == null ? 0 : p.confirmedHits;
    }

    public static int sampleCount(LivingEntity attacker) {
        String key = BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()).toString();
        Profile p = PROFILES.get(key);
        return p == null ? 0 : p.samples.size();
    }

    public static void reset() {
        PROFILES.clear();
        TRACKS.clear();
        ACTIVE.clear();
        preparedRemaining = -1;
    }

    private static void train(String typeKey, Track track, long hitTick) {
        Profile profile = PROFILES.computeIfAbsent(typeKey, k -> new Profile());
        int added = 0;

        for (Frame frame : track.frames) {
            int lead = (int)(hitTick - frame.tick);
            if (lead < 1 || lead > MAX_PREDICT_LEAD) continue;
            profile.samples.add(new Sample(frame, lead));
            added++;
        }

        while (profile.samples.size() > MAX_SAMPLES_PER_TYPE) {
            profile.samples.remove(0);
        }

        profile.confirmedHits++;
        LOGGER.info("[HI-LEARN] TRAIN type={} hits={} added={} samples={}",
                typeKey, profile.confirmedHits, added, profile.samples.size());
    }

    private static PredictionEstimate estimate(Profile profile, Frame current) {
        List<Neighbor> neighbors = new ArrayList<>(profile.samples.size());
        for (Sample sample : profile.samples) {
            double d = featureDistance(current, sample.frame);
            neighbors.add(new Neighbor(d, sample.leadTicks));
        }
        neighbors.sort(Comparator.comparingDouble(n -> n.distance));

        int count = Math.min(K_NEIGHBORS, neighbors.size());
        if (count < 3) return null;

        double weightedLead = 0.0D;
        double totalWeight = 0.0D;
        double avgDistance = 0.0D;

        for (int i = 0; i < count; i++) {
            Neighbor n = neighbors.get(i);
            double weight = 1.0D / (0.08D + n.distance);
            weightedLead += n.leadTicks * weight;
            totalWeight += weight;
            avgDistance += n.distance;
        }

        int lead = (int)Math.round(weightedLead / totalWeight);
        return new PredictionEstimate(lead, avgDistance / count, count);
    }

    private static double featureDistance(Frame a, Frame b) {
        double dd = (a.distance - b.distance) / 2.5D;
        double dc = (a.closingSpeed - b.closingSpeed) / 0.20D;
        double df = (a.facingDot - b.facingDot) / 0.40D;

        int ah = Math.min(a.sinceLastHit, 80);
        int bh = Math.min(b.sinceLastHit, 80);
        double dh = (ah - bh) / 18.0D;

        double d = dd * dd + 0.45D * dc * dc + 0.55D * df * df + 0.70D * dh * dh;
        if (a.lineOfSight != b.lineOfSight) d += 0.80D;
        if (a.aggressive != b.aggressive) d += 0.30D;
        if (a.swinging != b.swinging) d += 0.25D;
        return Math.sqrt(d);
    }

    private static final class Profile {
        int confirmedHits;
        final List<Sample> samples = new ArrayList<>();
    }

    private static final class Track {
        final UUID uuid;
        final String typeKey;
        final Deque<Frame> frames = new ArrayDeque<>();
        UUID victim;
        double lastDistance = Double.NaN;
        long lastHitTick = -1L;
        long lastSeenTick;
        long cooldownUntil;

        Track(UUID uuid, String typeKey) {
            this.uuid = uuid;
            this.typeKey = typeKey;
        }
    }

    private record Frame(long tick, double distance, double closingSpeed, double facingDot,
                         boolean lineOfSight, boolean aggressive, boolean swinging, int sinceLastHit) {}

    private record Sample(Frame frame, int leadTicks) {}
    private record Neighbor(double distance, int leadTicks) {}
    private record PredictionEstimate(int leadTicks, double avgDistance, int neighbors) {}

    private record Prediction(UUID attacker, UUID victim, String typeKey,
                              long startTick, long endTick, int estimatedLead) {}
}
