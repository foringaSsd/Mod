package com.bossrush.entity.ai.goal;

import com.bossrush.entity.boss.BastionWarlordEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.EnumSet;

/**
 * Attack #1: standard melee chase-and-hit, but every landed hit ignites
 * the target for 5 seconds on top of the damage.
 */
public class WarlordMoltenSlamGoal extends Goal {
    private final BastionWarlordEntity warlord;
    private final double speed;
    private int cooldown;

    public WarlordMoltenSlamGoal(BastionWarlordEntity warlord, double speed) {
        this.warlord = warlord;
        this.speed = speed;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldown > 0) cooldown--;
        LivingEntity target = warlord.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean shouldContinue() {
        LivingEntity target = warlord.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void tick() {
        if (cooldown > 0) cooldown--; // see GolemReachMeleeGoal for why this is needed here too
        LivingEntity target = warlord.getTarget();
        if (target == null) return;
        warlord.getLookControl().lookAt(target, 30.0f, 30.0f);

        boolean inRange = warlord.getBoundingBox().expand(1.3, 0.6, 1.3).intersects(target.getBoundingBox());
        if (inRange) {
            warlord.getNavigation().stop();
            if (cooldown <= 0) {
                attack(target);
                cooldown = warlord.isEnraged() ? 18 : 28;
            }
        } else {
            warlord.getNavigation().startMovingTo(target, speed);
        }
    }

    private void attack(LivingEntity target) {
        float damage = (float) warlord.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        DamageSource src = warlord.getWorld().getDamageSources().mobAttack(warlord);
        target.damage(src, damage);
        target.setFireTicks(100); // 5s burn
        target.takeKnockback(0.5, warlord.getX() - target.getX(), warlord.getZ() - target.getZ());

        warlord.getWorld().playSound(null, warlord.getBlockPos(), SoundEvents.ENTITY_PIGLIN_BRUTE_ANGRY,
                SoundCategory.HOSTILE, 1.2f, 0.8f);
        warlord.getWorld().playSound(null, warlord.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE,
                SoundCategory.HOSTILE, 1.0f, 1.2f);

        if (warlord.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.FLAME, target.getX(), target.getBodyY(0.5), target.getZ(),
                    12, 0.3, 0.3, 0.3, 0.05);
        }
    }

    /**
     * Debug trigger: hits the target immediately regardless of range or
     * cooldown.
     */
    public void debugForceSlam(LivingEntity target) {
        attack(target);
    }
}
