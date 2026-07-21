package com.bossrush.entity.boss;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Common base for all Boss Rush bosses. Adds nothing to the fight itself —
 * its only job is the debug-attack registry used by
 * /bossrush debug attack <boss> <attack> <target>. Subclasses call
 * registerDebugAttack(...) in initGoals(), typically passing a method
 * reference to a public "debugForce___" method on the relevant Goal
 * instance (see GolemRangedBarrageGoal#debugForceFire for the pattern).
 */
public abstract class AbstractBossEntity extends HostileEntity {

    private final Map<String, DebugAttack> debugAttacks = new LinkedHashMap<>();

    protected AbstractBossEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
    }

    protected void registerDebugAttack(String id, String displayName, Consumer<LivingEntity> trigger) {
        debugAttacks.put(id, new DebugAttack(displayName, trigger));
    }

    public Map<String, DebugAttack> getDebugAttacks() {
        return debugAttacks;
    }

    public record DebugAttack(String displayName, Consumer<LivingEntity> trigger) {
    }
}
