package com.soulshade.hitindicator.server;

import com.soulshade.hitindicator.Settings;
import com.soulshade.hitindicator.network.TelegraphEndPayload;
import com.soulshade.hitindicator.network.TelegraphStartPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber
public final class AttackDelayManager {
    private static final Map<UUID, PendingHit> PENDING = new HashMap<>();
    private static final ThreadLocal<Boolean> REPLAYING = ThreadLocal.withInitial(() -> false);

    private AttackDelayManager() {}

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (REPLAYING.get()) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim) || victim.level().isClientSide()) {
            return;
        }

        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof Mob attacker)) {
            return;
        }

        // One active telegraph per attacker. Any duplicate hit attempt while
        // the attacker is frozen is suppressed rather than stacked.
        if (PENDING.containsKey(attacker.getUUID())) {
            event.setCanceled(true);
            return;
        }

        event.setCanceled(true);

        PendingHit pending = new PendingHit(
                attacker,
                victim,
                event.getSource(),
                event.getOriginalAmount(),
                Settings.TELEGRAPH_TICKS);

        PENDING.put(attacker.getUUID(), pending);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                attacker,
                new TelegraphStartPayload(attacker.getId(), Settings.TELEGRAPH_TICKS));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Map.Entry<UUID, PendingHit>> iterator = PENDING.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingHit> entry = iterator.next();
            PendingHit pending = entry.getValue();

            if (pending.attacker.isRemoved()
                    || pending.victim.isRemoved()
                    || !pending.victim.isAlive()
                    || pending.attacker.level() != pending.victim.level()) {
                iterator.remove();
                sendEnd(pending.attacker);
                continue;
            }

            pending.remainingTicks--;
            if (pending.remainingTicks > 0) {
                continue;
            }

            iterator.remove();
            sendEnd(pending.attacker);

            REPLAYING.set(true);
            try {
                pending.victim.hurt(pending.source, pending.amount);
            } finally {
                REPLAYING.set(false);
            }
        }
    }

    public static boolean isFrozen(Entity entity) {
        return entity != null && PENDING.containsKey(entity.getUUID());
    }

    private static void sendEnd(Entity attacker) {
        if (attacker != null && !attacker.isRemoved() && !attacker.level().isClientSide()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    attacker,
                    new TelegraphEndPayload(attacker.getId()));
        }
    }

    private static final class PendingHit {
        final Mob attacker;
        final Player victim;
        final DamageSource source;
        final float amount;
        int remainingTicks;

        PendingHit(Mob attacker, Player victim, DamageSource source, float amount, int remainingTicks) {
            this.attacker = attacker;
            this.victim = victim;
            this.source = source;
            this.amount = amount;
            this.remainingTicks = remainingTicks;
        }
    }
}
