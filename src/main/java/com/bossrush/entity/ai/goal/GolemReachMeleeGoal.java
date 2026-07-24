package com.bossrush.entity.ai.goal;

import com.bossrush.entity.boss.TrueIronGolemEntity;
import com.bossrush.entity.boss.TrueIronGolemPhase;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * Attack #1: standard melee, plus a "reach" strike so a 1x1 tower a few
 * blocks tall no longer trivializes the fight — the golem yanks the player
 * off the pillar and hits them anyway.
 */
public class GolemReachMeleeGoal extends Goal {
    private final TrueIronGolemEntity golem;
    private final double speed;
    private final double reachHorizontal;
    private final double reachVertical;
    private int cooldown;

    public GolemReachMeleeGoal(TrueIronGolemEntity golem, double speed, double reachHorizontal, double reachVertical) {
        this.golem = golem;
        this.speed = speed;
        this.reachHorizontal = reachHorizontal;
        this.reachVertical = reachVertical;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldown > 0) cooldown--;
        LivingEntity target = golem.getTarget();
        return target != null && target.isAlive()
                && golem.getPhase() != TrueIronGolemPhase.SLEEPING
                && golem.getPhase() != TrueIronGolemPhase.DYING;
    }

    @Override
    public boolean shouldContinue() {
        LivingEntity target = golem.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void tick() {
        if (cooldown > 0) cooldown--; // must decrement here too — shouldContinue() keeps this goal
                                       // running indefinitely while a target is alive, so canStart()
                                       // (where this used to be the only decrement) never gets called
                                       // again once the goal starts, freezing the cooldown forever.
        LivingEntity target = golem.getTarget();
        if (target == null) return;

        golem.getLookControl().lookAt(target, 30.0f, 30.0f);

        double dx = target.getX() - golem.getX();
        double dz = target.getZ() - golem.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        double verticalDist = target.getY() - golem.getY();

        boolean withinPillarReach = horizontalDist <= reachHorizontal
                && verticalDist > 1.5 && verticalDist <= reachVertical;
        boolean withinNormalReach = golem.getBoundingBox().expand(1.2, 0.6, 1.2).intersects(target.getBoundingBox());

        if (withinNormalReach || withinPillarReach) {
            golem.getNavigation().stop();
            if (cooldown <= 0) {
                performAttack(target, withinPillarReach);
                cooldown = 30; // 1.5s between hits
            }
        } else {
            golem.getNavigation().startMovingTo(target, speed);
        }
    }

    private void performAttack(LivingEntity target, boolean pillarStrike) {
        float damage = (float) golem.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        DamageSource source = golem.getWorld().getDamageSources().mobAttack(golem);

        if (pillarStrike) {
            // Pull the player down off their tower before the hit lands.
            Vec3d pull = new Vec3d(golem.getX() - target.getX(), 0.0, golem.getZ() - target.getZ())
                    .normalize().multiply(0.9, 0.0, 0.9);
            target.setVelocity(target.getVelocity().add(pull.x, 0.45, pull.z));
            target.velocityModified = true;
            damage *= 1.15f;
        }

        target.damage(source, damage);
        target.takeKnockback(0.6, golem.getX() - target.getX(), golem.getZ() - target.getZ());

        golem.getWorld().playSound(null, golem.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK,
                SoundCategory.HOSTILE, 1.0f, pillarStrike ? 0.8f : 1.0f);

        if (golem.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5), target.getZ(),
                    10, 0.3, 0.3, 0.3, 0.1);
        }
    }
}
