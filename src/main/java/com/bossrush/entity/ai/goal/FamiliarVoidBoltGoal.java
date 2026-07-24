package com.bossrush.entity.ai.goal;

import com.bossrush.entity.boss.EndFamiliarEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.List;

/**
 * Ranged attack: a small void bolt that flies in a straight line to the
 * target's position at launch time, then deals direct damage in a small
 * radius on impact.
 *
 * This does NOT reuse vanilla DragonFireballEntity, even though it might
 * seem thematically fitting — vanilla's dragon fireball creates a
 * lingering area-damage cloud on impact that does not distinguish the
 * shooter from anyone else nearby. Since the familiar is small, fragile
 * (15 HP), and often fires at fairly close range while hovering near its
 * target, it would regularly catch itself in its own cloud and die to
 * its own attack. This custom bolt explicitly excludes the familiar from
 * its own impact damage instead.
 */
public class FamiliarVoidBoltGoal extends Goal {
    private final EndFamiliarEntity familiar;
    private int cooldown = 30;
    private int flightTicks = -1;
    private Vec3d start;
    private Vec3d end;

    private static final int TRAVEL_DURATION = 8; // quick shot, ~0.4s
    private static final float DAMAGE = 5.0f;
    private static final double IMPACT_RADIUS = 1.5;

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
        return flightTicks >= 0;
    }

    @Override
    public void start() {
        LivingEntity target = familiar.getTarget();
        if (target != null) fire(target);
        cooldown = 50 + familiar.getRandom().nextInt(30); // 2.5-4s
    }

    @Override
    public void stop() {
        flightTicks = -1;
    }

    @Override
    public void tick() {
        if (flightTicks < 0) return;
        flightTicks++;
        double progress = Math.min(1.0, flightTicks / (double) TRAVEL_DURATION);
        Vec3d pos = start.lerp(end, progress);

        if (familiar.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 2, 0.1, 0.1, 0.1, 0.0);
        }

        if (flightTicks >= TRAVEL_DURATION) {
            impact(pos);
            flightTicks = -1;
        }
    }

    private void fire(LivingEntity target) {
        World world = familiar.getWorld();
        start = familiar.getPos().add(0, familiar.getStandingEyeHeight(), 0);
        end = target.getPos().add(0, target.getStandingEyeHeight() * 0.5, 0);
        flightTicks = 0;

        world.playSound(null, familiar.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_SHOOT,
                SoundCategory.HOSTILE, 1.0f, 1.3f);
    }

    private void impact(Vec3d pos) {
        World world = familiar.getWorld();
        world.playSound(null, familiar.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_HURT,
                SoundCategory.HOSTILE, 0.8f, 1.4f);

        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.DRAGON_BREATH, pos.x, pos.y, pos.z, 10, 0.3, 0.3, 0.3, 0.02);
        }

        List<LivingEntity> hit = world.getEntitiesByClass(LivingEntity.class,
                new net.minecraft.util.math.Box(pos.x - IMPACT_RADIUS, pos.y - IMPACT_RADIUS, pos.z - IMPACT_RADIUS,
                        pos.x + IMPACT_RADIUS, pos.y + IMPACT_RADIUS, pos.z + IMPACT_RADIUS),
                e -> e != familiar && e.isAlive()); // explicitly excludes the familiar itself

        DamageSource src = world.getDamageSources().mobAttack(familiar);
        for (LivingEntity e : hit) {
            e.damage(src, DAMAGE);
        }
    }

    /**
     * Debug trigger: fires immediately at the given target, skipping the
     * cooldown check.
     */
    public void debugForceFire(LivingEntity target) {
        fire(target);
    }
}
