package com.bossrush.entity.boss;

import com.bossrush.entity.ai.goal.FamiliarDodgeGoal;
import com.bossrush.entity.ai.goal.FamiliarHoverGoal;
import com.bossrush.entity.ai.goal.FamiliarSupportGoal;
import com.bossrush.entity.ai.goal.FamiliarVoidBoltGoal;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

/**
 * Boss 3/5: a fragile flying familiar that assists the Ender Dragon.
 * 15 base HP as specified. Dodges incoming projectiles, regenerates over
 * time, and slowly grows its own max health the longer it survives — the
 * fight has real urgency to end it quickly, but its death explosion
 * makes carelessly meleeing it dangerous too.
 */
public class EndFamiliarEntity extends AbstractBossEntity {

    public static final float BASE_MAX_HEALTH = 15.0f; // as specified by the user
    private static final float REGEN_PER_SECOND = 0.5f; // "moderate" — assumption, tune freely
    private static final int GROWTH_MIN_TICKS = 600;  // 30s
    private static final int GROWTH_MAX_TICKS = 1200; // 60s
    private static final float GROWTH_AMOUNT = 1.0f;  // +1 HP (engine unit, i.e. half a heart) per tick-up
    private static final int SHULKERS_ON_DEATH = 3;
    private static final float DEATH_EXPLOSION_POWER = 4.0f; // TNT-like radius

    private int regenTickCounter;
    private int nextGrowthTicks;

    public EndFamiliarEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.setNoGravity(true);
        this.experiencePoints = 20;
        this.nextGrowthTicks = GROWTH_MIN_TICKS + this.random.nextInt(GROWTH_MAX_TICKS - GROWTH_MIN_TICKS);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, BASE_MAX_HEALTH)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.6)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new FamiliarDodgeGoal(this));
        this.goalSelector.add(1, new FamiliarVoidBoltGoal(this));
        this.goalSelector.add(2, new FamiliarHoverGoal(this));
        this.goalSelector.add(3, new FamiliarSupportGoal(this));
        this.goalSelector.add(6, new LookAroundGoal(this));

        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient || !this.isAlive()) return;

        tickRegeneration();
        tickGrowth();
    }

    private void tickRegeneration() {
        regenTickCounter++;
        if (regenTickCounter >= 20) { // once per second
            regenTickCounter = 0;
            if (this.getHealth() > 0 && this.getHealth() < this.getMaxHealth()) {
                this.heal(REGEN_PER_SECOND);
            }
        }
    }

    private void tickGrowth() {
        nextGrowthTicks--;
        if (nextGrowthTicks <= 0) {
            growMaxHealth();
            nextGrowthTicks = GROWTH_MIN_TICKS + this.random.nextInt(GROWTH_MAX_TICKS - GROWTH_MIN_TICKS);
        }
    }

    private void growMaxHealth() {
        EntityAttributeInstance attr = this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (attr == null) return;
        attr.setBaseValue(attr.getBaseValue() + GROWTH_AMOUNT);
        this.heal(GROWTH_AMOUNT);

        if (this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.5, this.getZ(),
                    6, 0.3, 0.3, 0.3, 0.02);
        }
    }

    // ---------- Death: shulkers + obsidian-piercing explosion ----------

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        if (this.getWorld() instanceof ServerWorld world) {
            spawnShulkers(world);
            deathExplosion(world);
        }
    }

    private void spawnShulkers(ServerWorld world) {
        for (int i = 0; i < SHULKERS_ON_DEATH; i++) {
            double ox = (this.random.nextDouble() - 0.5) * 4.0;
            double oz = (this.random.nextDouble() - 0.5) * 4.0;
            ShulkerEntity shulker = new ShulkerEntity(EntityType.SHULKER, world);
            shulker.refreshPositionAndAngles(this.getX() + ox, this.getY(), this.getZ() + oz, 0f, 0f);
            world.spawnEntity(shulker);
        }
        world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_SHULKER_TELEPORT,
                SoundCategory.HOSTILE, 1.5f, 1.0f);
    }

    private void deathExplosion(ServerWorld world) {
        // Real TNT-style explosion: normal power/radius, normal block destruction.
        world.createExplosion(this, this.getX(), this.getY(), this.getZ(),
                DEATH_EXPLOSION_POWER, false, World.ExplosionSourceType.TNT);

        // Obsidian survives every vanilla explosion (blast resistance 1200) —
        // this boss's blast is special, so clear any obsidian in range too.
        if (world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
            BlockPos center = this.getBlockPos();
            int r = (int) Math.ceil(DEATH_EXPLOSION_POWER);
            for (BlockPos pos : BlockPos.iterate(center.add(-r, -r, -r), center.add(r, r, r))) {
                if (pos.isWithinDistance(center, DEATH_EXPLOSION_POWER)
                        && world.getBlockState(pos).isOf(Blocks.OBSIDIAN)) {
                    world.removeBlock(pos, false);
                }
            }
        }
    }

    // ---------- Sounds ----------

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_ENDERMAN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_ENDERMAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_ENDERMAN_DEATH;
    }
}
