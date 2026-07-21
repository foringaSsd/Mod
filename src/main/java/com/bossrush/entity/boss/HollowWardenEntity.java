package com.bossrush.entity.boss;

import com.bossrush.entity.ai.goal.WardenMeleeSwipeGoal;
import com.bossrush.entity.ai.goal.WardenSonicPulseGoal;
import com.bossrush.entity.ai.goal.WardenVacuumBarrageGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Boss 2/5: a blind, vibration-sensing horror. It targets players without
 * needing line of sight (see targetSelector below), so hiding behind thin
 * cover doesn't save you. Phase 2 turns the arena into a vortex.
 *
 * Assumption: 140 hearts (280 HP) — not specified by the user, tune below.
 */
public class HollowWardenEntity extends AbstractBossEntity {

    private static final TrackedData<Integer> PHASE =
            DataTracker.registerData(HollowWardenEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> DEATH_TIMER =
            DataTracker.registerData(HollowWardenEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public static final float MAX_HEALTH = 280.0f; // 140 hearts (assumption)
    private static final int DEATH_SEQUENCE_TICKS = 100; // 5 seconds
    private static final double WAKE_RADIUS = 10.0;
    private static final float PHASE2_HEALTH_FRACTION = 0.50f;
    private static final float PHASE3_HEALTH_FRACTION = 0.15f;

    private final ServerBossBar bossBar =
            new ServerBossBar(Text.literal("The Hollow Warden"), BossBar.Color.BLUE, BossBar.Style.NOTCHED_10);

    private boolean finishing = false;

    public HollowWardenEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = 100;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 13.0)
                .add(EntityAttributes.GENERIC_ARMOR, 6.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(PHASE, HollowWardenPhase.DORMANT.ordinal());
        this.dataTracker.startTracking(DEATH_TIMER, 0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new WardenMeleeSwipeGoal(this, 1.0));
        this.goalSelector.add(2, new WardenSonicPulseGoal(this));
        this.goalSelector.add(2, new WardenVacuumBarrageGoal(this));

        this.goalSelector.add(6, new WanderAroundFarGoal(this, 0.6));
        this.goalSelector.add(8, new LookAroundGoal(this));

        // checkVisibility = false: it senses vibrations, not sight.
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
    }

    // ---------- Phase helpers ----------

    public HollowWardenPhase getPhase() {
        return HollowWardenPhase.values()[this.dataTracker.get(PHASE)];
    }

    private void setPhase(HollowWardenPhase phase) {
        this.dataTracker.set(PHASE, phase.ordinal());
    }

    public boolean isDormant() {
        return getPhase() == HollowWardenPhase.DORMANT;
    }

    public boolean isCollapsing() {
        return getPhase() == HollowWardenPhase.COLLAPSING;
    }

    // ---------- Damage / waking ----------

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (isDormant()) {
            wake();
            return false;
        }
        if (isCollapsing() && !finishing) {
            return false;
        }
        return super.damage(source, amount);
    }

    private void wake() {
        if (!isDormant()) return;
        setPhase(HollowWardenPhase.AWAKENED);
        this.setAiDisabled(false);
        this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_EMERGE,
                SoundCategory.HOSTILE, 2.0f, 0.8f);
        if (this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.SCULK_SOUL, this.getX(), this.getY() + 1.0, this.getZ(),
                    20, 1.0, 1.0, 1.0, 0.05);
        }
    }

    // ---------- Tick / phase transitions ----------

    @Override
    public void tick() {
        super.tick();
        World world = this.getWorld();

        if (isDormant()) {
            this.setAiDisabled(true);
            if (!world.isClient) {
                PlayerEntity nearest = world.getClosestPlayer(this, WAKE_RADIUS);
                if (nearest != null) wake();
            }
            return;
        }

        if (!world.isClient) {
            this.bossBar.setPercent(MathHelper.clamp(this.getHealth() / this.getMaxHealth(), 0.0f, 1.0f));

            if (getPhase() == HollowWardenPhase.AWAKENED
                    && this.getHealth() <= this.getMaxHealth() * PHASE2_HEALTH_FRACTION) {
                enterPhase2();
            } else if (getPhase() != HollowWardenPhase.COLLAPSING
                    && this.getHealth() <= this.getMaxHealth() * PHASE3_HEALTH_FRACTION) {
                enterCollapsingPhase();
            }

            if (isCollapsing()) {
                tickDeathSequence();
            }
        }
    }

    private void enterPhase2() {
        setPhase(HollowWardenPhase.CONSUMING);
        this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_ROAR,
                SoundCategory.HOSTILE, 1.8f, 0.7f);
    }

    private void enterCollapsingPhase() {
        setPhase(HollowWardenPhase.COLLAPSING);
        this.dataTracker.set(DEATH_TIMER, DEATH_SEQUENCE_TICKS);
        this.setAiDisabled(true);
        this.getNavigation().stop();
        this.setTarget(null);
        this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_DEATH,
                SoundCategory.HOSTILE, 1.8f, 0.6f);
    }

    private void tickDeathSequence() {
        int timer = this.dataTracker.get(DEATH_TIMER) - 1;
        this.dataTracker.set(DEATH_TIMER, Math.max(timer, 0));

        if (this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.SCULK_SOUL, this.getX(), this.getY() + 1.5, this.getZ(),
                    4, 0.6, 0.9, 0.6, 0.02);
            sw.spawnParticles(ParticleTypes.SCULK_CHARGE_POP, this.getX(), this.getY() + 0.5, this.getZ(),
                    2, 0.6, 0.3, 0.6, 0.0);
            if (timer % 20 == 0) {
                sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_SCULK_CATALYST_BLOOM,
                        SoundCategory.HOSTILE, 1.2f, 0.5f);
            }
        }

        if (timer <= 0) {
            finalExplosionAndDeath();
        }
    }

    private void finalExplosionAndDeath() {
        World world = this.getWorld();
        world.createExplosion(this, this.getX(), this.getY() + 1.0, this.getZ(),
                6.0f, false, World.ExplosionSourceType.NONE);
        finishing = true;
        this.kill();
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        this.bossBar.setVisible(false);
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

    // ---------- Persistence ----------

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("BossPhase", this.getPhase().ordinal());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("BossPhase")) {
            setPhase(HollowWardenPhase.values()[nbt.getInt("BossPhase")]);
        }
    }

    // ---------- Sounds ----------

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_WARDEN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_WARDEN_DEATH;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
