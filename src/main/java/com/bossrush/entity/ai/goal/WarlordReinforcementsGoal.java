package com.bossrush.entity.ai.goal;

import com.bossrush.entity.boss.BastionWarlordEntity;
import com.bossrush.entity.boss.BastionWarlordPhase;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PiglinBruteEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * Phase 2+ attack: calls in real Piglin Brutes to fight alongside it,
 * capped so it can't spam infinite adds. Unlike boss 4's decorative
 * shield anchors, these are full hostile mobs that actually fight.
 */
public class WarlordReinforcementsGoal extends Goal {
    private final BastionWarlordEntity warlord;
    private int cooldown = 200; // 10s before the first possible call
    private static final int MAX_ALLIES = 4;
    private static final int SUMMON_COUNT = 2;

    public WarlordReinforcementsGoal(BastionWarlordEntity warlord) {
        this.warlord = warlord;
    }

    @Override
    public boolean canStart() {
        if (cooldown > 0) cooldown--;
        if (warlord.getPhase() == BastionWarlordPhase.BASE_ATTACKS) return false;
        LivingEntity target = warlord.getTarget();
        return cooldown <= 0 && target != null && target.isAlive()
                && warlord.getAllyCount() < MAX_ALLIES;
    }

    @Override
    public boolean shouldContinue() {
        return false;
    }

    @Override
    public void start() {
        summon(warlord.getTarget());
        cooldown = warlord.isEnraged() ? 300 : 500; // 15s / 25s
    }

    private void summon(LivingEntity target) {
        if (!(warlord.getWorld() instanceof ServerWorld world)) return;

        for (int i = 0; i < SUMMON_COUNT; i++) {
            double angle = warlord.getRandom().nextDouble() * Math.PI * 2;
            double dist = 2.0 + warlord.getRandom().nextDouble() * 2.0;
            double x = warlord.getX() + Math.cos(angle) * dist;
            double z = warlord.getZ() + Math.sin(angle) * dist;

            PiglinBruteEntity brute = new PiglinBruteEntity(EntityType.PIGLIN_BRUTE, world);
            brute.refreshPositionAndAngles(x, warlord.getY(), z, 0f, 0f);
            world.spawnEntity(brute);
            if (target != null) brute.setTarget(target);
            warlord.registerAlly(brute.getUuid());
        }

        world.playSound(null, warlord.getBlockPos(), SoundEvents.ENTITY_PIGLIN_BRUTE_ANGRY,
                SoundCategory.HOSTILE, 2.0f, 0.6f);
    }

    /**
     * Debug trigger: summons reinforcements immediately, targeting the
     * given entity, ignoring cooldown and the ally cap.
     */
    public void debugForceSummon(LivingEntity target) {
        summon(target);
    }
}
