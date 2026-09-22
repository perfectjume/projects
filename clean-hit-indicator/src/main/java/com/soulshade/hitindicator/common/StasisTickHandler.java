package com.soulshade.hitindicator.common;

import com.soulshade.hitindicator.client.FrozenRenderClock;
import com.soulshade.hitindicator.client.TelegraphState;
import com.soulshade.hitindicator.server.AttackDelayManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber
public final class StasisTickHandler {
    private StasisTickHandler() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)) {
            return;
        }

        if (entity.level().isClientSide()) {
            boolean frozen = TelegraphState.isFrozen(entity.getId());

            if (!frozen && FrozenRenderClock.shouldHoldReleaseTick(living)) {
                frozen = true;
            }

            if (!frozen) {
                return;
            }

            syncRenderHistory(living);
            living.tickCount--;
            event.setCanceled(true);
            return;
        }

        if (AttackDelayManager.isFrozen(entity)) {
            // NeoForge increments tickCount immediately before EntityTickEvent.Pre.
            // Undo that increment so age/tick based animations are truly frozen.
            living.tickCount--;
            event.setCanceled(true);
        }
    }

    private static void syncRenderHistory(LivingEntity living) {
        double x = living.getX();
        double y = living.getY();
        double z = living.getZ();

        living.xOld = x;
        living.yOld = y;
        living.zOld = z;
        living.xo = x;
        living.yo = y;
        living.zo = z;

        living.xRotO = living.getXRot();
        living.yRotO = living.getYRot();
        living.walkDistO = living.walkDist;
        living.yBodyRotO = living.yBodyRot;
        living.yHeadRotO = living.yHeadRot;
        living.oAttackAnim = living.attackAnim;
    }
}
