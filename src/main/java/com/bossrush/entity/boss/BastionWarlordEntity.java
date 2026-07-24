package com.bossrush.entity.boss;

import com.bossrush.entity.ai.goal.WarlordFlameWaveGoal;
import com.bossrush.entity.ai.goal.WarlordMeteorShowerGoal;
import com.bossrush.entity.ai.goal.WarlordMoltenSlamGoal;
import com.bossrush.entity.ai.goal.WarlordReinforcementsGoal;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Boss 5/5: a mobile piglin brute warlord guarding a Nether bastion.
 * No sleeping phase — aggressive immediately. Phase 2 unlocks real
 * Piglin Brute reinforcements and telegraphed lava-impact hazards;
 * phase 3 just tightens every cooldown for a final push.
 *
 * Assumption: 200 hearts (400 HP) — the toughest of the five as the
 * finale, not specified — tune below.
 */
public class BastionWarlordEntity extends AbstractBossEntity {

    private static final TrackedData<Integer> PHASE =
            DataTracker.registerData(BastionWarlordEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public static final float MAX_HEALTH = 400.0f; // 200 hearts (assumption)
    private static final float PHASE2_HEALTH_FRACTION = 0.55f;
    private static final float PHASE3_HEALTH_FRACTION = 0.20f;

    private final ServerBossBar bossBar =
            new ServerBossBar(Text.literal("The Bastion Warlord"), BossBar.Color.RED, BossBar.Style.NOTCHED_12);

    private final List<UUID> allyIds = new ArrayList<>();
    private final Map<BlockPos, Integer> pendingFireReverts = new HashMap<>();

    public BastionWarlordEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = 120;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 16.0)
                .add(EntityAttributes.GENERIC_ARMOR, 8.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.6)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(PHASE, BastionWarlordPhase.BASE_ATTACKS.ordinal());
    }

    @Override
    protected void initGoals() {
        // See TrueIronGolemEntity#initGoals for why special attacks need
        // a lower priority number than melee. Reinforcements and Meteor
        // Shower claim no Controls at all, so they were never actually
        // blocked by this — only Flame Wave (shares Control.LOOK with
        // molten slam) needed the reorder. Kept all three together at
        // priority 1 for consistency and because Reinforcements/Meteor
        // are Phase 2+ gated anyway, so they won't appear until then.
        WarlordFlameWaveGoal waveGoal = new WarlordFlameWaveGoal(this);
        this.goalSelector.add(1, waveGoal);
        WarlordReinforcementsGoal reinforcementsGoal = new WarlordReinforcementsGoal(this);
        this.goalSelector.add(1, reinforcementsGoal);
        WarlordMeteorShowerGoal meteorGoal = new WarlordMeteorShowerGoal(this);
        this.goalSelector.add(1, meteorGoal);
        WarlordMoltenSlamGoal slamGoal = new WarlordMoltenSlamGoal(this, 1.05);
        this.goalSelector.add(2, slamGoal);

        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));

        registerDebugAttack("slam", "Molten Slam", slamGoal::debugForceSlam);
        registerDebugAttack("flamewave", "Flame Wave", waveGoal::debugForceWave);
        registerDebugAttack("reinforcements", "Reinforcements", reinforcementsGoal::debugForceSummon);
        registerDebugAttack("meteor", "Meteor Shower", meteorGoal::debugForceMeteor);
    }

    // ---------- Phase helpers ----------

    public BastionWarlordPhase getPhase() {
        return BastionWarlordPhase.values()[this.dataTracker.get(PHASE)];
    }

    private void setPhase(BastionWarlordPhase phase) {
        this.dataTracker.set(PHASE, phase.ordinal());
    }

    public boolean isEnraged() {
        return getPhase() == BastionWarlordPhase.ENRAGED;
    }

    // ---------- Ally tracking ----------

    public void registerAlly(UUID id) {
        allyIds.add(id);
    }

    public int getAllyCount() {
        if (this.getWorld() instanceof ServerWorld sw) {
            allyIds.removeIf(id -> {
                Entity e = sw.getEntity(id);
                return e == null || !e.isAlive();
            });
        }
        return allyIds.size();
    }

    // ---------- Fire hazard bookkeeping ----------

    public void scheduleFireRevert(BlockPos pos, int delayTicks) {
        pendingFireReverts.put(pos.toImmutable(), delayTicks);
    }

    private void tickFireReverts() {
        if (!(this.getWorld() instanceof ServerWorld world)) return;
        Iterator<Map.Entry<BlockPos, Integer>> it = pendingFireReverts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                if (world.getBlockState(entry.getKey()).isOf(Blocks.FIRE)) {
                    world.setBlockState(entry.getKey(), Blocks.AIR.getDefaultState());
                }
                it.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    // ---------- Tick / phase transitions ----------

    @Override
    public void tick() {
        super.tick();
        World world = this.getWorld();

        if (!world.isClient) {
            this.bossBar.setPercent(MathHelper.clamp(this.getHealth() / this.getMaxHealth(), 0.0f, 1.0f));
            tickFireReverts();

            if (getPhase() == BastionWarlordPhase.BASE_ATTACKS
                    && this.getHealth() <= this.getMaxHealth() * PHASE2_HEALTH_FRACTION) {
                enterPhase2();
            } else if (getPhase() == BastionWarlordPhase.EMPOWERED
                    && this.getHealth() <= this.getMaxHealth() * PHASE3_HEALTH_FRACTION) {
                enterEnraged();
            }
        }
    }

    private void enterPhase2() {
        setPhase(BastionWarlordPhase.EMPOWERED);
        this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PIGLIN_BRUTE_ANGRY,
                SoundCategory.HOSTILE, 2.0f, 0.6f);
    }

    private void enterEnraged() {
        setPhase(BastionWarlordPhase.ENRAGED);
        this.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 60 * 10, 0));
        this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_BLAZE_AMBIENT,
                SoundCategory.HOSTILE, 2.0f, 0.5f);
        if (this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 1.0, this.getZ(),
                    30, 0.8, 1.0, 0.8, 0.05);
        }
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        this.bossBar.setVisible(false);
        if (this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.LAVA, this.getX(), this.getY() + 1.0, this.getZ(),
                    30, 0.8, 1.0, 0.8, 0.1);
            sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE,
                    SoundCategory.HOSTILE, 2.0f, 0.6f);
        }
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        this.bossBar.removePlayer(player);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_PIGLIN_BRUTE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_PIGLIN_BRUTE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_PIGLIN_BRUTE_DEATH;
    }
}
