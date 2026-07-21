package com.bossrush.entity.ai.goal;

import com.bossrush.entity.boss.ShulkerSovereignEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * Every 15 seconds, spawns 3 armor-stand "Shield Anchors" around the
 * boss. While any are alive, the boss is fully invulnerable (see
 * ShulkerSovereignEntity#damage) — an ULTRAKILL-Earthmover-style
 * "destroy the shield generators first" loop. Runs independently of
 * SovereignVoidBoltGoal, so both can be active at once.
 */
public class ShieldSummonGoal extends Goal {
    private final ShulkerSovereignEntity boss;
    private int timer = 40; // first summon 2s after spawn
    private static final int INTERVAL = 300; // 15s
    private static final int MAX_ANCHORS = 9;
    private static final double ANCHOR_RADIUS = 3.5;

    public ShieldSummonGoal(ShulkerSovereignEntity boss) {
        this.boss = boss;
    }

    @Override
    public boolean canStart() {
        timer--;
        return timer <= 0;
    }

    @Override
    public boolean shouldContinue() {
        return false; // instantaneous burst, re-triggered via canStart on its own timer
    }

    @Override
    public void start() {
        summon();
        timer = INTERVAL;
    }

    private void summon() {
        if (!(boss.getWorld() instanceof ServerWorld world)) return;
        if (boss.getShieldAnchorCount() >= MAX_ANCHORS) return;

        double baseAngle = boss.getRandom().nextDouble() * 360;
        for (int i = 0; i < 3; i++) {
            double rad = Math.toRadians(baseAngle + i * 120);
            double x = boss.getX() + Math.cos(rad) * ANCHOR_RADIUS;
            double z = boss.getZ() + Math.sin(rad) * ANCHOR_RADIUS;

            ArmorStandEntity anchor = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
            anchor.refreshPositionAndAngles(x, boss.getY(), z, 0f, 0f);
            anchor.setInvisible(false);
            anchor.setGlowing(true);
            anchor.setShowArms(false);
            anchor.setNoBasePlate(true);
            anchor.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.SHULKER_SHELL));
            anchor.setCustomName(Text.literal("Shield Anchor"));
            anchor.setCustomNameVisible(true);

            world.spawnEntity(anchor);
            boss.registerShieldAnchor(anchor.getUuid());
        }

        world.playSound(null, boss.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE,
                SoundCategory.HOSTILE, 1.5f, 0.8f);
        world.playSound(null, boss.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_FRAME_FILL,
                SoundCategory.HOSTILE, 1.2f, 1.0f);
        world.spawnParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + 1.0, boss.getZ(),
                30, 1.5, 1.0, 1.5, 0.05);
    }
}
