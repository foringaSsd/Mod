package com.bossrush.entity.ai.goal;

import com.bossrush.entity.boss.ShulkerSovereignEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.List;

/**
 * The "better shulker" signature attack: a slow, heavily telegraphed
 * bolt that flies in a straight line toward where the target was when
 * it launched (not fully homing, so it's dodgeable by moving), and on
 * impact deals a large flat damage burst instead of the vanilla shulker
 * bullet's Levitation effect. Runs on its own timer independent of the
 * shield loop.
 */
public class SovereignVoidBoltGoal extends Goal {
    private final ShulkerSovereignEntity boss;
    private int cooldown = 40;
    private int windup = -1;
    private int flightTicks = -1;
    private Vec3d start;
    private Vec3d end;

    private static final int WINDUP_DURATION = 25; // ~1.25s telegraph
    private static final int TRAVEL_DURATION = 20; // ~1s flight
    private static final float DAMAGE = 22.0f;
    private static final double IMPACT_RADIUS = 2.0;

    public SovereignVoidBoltGoal(ShulkerSovereignEntity boss) {
        this.boss = boss;
        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldown > 0) cooldown--;
        LivingEntity target = boss.getTarget();
        return cooldown <= 0 && target != null && target.isAlive();
    }

    @Override
    public boolean shouldContinue() {
        return windup >= 0 || flightTicks >= 0;
    }

    @Override
    public void start() {
        windup = 0;
        boss.getWorld().playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_SHULKER_SHOOT,
                SoundCategory.HOSTILE, 2.0f, 0.5f);
    }

    @Override
    public void stop() {
        windup = -1;
        flightTicks = -1;
        cooldown = 100; // 5s between bolts
    }

    @Override
    public void tick() {
        LivingEntity target = boss.getTarget();

        if (windup >= 0) {
            if (target != null) boss.getLookControl().lookAt(target, 30.0f, 30.0f);
            if (boss.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + 1.5, boss.getZ(),
                        4, 0.3, 0.3, 0.3, 0.02);
            }
            windup++;
            if (windup >= WINDUP_DURATION) {
                windup = -1;
                if (target != null) launch(target);
            }
            return;
        }

        if (flightTicks >= 0) {
            flightTicks++;
            double progress = Math.min(1.0, flightTicks / (double) TRAVEL_DURATION);
            Vec3d pos = start.lerp(end, progress);

            if (boss.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.DRAGON_BREATH, pos.x, pos.y, pos.z, 3, 0.2, 0.2, 0.2, 0.0);
                sw.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, 0.1, 0.1, 0.1, 0.0);
            }

            if (flightTicks >= TRAVEL_DURATION) {
                impact(pos);
                flightTicks = -1;
            }
        }
    }

    private void launch(LivingEntity target) {
        start = boss.getPos().add(0, boss.getStandingEyeHeight(), 0);
        end = target.getPos().add(0, 1.0, 0);
        flightTicks = 0;
        boss.getWorld().playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_SHOOT,
                SoundCategory.HOSTILE, 2.0f, 0.6f);
    }

    private void impact(Vec3d pos) {
        World world = boss.getWorld();
        world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.HOSTILE, 2.0f, 0.7f);

        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }

        Box impactBox = new Box(
                pos.x - IMPACT_RADIUS, pos.y - IMPACT_RADIUS, pos.z - IMPACT_RADIUS,
                pos.x + IMPACT_RADIUS, pos.y + IMPACT_RADIUS, pos.z + IMPACT_RADIUS
        );
        List<LivingEntity> hit = world.getEntitiesByClass(LivingEntity.class, impactBox,
                e -> e != boss && e.isAlive());

        DamageSource src = world.getDamageSources().mobAttack(boss);
        for (LivingEntity e : hit) {
            e.damage(src, DAMAGE);
            e.takeKnockback(1.0, boss.getX() - e.getX(), boss.getZ() - e.getZ());
        }
    }
}
