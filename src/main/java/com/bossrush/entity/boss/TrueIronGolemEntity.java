package com.bossrush.entity.boss;

import com.bossrush.entity.ai.goal.GolemChargeSmashGoal;
import com.bossrush.entity.ai.goal.GolemGroundSlamGoal;
import com.bossrush.entity.ai.goal.GolemRangedBarrageGoal;
import com.bossrush.entity.ai.goal.GolemReachMeleeGoal;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class TrueIronGolemEntity extends AbstractBossEntity {

    private static final TrackedData<Integer> PHASE =
            DataTracker.registerData(TrueIronGolemEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> ARENA_EXPANDED =
            DataTracker.registerData(TrueIronGolemEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> DEATH_TIMER =
            DataTracker.registerData(TrueIronGolemEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public static final float MAX_HEALTH = 320.0f; // 160 hearts
    private static final int DEATH_SEQUENCE_TICKS = 100; // 5 seconds
    private static final double WAKE_RADIUS = 8.0;
    // Assumption: phase thresholds — tune these two fractions to taste.
    private static final float PHASE2_HEALTH_FRACTION = 0.60f;
    private static final float PHASE3_HEALTH_FRACTION = 0.15f;

    private final ServerBossBar bossBar =
            new ServerBossBar(Text.literal("The True Iron Golem"), BossBar.Color.RED, BossBar.Style.NOTCHED_10);

    private LivingEntity lastAttacker;
    private BlockPos homePos;
    private boolean finishing = false;
    private final int arenaExpandRadius = 10;

    public TrueIronGolemEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.setStepHeight(1.0f);
        this.experiencePoints = 100;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 14.0)
                .add(EntityAttributes.GENERIC_ARMOR, 12.0)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 8.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(PHASE, TrueIronGolemPhase.SLEEPING.ordinal());
        this.dataTracker.startTracking(ARENA_EXPANDED, false);
        this.dataTracker.startTracking(DEATH_TIMER, 0);
    }

    @Override
    protected void initGoals() {
        // Special attacks are given a LOWER priority number (= higher
        // importance) than melee on purpose: melee's shouldContinue()
        // keeps it running for as long as a target is alive (needed for
        // chasing), so it never voluntarily yields control. Minecraft's
        // goal system only lets a new goal interrupt an already-running
        // one if the new goal's priority number is strictly lower — so
        // special attacks need to outrank melee to ever get a turn.
        // Melee becomes the priority-2 "default" that fills the gaps
        // between special-attack cooldowns.
        this.goalSelector.add(1, new GolemGroundSlamGoal(this));                  // #2 AOE, no block damage
        GolemRangedBarrageGoal rangedGoal = new GolemRangedBarrageGoal(this);
        this.goalSelector.add(1, rangedGoal);                                     // #3 3 shulker + 3 arrows
        this.goalSelector.add(1, new GolemChargeSmashGoal(this));                 // Phase 2: charging shoulder smash
        this.goalSelector.add(2, new GolemReachMeleeGoal(this, 1.0, 4.5, 6.0));   // #1 melee + pillar reach (fallback)

        this.goalSelector.add(6, new WanderAroundFarGoal(this, 0.6));
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));

        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));

        registerDebugAttack("projectile", "Projectile Attack", rangedGoal::debugForceFire);
    }

    // ---------- Phase helpers ----------

    public TrueIronGolemPhase getPhase() {
        return TrueIronGolemPhase.values()[this.dataTracker.get(PHASE)];
    }

    private void setPhase(TrueIronGolemPhase phase) {
        this.dataTracker.set(PHASE, phase.ordinal());
    }

    public boolean isSleeping() {
        return getPhase() == TrueIronGolemPhase.SLEEPING;
    }

    public boolean isDying() {
        return getPhase() == TrueIronGolemPhase.DYING;
    }

    public LivingEntity getLastAttacker() {
        return lastAttacker;
    }

    public void setHomePos(BlockPos pos) {
        this.homePos = pos;
    }

    // ---------- Damage / waking ----------

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (isSleeping()) {
            wake();
            return false; // the hit that wakes it doesn't also hurt it
        }
        if (isDying() && !finishing) {
            return false; // invulnerable during the scripted death sequence
        }
        boolean hurt = super.damage(source, amount);
        if (hurt && source.getAttacker() instanceof LivingEntity attacker) {
            this.lastAttacker = attacker;
        }
        return hurt;
    }

    private void wake() {
        if (!isSleeping()) return;
        setPhase(TrueIronGolemPhase.BASE_ATTACKS);
        this.setAiDisabled(false);
        this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK,
                SoundCategory.HOSTILE, 2.0f, 0.6f);
        if (this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 1.5, this.getZ(),
                    6, 0.6, 0.6, 0.6, 0.0);
        }
    }

    // ---------- Tick / phase transitions ----------

    @Override
    public void tick() {
        super.tick();
        World world = this.getWorld();

        if (isSleeping()) {
            this.setAiDisabled(true);
            if (!world.isClient) {
                PlayerEntity nearest = world.getClosestPlayer(this, WAKE_RADIUS);
                if (nearest != null) wake();
            }
            return;
        }

        if (!world.isClient) {
            this.bossBar.setPercent(MathHelper.clamp(this.getHealth() / this.getMaxHealth(), 0.0f, 1.0f));

            if (getPhase() == TrueIronGolemPhase.BASE_ATTACKS
                    && this.getHealth() <= this.getMaxHealth() * PHASE2_HEALTH_FRACTION) {
                enterPhase2();
            } else if (getPhase() != TrueIronGolemPhase.DYING
                    && this.getHealth() <= this.getMaxHealth() * PHASE3_HEALTH_FRACTION) {
                enterDyingPhase();
            }

            if (isDying()) {
                tickDeathSequence();
            }
        }
    }

    private void enterPhase2() {
        setPhase(TrueIronGolemPhase.ARENA_CHANGE);
        if (!this.dataTracker.get(ARENA_EXPANDED)) {
            expandArena();
            this.dataTracker.set(ARENA_EXPANDED, true);
        }
        this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN,
                SoundCategory.HOSTILE, 1.5f, 0.7f);
    }

    private void enterDyingPhase() {
        setPhase(TrueIronGolemPhase.DYING);
        this.dataTracker.set(DEATH_TIMER, DEATH_SEQUENCE_TICKS);
        this.setAiDisabled(true);
        this.getNavigation().stop();
        this.setTarget(null);
        this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_DEATH,
                SoundCategory.HOSTILE, 1.5f, 0.6f);
    }

    private void tickDeathSequence() {
        int timer = this.dataTracker.get(DEATH_TIMER) - 1;
        this.dataTracker.set(DEATH_TIMER, Math.max(timer, 0));

        if (this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.5, this.getZ(),
                    3, 0.5, 0.8, 0.5, 0.02);
            sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.0, this.getZ(),
                    2, 0.4, 1.0, 0.4, 0.01);
            if (timer % 20 == 0) {
                sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BEACON_AMBIENT,
                        SoundCategory.HOSTILE, 1.0f, 0.5f);
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
        this.kill(); // routes through normal death handling: loot, onDeath, stats
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

    // ---------- Arena expansion (Phase 2) ----------
    // NOTE: this procedurally reshapes terrain around the golem rather than
    // pasting a second NBT structure, so it works with zero extra assets.
    // If you build a real "expanded arena" structure with structure blocks,
    // swap this method to paste it instead (see README).
    private void expandArena() {
        if (!(this.getWorld() instanceof ServerWorld world)) return;
        BlockPos center = homePos != null ? homePos : this.getBlockPos();
        int innerRadius = 6;
        int outerRadius = arenaExpandRadius;

        for (int x = -outerRadius; x <= outerRadius; x++) {
            for (int z = -outerRadius; z <= outerRadius; z++) {
                double dist = Math.sqrt((double) (x * x + z * z));
                if (dist > innerRadius && dist <= outerRadius) {
                    world.setBlockState(center.add(x, -1, z), Blocks.IRON_BLOCK.getDefaultState());
                    world.setBlockState(center.add(x, 0, z), Blocks.AIR.getDefaultState());
                    world.setBlockState(center.add(x, 1, z), Blocks.AIR.getDefaultState());
                    world.setBlockState(center.add(x, 2, z), Blocks.AIR.getDefaultState());
                }
            }
        }

        world.playSound(null, center, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 2.0f, 0.5f);
        for (PlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(this) < 40 * 40) {
                p.sendMessage(Text.literal("The arena shudders and expands!"), true);
            }
        }
    }

    // ---------- Persistence ----------

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("BossPhase", this.getPhase().ordinal());
        nbt.putBoolean("ArenaExpanded", this.dataTracker.get(ARENA_EXPANDED));
        if (homePos != null) {
            nbt.putInt("HomeX", homePos.getX());
            nbt.putInt("HomeY", homePos.getY());
            nbt.putInt("HomeZ", homePos.getZ());
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("BossPhase")) {
            setPhase(TrueIronGolemPhase.values()[nbt.getInt("BossPhase")]);
        }
        if (nbt.contains("ArenaExpanded")) {
            this.dataTracker.set(ARENA_EXPANDED, nbt.getBoolean("ArenaExpanded"));
        }
        if (nbt.contains("HomeX")) {
            homePos = new BlockPos(nbt.getInt("HomeX"), nbt.getInt("HomeY"), nbt.getInt("HomeZ"));
        }
    }

    // ---------- Sounds ----------

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_IRON_GOLEM_STEP;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_IRON_GOLEM_DEATH;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
