package com.bossrush.entity.ai.goal;

import com.bossrush.entity.boss.EndFamiliarEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.projectile.DragonFireballEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * Ranged attack: a small void bolt (reuses vanilla DragonFireballEntity,
 * fitting since this thing is literally the dragon's helper — impact
 * behavior, including the lingering damage cloud, comes for free from
 * the vanilla class).
 */
public class FamiliarVoidBoltGoal extends Goal {
    private final EndFamiliarEntity familiar;
    private int cooldown = 30;

    public FamiliarVoidBoltGoal(EndFamiliarEntity familiar) {
        this.familiar = familiar;
        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldown > 0) cooldown--;
        LivingEntity target = familiar.getTarget();
        return cooldown <= 0 && target != null && target.isAlive()
                && familiar.squaredDistanceTo(target) < 20 * 20;
    }

    @Override
    public boolean shouldContinue() {
        return false; // instantaneous, re-triggered via canStart on cooldown
    }

    @Override
    public void start() {
        LivingEntity target = familiar.getTarget();
        if (target != null) fire(target);
        cooldown = 50 + familiar.getRandom().nextInt(30); // 2.5-4s
    }

    private void fire(LivingEntity target) {
        World world = familiar.getWorld();
        Vec3d origin = familiar.getPos().add(0, familiar.getStandingEyeHeight(), 0);
        Vec3d targetPos = target.getPos().add(0, target.getStandingEyeHeight() * 0.5, 0);
        Vec3d direction = targetPos.subtract(origin).normalize();

        DragonFireballEntity fireball = new DragonFireballEntity(world, familiar, direction.x, direction.y, direction.z);
        fireball.setPosition(origin.x, origin.y, origin.z);
        world.spawnEntity(fireball);

        world.playSound(null, familiar.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_SHOOT,
                SoundCategory.HOSTILE, 1.0f, 1.3f);
    }

    /**
     * Debug trigger: fires immediately at the given target, skipping the
     * cooldown check.
     */
    public void debugForceFire(LivingEntity target) {
        fire(target);
    }
}
