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

    // NOT initialized here with "= new LinkedHashMap<>()" — vanilla's
    // MobEntity constructor calls initGoals() internally, which happens
    // BEFORE any subclass instance field initializers run (Java always
    // finishes the full super() call chain first). Since initGoals()
    // calls registerDebugAttack(), a field initializer here would still
    // be null at that point, causing a NullPointerException during
    // entity construction. Lazy-initializing on first access sidesteps
    // the ordering problem entirely.
    private Map<String, DebugAttack> debugAttacks;

    protected AbstractBossEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
    }

    protected void registerDebugAttack(String id, String displayName, Consumer<LivingEntity> trigger) {
        if (debugAttacks == null) debugAttacks = new LinkedHashMap<>();
        debugAttacks.put(id, new DebugAttack(displayName, trigger));
    }

    public Map<String, DebugAttack> getDebugAttacks() {
        if (debugAttacks == null) debugAttacks = new LinkedHashMap<>();
        return debugAttacks;
    }

    public record DebugAttack(String displayName, Consumer<LivingEntity> trigger) {
    }
}
