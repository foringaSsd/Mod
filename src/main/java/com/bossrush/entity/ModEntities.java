package com.bossrush.entity;

import com.bossrush.BossRushMod;
import com.bossrush.entity.boss.BastionWarlordEntity;
import com.bossrush.entity.boss.EndFamiliarEntity;
import com.bossrush.entity.boss.HollowWardenEntity;
import com.bossrush.entity.boss.ShulkerSovereignEntity;
import com.bossrush.entity.boss.TrueIronGolemEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Holds all boss entity type registrations. Add each new boss here the
 * same way the existing ones are registered.
 */
public class ModEntities {
    public static EntityType<TrueIronGolemEntity> TRUE_IRON_GOLEM;
    public static EntityType<HollowWardenEntity> HOLLOW_WARDEN;
    public static EntityType<EndFamiliarEntity> END_FAMILIAR;
    public static EntityType<ShulkerSovereignEntity> SHULKER_SOVEREIGN;
    public static EntityType<BastionWarlordEntity> BASTION_WARLORD;

    public static void register() {
        TRUE_IRON_GOLEM = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier(BossRushMod.MOD_ID, "true_iron_golem"),
                FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, TrueIronGolemEntity::new)
                        .dimensions(EntityDimensions.fixed(1.8f, 3.2f))
                        .trackRangeBlocks(80)
                        .build()
        );

        HOLLOW_WARDEN = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier(BossRushMod.MOD_ID, "hollow_warden"),
                FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, HollowWardenEntity::new)
                        .dimensions(EntityDimensions.fixed(1.6f, 3.2f))
                        .trackRangeBlocks(80)
                        .build()
        );

        END_FAMILIAR = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier(BossRushMod.MOD_ID, "end_familiar"),
                FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, EndFamiliarEntity::new)
                        .dimensions(EntityDimensions.fixed(0.8f, 0.9f))
                        .trackRangeBlocks(64)
                        .build()
        );

        SHULKER_SOVEREIGN = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier(BossRushMod.MOD_ID, "shulker_sovereign"),
                FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, ShulkerSovereignEntity::new)
                        .dimensions(EntityDimensions.fixed(2.0f, 2.5f))
                        .trackRangeBlocks(80)
                        .build()
        );

        BASTION_WARLORD = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier(BossRushMod.MOD_ID, "bastion_warlord"),
                FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, BastionWarlordEntity::new)
                        .dimensions(EntityDimensions.fixed(1.4f, 2.5f))
                        .trackRangeBlocks(80)
                        .build()
        );
    }
}
