package com.bossrush.entity.util;

import com.bossrush.entity.ModEntities;
import com.bossrush.entity.boss.EndFamiliarEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

/**
 * Spawns End Familiars near the Ender Dragon.
 *
 * This uses Fabric's entity-load event rather than a precise "dragon just
 * spawned" hook, because that needs a Mixin into EnderDragonFight (not set
 * up in this project yet). It fires whenever a dragon entity loads into a
 * world, and only spawns familiars if none already exist nearby — this
 * covers a fresh dragon spawn and a dragon respawn after crystals are
 * re-lit.
 *
 * Caveat: if every familiar dies while the dragon is still alive, then you
 * leave and re-enter the End (forcing a chunk reload), a new batch will
 * spawn again. If you want an exact one-time-per-fight trigger instead,
 * this is the place to swap in a Mixin on EnderDragonFight's spawn logic.
 */
public class EndFamiliarSpawner {
    private static final int FAMILIAR_COUNT = 2; // assumption — not specified, tune freely
    private static final double NEARBY_CHECK_RADIUS = 150.0;

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof EnderDragonEntity dragon)) return;
            if (!(world instanceof ServerWorld serverWorld)) return;

            List<EndFamiliarEntity> existing = serverWorld.getEntitiesByClass(
                    EndFamiliarEntity.class,
                    dragon.getBoundingBox().expand(NEARBY_CHECK_RADIUS),
                    e -> true
            );
            if (!existing.isEmpty()) return;

            for (int i = 0; i < FAMILIAR_COUNT; i++) {
                spawnFamiliar(serverWorld, dragon);
            }
        });
    }

    private static void spawnFamiliar(ServerWorld world, LivingEntity dragon) {
        EndFamiliarEntity familiar = ModEntities.END_FAMILIAR.create(world);
        if (familiar == null) return;

        double angle = world.getRandom().nextDouble() * Math.PI * 2;
        double dist = 8.0 + world.getRandom().nextDouble() * 6.0;
        double x = dragon.getX() + Math.cos(angle) * dist;
        double z = dragon.getZ() + Math.sin(angle) * dist;
        double y = dragon.getY() + 5 + world.getRandom().nextInt(10);

        familiar.refreshPositionAndAngles(x, y, z, 0f, 0f);
        world.spawnEntity(familiar);
    }
}
