package com.bossrush.entity.ai.goal;

import com.bossrush.entity.boss.TrueIronGolemEntity;
import com.bossrush.entity.boss.TrueIronGolemPhase;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.List;

/**
 * Attack #2: a telegraphed ground-slam AOE. Particles ring outward for a
 * short wind-up (so it's dodgeable), then a block-safe explosion damages
 * everyone caught in range. Uses World.ExplosionSourceType.NONE so it
 * never breaks terrain, independent of the mobGriefing gamerule.
 */
public class GolemGroundSlamGoal extends Goal {
    private final TrueIronGolemEntity golem;
    private int cooldown = 60;
    private int windupTicks = -1;
    private static final int WINDUP_DURATION = 20; // 1s telegraph
    private static final double RANGE = 8.0;
    private static final double DAMAGE_RADIUS = 6.0;

    public GolemGroundSlamGoal(TrueIronGolemEntity golem) {
        this.golem = golem;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (cooldown > 0) cooldown--;
        LivingEntity target = golem.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (golem.getPhase() == TrueIronGolemPhase.SLEEPING || golem.getPhase() == TrueIronGolemPhase.DYING) return false;
        return cooldown <= 0 && golem.squaredDistanceTo(target) <= RANGE * RANGE;
    }

    @Override
    public boolean shouldContinue() {
        return windupTicks >= 0;
    }

    @Override
    public void start() {
        windupTicks = 0;
        golem.getNavigation().stop();
    }

    @Override
    public void stop() {
        windupTicks = -1;
    }

    @Override
    public void tick() {
        golem.getNavigation().stop();
        LivingEntity target = golem.getTarget();
        if (target != null) golem.getLookControl().lookAt(target, 30.0f, 30.0f);

        if (golem.getWorld() instanceof ServerWorld sw) {
            double angle = (windupTicks / (double) WINDUP_DURATION) * Math.PI * 4;
            for (int i = 0; i < 3; i++) {
                double a = angle + (i * (Math.PI * 2 / 3));
                double px = golem.getX() + Math.cos(a) * 2.2;
                double pz = golem.getZ() + Math.sin(a) * 2.2;
                sw.spawnParticles(ParticleTypes.CRIT, px, golem.getY() + 0.1, pz, 1, 0, 0, 0, 0);
            }
        }

        windupTicks++;
        if (windupTicks >= WINDUP_DURATION) {
            detonate();
            windupTicks = -1;
            cooldown = 100; // 5s
        }
    }

    private void detonate() {
        World world = golem.getWorld();
        world.playSound(null, golem.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.HOSTILE, 1.5f, 1.0f);
        world.createExplosion(golem, golem.getX(), golem.getY() + 0.1, golem.getZ(),
                3.0f, false, World.ExplosionSourceType.NONE);

        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, golem.getX(), golem.getY() + 0.2, golem.getZ(),
                    1, 0, 0, 0, 0);
        }

        List<LivingEntity> hit = world.getEntitiesByClass(LivingEntity.class,
                golem.getBoundingBox().expand(DAMAGE_RADIUS),
                e -> e != golem && e.isAlive());

        for (LivingEntity e : hit) {
            double dist = Math.sqrt(golem.squaredDistanceTo(e));
            if (dist > DAMAGE_RADIUS) continue;
            DamageSource src = world.getDamageSources().mobAttack(golem);
            float dmg = (float) (10.0 * (1.0 - dist / DAMAGE_RADIUS) + 4.0);
            e.damage(src, dmg);
            e.takeKnockback(1.1, golem.getX() - e.getX(), golem.getZ() - e.getZ());
        }
    }
}
