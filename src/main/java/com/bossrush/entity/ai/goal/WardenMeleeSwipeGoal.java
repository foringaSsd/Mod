package com.bossrush.entity.ai.goal;

import com.bossrush.entity.boss.HollowWardenEntity;
import com.bossrush.entity.boss.HollowWardenPhase;
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
 * Close-range wide swipe: hits every living entity in a radius around the
 * warden, not just the current target — punishes players who group up.
 */
public class WardenMeleeSwipeGoal extends Goal {
    private final HollowWardenEntity warden;
    private final double speed;
    private int cooldown;

    public WardenMeleeSwipeGoal(HollowWardenEntity warden, double speed) {
        this.warden = warden;
        this.speed = speed;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldown > 0) cooldown--;
        LivingEntity target = warden.getTarget();
        return target != null && target.isAlive()
                && warden.getPhase() != HollowWardenPhase.DORMANT
                && warden.getPhase() != HollowWardenPhase.COLLAPSING;
    }

    @Override
    public boolean shouldContinue() {
        LivingEntity target = warden.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void tick() {
        LivingEntity target = warden.getTarget();
        if (target == null) return;
        warden.getLookControl().lookAt(target, 30.0f, 30.0f);

        boolean inRange = warden.getBoundingBox().expand(1.5, 0.8, 1.5).intersects(target.getBoundingBox());
        if (inRange) {
            warden.getNavigation().stop();
            if (cooldown <= 0) {
                swipe();
                cooldown = 35; // ~1.75s
            }
        } else {
            warden.getNavigation().startMovingTo(target, speed);
        }
    }

    private void swipe() {
        World world = warden.getWorld();
        List<LivingEntity> hit = world.getEntitiesByClass(LivingEntity.class,
                warden.getBoundingBox().expand(2.5, 1.0, 2.5),
                e -> e != warden && e.isAlive());

        DamageSource src = world.getDamageSources().mobAttack(warden);
        for (LivingEntity e : hit) {
            e.damage(src, 13.0f);
            e.takeKnockback(0.8, warden.getX() - e.getX(), warden.getZ() - e.getZ());
        }

        world.playSound(null, warden.getBlockPos(), SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT,
                SoundCategory.HOSTILE, 1.2f, 1.0f);
        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.SCULK_CHARGE_POP, warden.getX(), warden.getY() + 1.0, warden.getZ(),
                    15, 1.2, 0.6, 1.2, 0.02);
        }
    }
}
