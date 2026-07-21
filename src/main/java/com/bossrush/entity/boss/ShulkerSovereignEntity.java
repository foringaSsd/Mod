package com.bossrush.entity.boss;

import com.bossrush.entity.ai.goal.ShieldSummonGoal;
import com.bossrush.entity.ai.goal.SovereignVoidBoltGoal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Boss 4/5: a stationary guardian for the End City ship. Every 15s it
 * summons Shield Anchor armor stands that make it fully invulnerable
 * until destroyed (ULTRAKILL Earthmover "Brain" style), while a slow,
 * devastating telegraphed bolt keeps firing independently of shield
 * state.
 *
 * Assumption: 130 hearts (260 HP) — not specified, tune below.
 */
public class ShulkerSovereignEntity extends AbstractBossEntity {

    public static final float MAX_HEALTH = 260.0f;
    private static final double AGGRO_RADIUS = 20.0;

    private final ServerBossBar bossBar =
            new ServerBossBar(Text.literal("The Shulker Sovereign"), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_10);

    private final List<UUID> shieldAnchorIds = new ArrayList<>();

    public ShulkerSovereignEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = 100;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10.0)
                .add(EntityAttributes.GENERIC_ARMOR, 10.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SovereignVoidBoltGoal(this));
        this.goalSelector.add(1, new ShieldSummonGoal(this));
        this.goalSelector.add(5, new LookAtEntityGoal(this, PlayerEntity.class, 16.0f));

        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    // ---------- Shield anchor tracking ----------

    public void registerShieldAnchor(UUID id) {
        shieldAnchorIds.add(id);
    }

    public int getShieldAnchorCount() {
        pruneShieldAnchors();
        return shieldAnchorIds.size();
    }

    public boolean isShielded() {
        pruneShieldAnchors();
        return !shieldAnchorIds.isEmpty();
    }

    private void pruneShieldAnchors() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        shieldAnchorIds.removeIf(id -> {
            Entity e = sw.getEntity(id);
            return !(e instanceof ArmorStandEntity) || !e.isAlive();
        });
    }

    // ---------- Damage ----------

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (isShielded()) {
            if (this.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.2, this.getZ(),
                        6, 0.5, 0.5, 0.5, 0.1);
            }
            this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND,
                    SoundCategory.HOSTILE, 0.6f, 1.7f);
            return false;
        }
        return super.damage(source, amount);
    }

    // ---------- Tick ----------

    @Override
    public void tick() {
        super.tick();
        this.setVelocity(0, this.getVelocity().y, 0); // stay planted — it's a stationary guardian

        World world = this.getWorld();
        if (!world.isClient) {
            this.bossBar.setPercent(MathHelper.clamp(this.getHealth() / this.getMaxHealth(), 0.0f, 1.0f));

            if (this.getTarget() == null) {
                PlayerEntity nearest = world.getClosestPlayer(this, AGGRO_RADIUS);
                if (nearest != null) this.setTarget(nearest);
            }

            if (isShielded() && world instanceof ServerWorld sw && this.age % 4 == 0) {
                sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.0, this.getZ(),
                        1, 0.6, 0.8, 0.6, 0.01);
            }
        }
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        this.bossBar.setVisible(false);
        if (this.getWorld() instanceof ServerWorld sw) {
            pruneShieldAnchors();
            for (UUID id : new ArrayList<>(shieldAnchorIds)) {
                Entity e = sw.getEntity(id);
                if (e != null) e.discard();
            }
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
        return SoundEvents.ENTITY_SHULKER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_SHULKER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_SHULKER_DEATH;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
