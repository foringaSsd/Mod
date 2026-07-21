package com.bossrush.entity.ai.goal;

import com.bossrush.entity.boss.EndFamiliarEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * Flight movement: keeps the familiar circling/strafing at range from its
 * target instead of closing to melee — it's meant to be shot at from a
 * distance, not tanked.
 */
public class FamiliarHoverGoal extends Goal {
    private final EndFamiliarEntity familiar;
    private static final double PREFERRED_DIST = 8.0;

    public FamiliarHoverGoal(EndFamiliarEntity familiar) {
        this.familiar = familiar;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        LivingEntity target = familiar.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean shouldContinue() {
        return canStart();
    }

    @Override
    public void tick() {
        LivingEntity target = familiar.getTarget();
        if (target == null) return;
        familiar.getLookControl().lookAt(target, 30.0f, 30.0f);

        Vec3d toTarget = target.getPos().subtract(familiar.getPos());
        double dist = toTarget.length();
        if (dist < 0.01) return;

        Vec3d desiredVelocity;
        if (dist > PREFERRED_DIST + 2) {
            desiredVelocity = toTarget.normalize().multiply(0.12);
        } else if (dist < PREFERRED_DIST - 2) {
            desiredVelocity = toTarget.normalize().multiply(-0.1);
        } else {
            Vec3d strafe = new Vec3d(-toTarget.z, 0, toTarget.x).normalize()
                    .multiply(familiar.getId() % 2 == 0 ? 0.08 : -0.08);
            desiredVelocity = strafe;
        }

        double targetY = target.getY() + 3.0;
        double dy = MathHelper.clamp((targetY - familiar.getY()) * 0.02, -0.08, 0.08);

        familiar.setVelocity(familiar.getVelocity().multiply(0.8).add(desiredVelocity.x, dy, desiredVelocity.z));
        familiar.velocityModified = true;
    }
}
