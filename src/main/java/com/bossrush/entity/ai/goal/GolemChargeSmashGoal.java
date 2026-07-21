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
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;

/**
 * New Phase 2 attack: the golem picks a direction toward its target and
 * charges in a straight line, damaging and knocking back anything it
 * plows into. Only available once the arena has expanded (Phase 2+),
 * taking advantage of the extra room.
 */
public class GolemChargeSmashGoal extends Goal {
    private final TrueIronGolemEntity golem;
    private int cooldown = 80;
    private int chargeTicks = -1;
    private Vec3d direction = Vec3d.ZERO;
    private static final int MAX_CHARGE_TICKS = 30;
    private static final double CHARGE_SPEED = 0.9;

    public GolemChargeSmashGoal(TrueIronGolemEntity golem) {
        this.golem = golem;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldown > 0) cooldown--;
        if (golem.getPhase() != TrueIronGolemPhase.ARENA_CHANGE) return false;
        LivingEntity target = golem.getTarget();
        if (target == null || !target.isAlive()) return false;
        double distSq = golem.squaredDistanceTo(target);
        return cooldown <= 0 && distSq > 6 * 6 && distSq < 16 * 16;
    }

    @Override
    public boolean shouldContinue() {
        return chargeTicks >= 0 && chargeTicks < MAX_CHARGE_TICKS;
    }

    @Override
    public void start() {
        LivingEntity target = golem.getTarget();
        if (target != null) {
            direction = new Vec3d(target.getX() - golem.getX(), 0, target.getZ() - golem.getZ()).normalize();
        }
        chargeTicks = 0;
        golem.getWorld().playSound(null, golem.getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR,
                SoundCategory.HOSTILE, 1.2f, 0.7f);
    }

    @Override
    public void stop() {
        chargeTicks = -1;
        cooldown = 160; // 8s
        golem.setVelocity(golem.getVelocity().multiply(0.1, 1.0, 0.1));
    }

    @Override
    public void tick() {
        golem.setVelocity(direction.x * CHARGE_SPEED, golem.getVelocity().y, direction.z * CHARGE_SPEED);
        golem.velocityModified = true;
        golem.getLookControl().lookAt(golem.getX() + direction.x, golem.getY(), golem.getZ() + direction.z);

        if (golem.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.CLOUD, golem.getX(), golem.getY() + 0.2, golem.getZ(),
                    2, 0.3, 0.1, 0.3, 0.01);
        }

        List<LivingEntity> hit = golem.getWorld().getEntitiesByClass(LivingEntity.class,
                golem.getBoundingBox().expand(0.5),
                e -> e != golem && e.isAlive());
        for (LivingEntity e : hit) {
            DamageSource src = golem.getWorld().getDamageSources().mobAttack(golem);
            e.damage(src, 12.0f);
            e.takeKnockback(1.4, golem.getX() - e.getX(), golem.getZ() - e.getZ());
            chargeTicks = MAX_CHARGE_TICKS; // stop the charge once it connects
        }

        chargeTicks++;
    }
}
