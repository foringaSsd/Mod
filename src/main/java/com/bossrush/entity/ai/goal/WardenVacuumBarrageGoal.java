package com.bossrush.entity.ai.goal;

import com.bossrush.entity.boss.HollowWardenEntity;
import com.bossrush.entity.boss.HollowWardenPhase;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.List;

/**
 * Phase 2 signature attack: the warden anchors itself and opens a 5s
 * vortex. Every nearby living entity gets pulled toward it, while it
 * simultaneously fires arrows and thrown Harming potions at its target —
 * getting sucked in means getting shot at the same time.
 */
public class WardenVacuumBarrageGoal extends Goal {
    private final HollowWardenEntity warden;
    private int cooldown = 100;
    private int activeTicks = -1;
    private int shotTimer;
    private int shotIndex;
    private static final int DURATION = 100; // 5s
    private static final double PULL_RADIUS = 14.0;
    private static final double PULL_STRENGTH = 0.14;
    private static final double MIN_PULL_DIST = 2.5;

    public WardenVacuumBarrageGoal(HollowWardenEntity warden) {
        this.warden = warden;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldown > 0) cooldown--;
        if (warden.getPhase() != HollowWardenPhase.CONSUMING) return false;
        LivingEntity target = warden.getTarget();
        return cooldown <= 0 && target != null && target.isAlive();
    }

    @Override
    public boolean shouldContinue() {
        return activeTicks >= 0 && activeTicks < DURATION;
    }

    @Override
    public void start() {
        activeTicks = 0;
        shotTimer = 0;
        shotIndex = 0;
        warden.getNavigation().stop();
        warden.getWorld().playSound(null, warden.getBlockPos(), SoundEvents.ENTITY_WARDEN_ROAR,
                SoundCategory.HOSTILE, 2.0f, 0.6f);
    }

    @Override
    public void stop() {
        activeTicks = -1;
        cooldown = 200; // 10s between vortexes
    }

    @Override
    public void tick() {
        LivingEntity target = warden.getTarget();
        if (target != null) warden.getLookControl().lookAt(target, 30.0f, 30.0f);

        pullEntities();

        if (warden.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.SCULK_SOUL, warden.getX(), warden.getY() + 1.2, warden.getZ(),
                    3, 1.0, 0.5, 1.0, 0.02);
        }

        shotTimer--;
        if (shotTimer <= 0 && target != null && target.isAlive()) {
            fireAtTarget(target);
            shotTimer = 15; // shot every 0.75s while the vortex is open
        }

        activeTicks++;
    }

    private void pullEntities() {
        World world = warden.getWorld();
        List<LivingEntity> nearby = world.getEntitiesByClass(LivingEntity.class,
                warden.getBoundingBox().expand(PULL_RADIUS),
                e -> e != warden && e.isAlive());

        for (LivingEntity e : nearby) {
            double dx = warden.getX() - e.getX();
            double dz = warden.getZ() - e.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < MIN_PULL_DIST) continue; // don't yank them into the hitbox
            e.setVelocity(e.getVelocity().add((dx / dist) * PULL_STRENGTH, 0.02, (dz / dist) * PULL_STRENGTH));
            e.velocityModified = true;
        }
    }

    private void fireAtTarget(LivingEntity target) {
        World world = warden.getWorld();
        shotIndex++;

        if (shotIndex % 3 == 0) {
            firePotion(world, target);
        } else {
            fireArrow(world, target);
        }
    }

    private void firePotion(World world, LivingEntity target) {
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);
        PotionUtil.setPotion(stack, Potions.HARMING);

        PotionEntity potion = new PotionEntity(world, warden);
        potion.setItem(stack);
        potion.setPosition(warden.getX(), warden.getEyeY() - 0.2, warden.getZ());

        double d = target.getX() - warden.getX();
        double e = target.getBodyY(0.3333333333333333) - potion.getY();
        double f = target.getZ() - warden.getZ();
        double horiz = Math.sqrt(d * d + f * f);
        potion.setVelocity(d, e + horiz * 0.2, f, 0.75f, 8.0f);

        world.spawnEntity(potion);
        world.playSound(null, warden.getBlockPos(), SoundEvents.ENTITY_WITCH_THROW, SoundCategory.HOSTILE, 1.0f, 0.8f);
    }

    private void fireArrow(World world, LivingEntity target) {
        ArrowEntity arrow = new ArrowEntity(world, warden);
        arrow.setPosition(warden.getX(), warden.getEyeY() - 0.1, warden.getZ());

        double d = target.getX() - warden.getX();
        double e = target.getBodyY(0.3333333333333333) - arrow.getY();
        double f = target.getZ() - warden.getZ();
        double horiz = Math.sqrt(d * d + f * f);
        arrow.setVelocity(d, e + horiz * 0.2, f, 1.8f, 4.0f);
        arrow.setDamage(5.0);

        world.spawnEntity(arrow);
        world.playSound(null, warden.getBlockPos(), SoundEvents.ENTITY_SKELETON_SHOOT, SoundCategory.HOSTILE, 1.0f, 0.7f);
    }
}
