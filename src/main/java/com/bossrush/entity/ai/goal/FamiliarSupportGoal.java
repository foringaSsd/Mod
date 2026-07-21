package com.bossrush.entity.ai.goal;

import com.bossrush.entity.boss.EndFamiliarEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

/**
 * The "helps him" mechanic made concrete: periodically grants the Ender
 * Dragon a Regeneration buff if it's alive and nearby.
 */
public class FamiliarSupportGoal extends Goal {
    private final EndFamiliarEntity familiar;
    private int cooldown = 100;

    public FamiliarSupportGoal(EndFamiliarEntity familiar) {
        this.familiar = familiar;
    }

    @Override
    public boolean canStart() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        return findDragon() != null;
    }

    @Override
    public boolean shouldContinue() {
        return false;
    }

    @Override
    public void start() {
        EnderDragonEntity dragon = findDragon();
        if (dragon != null) {
            dragon.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1));
            if (familiar.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.HEART, dragon.getX(), dragon.getY() + 3, dragon.getZ(),
                        5, 1.0, 1.0, 1.0, 0.0);
            }
        }
        cooldown = 200 + familiar.getRandom().nextInt(200); // every 10-20s
    }

    private EnderDragonEntity findDragon() {
        List<EnderDragonEntity> dragons = familiar.getWorld().getEntitiesByClass(
                EnderDragonEntity.class, familiar.getBoundingBox().expand(50.0), e -> e.isAlive());
        return dragons.isEmpty() ? null : dragons.get(0);
    }
}
