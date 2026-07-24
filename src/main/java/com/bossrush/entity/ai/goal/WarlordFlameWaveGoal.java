package com.bossrush.entity.ai.goal;

import com.bossrush.entity.boss.BastionWarlordEntity;
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
 * Attack #2: ~1s telegraph, then a fire cone in front of the warlord
 * (direction locked at the moment the telegraph starts) damages and
 * ignites everything caught in it.
 */
public class WarlordFlameWaveGoal extends Goal {
    private final BastionWarlordEntity warlord;
    private int cooldown = 60;
    private int windup = -1;
    private Vec3d direction = Vec3d.ZERO;

    private static final int WINDUP_DURATION = 20; // ~1s
    private static final double RANGE = 8.0;
    private static final double CONE_DOT_THRESHOLD = 0.8; // ~35 degree half-angle
    private static final float DAMAGE = 10.0f;

    public WarlordFlameWaveGoal(BastionWarlordEntity warlord) {
        this.warlord = warlord;
        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldown > 0) cooldown--;
        LivingEntity target = warlord.getTarget();
        return cooldown <= 0 && target != null && target.isAlive()
                && warlord.squaredDistanceTo(target) <= RANGE * RANGE;
    }

    @Override
    public boolean shouldContinue() {
        return windup >= 0;
    }

    @Override
    public void start() {
        windup = 0;
        LivingEntity target = warlord.getTarget();
        direction = target != null
                ? target.getPos().subtract(warlord.getPos()).normalize()
                : warlord.getRotationVec(1.0f);
        warlord.getWorld().playSound(null, warlord.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE,
                SoundCategory.HOSTILE, 1.5f, 0.6f);
    }

    @Override
    public void stop() {
        windup = -1;
        cooldown = warlord.isEnraged() ? 60 : 100;
    }

    @Override
    public void tick() {
        if (warlord.getWorld() instanceof ServerWorld sw) {
            for (int i = 2; i <= 8; i++) {
                Vec3d p = warlord.getPos().add(direction.multiply(i)).add(0, 0.3, 0);
                sw.spawnParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.4, 0.2, 0.4, 0.01);
            }
        }

        windup++;
        if (windup >= WINDUP_DURATION) {
            unleash();
            windup = -1;
        }
    }

    private void unleash() {
        World world = warlord.getWorld();
        world.playSound(null, warlord.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT,
                SoundCategory.HOSTILE, 1.5f, 0.7f);

        List<LivingEntity> nearby = world.getEntitiesByClass(LivingEntity.class,
                warlord.getBoundingBox().expand(RANGE),
                e -> e != warlord && e.isAlive());

        DamageSource src = world.getDamageSources().mobAttack(warlord);
        for (LivingEntity e : nearby) {
            Vec3d toEntity = e.getPos().subtract(warlord.getPos());
            double dist = toEntity.length();
            if (dist < 0.01 || dist > RANGE) continue;
            double alignment = toEntity.normalize().dotProduct(direction);
            if (alignment < CONE_DOT_THRESHOLD) continue;

            e.damage(src, DAMAGE);
            e.setFireTicks(80); // 4s
            e.takeKnockback(0.7, warlord.getX() - e.getX(), warlord.getZ() - e.getZ());
        }

        if (world instanceof ServerWorld sw) {
            for (int i = 1; i <= 8; i++) {
                Vec3d p = warlord.getPos().add(direction.multiply(i)).add(0, 0.3, 0);
                sw.spawnParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 3, 0.5, 0.3, 0.5, 0.02);
            }
        }
    }

    /**
     * Debug trigger: locks the cone direction onto the target immediately
     * and unleashes it, skipping the telegraph.
     */
    public void debugForceWave(LivingEntity target) {
        direction = target.getPos().subtract(warlord.getPos()).normalize();
        unleash();
    }
}
