package com.bossrush.entity.ai.goal;

import com.bossrush.entity.boss.EndFamiliarEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.projectile.ProjectileEntity;
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
 * Highest-priority goal: scans for incoming projectiles on a trajectory
 * that would hit the familiar and short-blinks it perpendicular to
 * dodge. Uses a teleport rather than a physics-based sidestep so it's
 * reliable even against fast projectiles.
 */
public class FamiliarDodgeGoal extends Goal {
    private final EndFamiliarEntity familiar;
    private int dodgeCooldown;
    private static final double SCAN_RADIUS = 14.0;
    private static final double MISS_THRESHOLD = 2.0;

    public FamiliarDodgeGoal(EndFamiliarEntity familiar) {
        this.familiar = familiar;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (dodgeCooldown > 0) {
            dodgeCooldown--;
            return false;
        }
        return findIncomingProjectile() != null;
    }

    @Override
    public boolean shouldContinue() {
        return false; // one-shot: the dodge itself happens in start()
    }

    @Override
    public void start() {
        ProjectileEntity threat = findIncomingProjectile();
        if (threat != null) {
            performDodge(threat);
        }
        dodgeCooldown = 10; // small buffer so it doesn't jitter every tick
    }

    private ProjectileEntity findIncomingProjectile() {
        World world = familiar.getWorld();
        Box searchBox = familiar.getBoundingBox().expand(SCAN_RADIUS);
        List<ProjectileEntity> projectiles = world.getEntitiesByClass(ProjectileEntity.class, searchBox, p -> true);
        for (ProjectileEntity p : projectiles) {
            if (isThreatening(p)) return p;
        }
        return null;
    }

    private boolean isThreatening(ProjectileEntity p) {
        Vec3d vel = p.getVelocity();
        if (vel.lengthSquared() < 0.01) return false;

        Vec3d toFamiliar = familiar.getPos().subtract(p.getPos());
        double dist = toFamiliar.length();
        if (dist > SCAN_RADIUS || dist < 0.01) return false;

        Vec3d velDir = vel.normalize();
        double along = toFamiliar.dotProduct(velDir);
        if (along <= 0 || along > SCAN_RADIUS) return false; // heading away or too far ahead

        Vec3d closestPoint = p.getPos().add(velDir.multiply(along));
        double missDist = familiar.getPos().distanceTo(closestPoint);
        return missDist < MISS_THRESHOLD;
    }

    private void performDodge(ProjectileEntity threat) {
        Vec3d vel = threat.getVelocity().normalize();
        Vec3d perpendicular = new Vec3d(-vel.z, 0, vel.x).normalize();
        if (familiar.getRandom().nextBoolean()) perpendicular = perpendicular.multiply(-1);

        double dodgeDistance = 4.0 + familiar.getRandom().nextDouble() * 2.0;
        double verticalJitter = (familiar.getRandom().nextDouble() - 0.5) * 3.0;

        Vec3d destination = familiar.getPos()
                .add(perpendicular.multiply(dodgeDistance))
                .add(0, verticalJitter, 0);

        if (!isSafeDestination(destination)) return;

        World world = familiar.getWorld();
        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.PORTAL, familiar.getX(), familiar.getY() + 1, familiar.getZ(),
                    20, 0.3, 0.5, 0.3, 0.05);
        }

        familiar.setPosition(destination.x, destination.y, destination.z);
        world.playSound(null, familiar.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.HOSTILE, 1.0f, 1.2f);

        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.PORTAL, destination.x, destination.y + 1, destination.z,
                    20, 0.3, 0.5, 0.3, 0.05);
        }
    }

    private boolean isSafeDestination(Vec3d pos) {
        Box destinationBox = familiar.getBoundingBox().offset(pos.subtract(familiar.getPos()));
        return familiar.getWorld().isSpaceEmpty(familiar, destinationBox);
    }
}
