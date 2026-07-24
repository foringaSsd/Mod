package com.bossrush.entity.ai.goal;

import com.bossrush.entity.boss.HollowWardenEntity;
import com.bossrush.entity.boss.HollowWardenPhase;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.List;

/**
 * Ranged line attack: ~1.25s telegraph tracing a particle line toward the
 * target, then a beam that damages and applies Darkness to everything
 * within a narrow corridor along that line, ignoring most thin cover.
 */
public class WardenSonicPulseGoal extends Goal {
    private final HollowWardenEntity warden;
    private int cooldown = 60;
    private int windup = -1;
    private static final int WINDUP_DURATION = 25;
    private static final double RANGE = 16.0;
    private static final double BEAM_WIDTH = 1.5;

    public WardenSonicPulseGoal(HollowWardenEntity warden) {
        this.warden = warden;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldown > 0) cooldown--;
        if (warden.getPhase() == HollowWardenPhase.DORMANT || warden.getPhase() == HollowWardenPhase.COLLAPSING) {
            return false;
        }
        LivingEntity target = warden.getTarget();
        if (target == null || !target.isAlive()) return false;
        double distSq = warden.squaredDistanceTo(target);
        return cooldown <= 0 && distSq <= RANGE * RANGE && distSq > 4 * 4;
    }

    @Override
    public boolean shouldContinue() {
        return windup >= 0 && warden.getTarget() != null && warden.getTarget().isAlive();
    }

    @Override
    public void start() {
        windup = 0;
        warden.getNavigation().stop();
        warden.getWorld().playSound(null, warden.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE,
                SoundCategory.HOSTILE, 1.5f, 1.0f);
    }

    @Override
    public void stop() {
        windup = -1;
        cooldown = 90; // 4.5s
    }

    @Override
    public void tick() {
        LivingEntity target = warden.getTarget();
        if (target == null) return;
        warden.getLookControl().lookAt(target, 30.0f, 30.0f);

        if (warden.getWorld() instanceof ServerWorld sw) {
            Vec3d dir = target.getPos().subtract(warden.getPos()).normalize();
            Vec3d origin = warden.getPos().add(0, warden.getStandingEyeHeight(), 0);
            for (int i = 1; i <= 12; i++) {
                Vec3d p = origin.add(dir.multiply(i));
                sw.spawnParticles(ParticleTypes.SCULK_SOUL, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }

        windup++;
        if (windup >= WINDUP_DURATION) {
            firePulse(target);
            windup = -1;
            cooldown = 90;
        }
    }

    private void firePulse(LivingEntity primaryTarget) {
        World world = warden.getWorld();
        world.playSound(null, warden.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                SoundCategory.HOSTILE, 2.0f, 1.0f);

        Vec3d origin = warden.getPos().add(0, warden.getStandingEyeHeight(), 0);
        Vec3d dir = primaryTarget.getPos().subtract(warden.getPos()).normalize();

        List<LivingEntity> nearby = world.getEntitiesByClass(LivingEntity.class,
                warden.getBoundingBox().expand(RANGE),
                e -> e != warden && e.isAlive());

        DamageSource src = world.getDamageSources().mobAttack(warden);
        for (LivingEntity e : nearby) {
            Vec3d toEntity = e.getPos().subtract(origin);
            double along = toEntity.dotProduct(dir);
            if (along < 0 || along > RANGE) continue;
            Vec3d closestPoint = origin.add(dir.multiply(along));
            if (e.getPos().distanceTo(closestPoint) > BEAM_WIDTH) continue;

            e.damage(src, 10.0f);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 150, 0));
            e.takeKnockback(1.2, warden.getX() - e.getX(), warden.getZ() - e.getZ());
        }

        if (world instanceof ServerWorld sw) {
            for (int i = 1; i <= 16; i++) {
                Vec3d p = origin.add(dir.multiply(i));
                sw.spawnParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 0, 0, 0, 0, 0);
            }
        }
    }

    /**
     * Debug trigger: fires the pulse immediately at the given target,
     * skipping the windup telegraph.
     */
    public void debugForcePulse(LivingEntity target) {
        firePulse(target);
    }
}
