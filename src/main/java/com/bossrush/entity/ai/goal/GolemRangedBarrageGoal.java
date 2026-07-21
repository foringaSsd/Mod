package com.bossrush.entity.ai.goal;

import com.bossrush.entity.boss.TrueIronGolemEntity;
import com.bossrush.entity.boss.TrueIronGolemPhase;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Direction;

import java.util.EnumSet;

/**
 * Attack #3: fires three shulker bullets followed by three arrows, all
 * aimed at whoever last damaged the golem (falls back to the current
 * target if the last attacker is gone or far away).
 */
public class GolemRangedBarrageGoal extends Goal {
    private final TrueIronGolemEntity golem;
    private int cooldown = 40;
    private int shotsFired;
    private int shotDelay;
    private static final int TOTAL_SHOTS = 6; // 3 shulker + 3 arrows
    private static final int TICKS_BETWEEN_SHOTS = 8;

    public GolemRangedBarrageGoal(TrueIronGolemEntity golem) {
        this.golem = golem;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    private LivingEntity pickTarget() {
        LivingEntity last = golem.getLastAttacker();
        if (last != null && last.isAlive() && golem.squaredDistanceTo(last) < 40 * 40) return last;
        LivingEntity target = golem.getTarget();
        return (target != null && target.isAlive()) ? target : null;
    }

    @Override
    public boolean canStart() {
        if (cooldown > 0) cooldown--;
        if (golem.getPhase() == TrueIronGolemPhase.SLEEPING || golem.getPhase() == TrueIronGolemPhase.DYING) return false;
        return cooldown <= 0 && pickTarget() != null;
    }

    @Override
    public boolean shouldContinue() {
        return shotsFired < TOTAL_SHOTS && pickTarget() != null;
    }

    @Override
    public void start() {
        shotsFired = 0;
        shotDelay = 0;
        golem.getNavigation().stop();
    }

    @Override
    public void stop() {
        cooldown = 140; // 7s between barrages
    }

    /**
     * Fires the full 3-shulker + 3-arrow barrage at the given target
     * immediately, bypassing cooldown and the stagger delay between
     * shots. Used by /bossrush debug attack ttig projectile <target>.
     */
    public void debugForceFire(LivingEntity target) {
        for (int i = 0; i < 3; i++) fireShulkerBullet(target);
        for (int i = 0; i < 3; i++) fireArrow(target);
    }

    @Override
    public void tick() {
        LivingEntity target = pickTarget();
        if (target == null) return;
        golem.getLookControl().lookAt(target, 30.0f, 30.0f);
        golem.getNavigation().stop();

        if (shotDelay > 0) {
            shotDelay--;
            return;
        }

        if (shotsFired < 3) {
            fireShulkerBullet(target);
        } else {
            fireArrow(target);
        }
        shotsFired++;
        shotDelay = TICKS_BETWEEN_SHOTS;
    }

    private void fireShulkerBullet(LivingEntity target) {
        ShulkerBulletEntity bullet = new ShulkerBulletEntity(golem.getWorld(), golem, target, Direction.Axis.Y);
        bullet.setPosition(golem.getX(), golem.getEyeY() - 0.2, golem.getZ());
        golem.getWorld().spawnEntity(bullet);
        golem.getWorld().playSound(null, golem.getBlockPos(), SoundEvents.ENTITY_SHULKER_SHOOT,
                SoundCategory.HOSTILE, 1.0f, 1.0f);
    }

    private void fireArrow(LivingEntity target) {
        ArrowEntity arrow = new ArrowEntity(golem.getWorld(), golem);
        arrow.setPosition(golem.getX(), golem.getEyeY() - 0.1, golem.getZ());

        double d = target.getX() - golem.getX();
        double e = target.getBodyY(0.3333333333333333) - arrow.getY();
        double f = target.getZ() - golem.getZ();
        double horizontalDist = Math.sqrt(d * d + f * f);
        arrow.setVelocity(d, e + horizontalDist * 0.2, f, 1.8f, 6.0f);
        arrow.setDamage(6.0);

        golem.getWorld().spawnEntity(arrow);
        golem.getWorld().playSound(null, golem.getBlockPos(), SoundEvents.ENTITY_SKELETON_SHOOT,
                SoundCategory.HOSTILE, 1.0f, 0.9f);
    }
}
