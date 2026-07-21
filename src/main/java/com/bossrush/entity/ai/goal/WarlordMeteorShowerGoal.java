package com.bossrush.entity.ai.goal;

import com.bossrush.entity.boss.BastionWarlordEntity;
import com.bossrush.entity.boss.BastionWarlordPhase;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 2+ attack: telegraphs 3 impact points near the target, then each
 * one detonates in a small fire AOE. If mobGriefing is on, it also
 * briefly sets the impact block to fire — always scheduled for auto
 * revert via BastionWarlordEntity#scheduleFireRevert, so nothing burns
 * permanently even on netherrack.
 */
public class WarlordMeteorShowerGoal extends Goal {
    private final BastionWarlordEntity warlord;
    private int cooldown = 160; // 8s before first possible use
    private static final int TELEGRAPH_DURATION = 25; // ~1.25s
    private static final int IMPACT_COUNT = 3;
    private static final double SCATTER_RADIUS = 6.0;
    private static final double DAMAGE_RADIUS = 2.5;
    private static final float DAMAGE = 9.0f;
    private static final int FIRE_LIFETIME_TICKS = 80; // 4s

    private final List<PendingImpact> pending = new ArrayList<>();

    public WarlordMeteorShowerGoal(BastionWarlordEntity warlord) {
        this.warlord = warlord;
    }

    @Override
    public boolean canStart() {
        if (cooldown > 0) cooldown--;
        if (warlord.getPhase() == BastionWarlordPhase.BASE_ATTACKS) return false;
        LivingEntity target = warlord.getTarget();
        return cooldown <= 0 && target != null && target.isAlive();
    }

    @Override
    public boolean shouldContinue() {
        return !pending.isEmpty();
    }

    @Override
    public void start() {
        LivingEntity target = warlord.getTarget();
        if (target == null) return;

        BlockPos center = target.getBlockPos();
        for (int i = 0; i < IMPACT_COUNT; i++) {
            double ox = (warlord.getRandom().nextDouble() - 0.5) * 2 * SCATTER_RADIUS;
            double oz = (warlord.getRandom().nextDouble() - 0.5) * 2 * SCATTER_RADIUS;
            BlockPos pos = center.add((int) ox, 0, (int) oz);
            pending.add(new PendingImpact(pos, TELEGRAPH_DURATION));
        }

        warlord.getWorld().playSound(null, warlord.getBlockPos(), SoundEvents.ENTITY_BLAZE_AMBIENT,
                SoundCategory.HOSTILE, 2.0f, 0.5f);
    }

    @Override
    public void stop() {
        cooldown = warlord.isEnraged() ? 250 : 400; // 12.5s / 20s
    }

    @Override
    public void tick() {
        World world = warlord.getWorld();
        var it = pending.iterator();
        while (it.hasNext()) {
            PendingImpact impact = it.next();

            if (world instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.LAVA,
                        impact.pos.getX() + 0.5, impact.pos.getY() + 0.3, impact.pos.getZ() + 0.5,
                        2, 0.3, 0.1, 0.3, 0.0);
                sw.spawnParticles(ParticleTypes.SMOKE,
                        impact.pos.getX() + 0.5, impact.pos.getY() + 0.5, impact.pos.getZ() + 0.5,
                        2, 0.3, 0.4, 0.3, 0.02);
            }

            impact.ticksLeft--;
            if (impact.ticksLeft <= 0) {
                detonate(impact.pos);
                it.remove();
            }
        }
    }

    private void detonate(BlockPos pos) {
        World world = warlord.getWorld();
        world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.2f, 1.1f);

        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
                    15, 0.6, 0.2, 0.6, 0.1);
        }

        List<LivingEntity> hit = world.getEntitiesByClass(LivingEntity.class,
                new net.minecraft.util.math.Box(pos).expand(DAMAGE_RADIUS),
                e -> e != warlord && e.isAlive());

        DamageSource src = world.getDamageSources().mobAttack(warlord);
        for (LivingEntity e : hit) {
            e.damage(src, DAMAGE);
            e.setFireTicks(60); // 3s
        }

        if (world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
            BlockPos groundPos = pos.up(0);
            if (world.getBlockState(groundPos).isAir() && !world.getBlockState(groundPos.down()).isAir()) {
                world.setBlockState(groundPos, Blocks.FIRE.getDefaultState());
                warlord.scheduleFireRevert(groundPos, FIRE_LIFETIME_TICKS);
            }
        }
    }

    private static class PendingImpact {
        final BlockPos pos;
        int ticksLeft;

        PendingImpact(BlockPos pos, int ticksLeft) {
            this.pos = pos;
            this.ticksLeft = ticksLeft;
        }
    }
}
