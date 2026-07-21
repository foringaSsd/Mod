package com.bossrush;

import com.bossrush.command.BossRushCommand;
import com.bossrush.entity.ModEntities;
import com.bossrush.entity.boss.BastionWarlordEntity;
import com.bossrush.entity.boss.EndFamiliarEntity;
import com.bossrush.entity.boss.HollowWardenEntity;
import com.bossrush.entity.boss.ShulkerSovereignEntity;
import com.bossrush.entity.boss.TrueIronGolemEntity;
import com.bossrush.entity.util.EndFamiliarSpawner;
import com.bossrush.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BossRushMod implements ModInitializer {
    public static final String MOD_ID = "bossrush";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModEntities.register();
        ModItems.register();

        FabricDefaultAttributeRegistry.register(
                ModEntities.TRUE_IRON_GOLEM,
                TrueIronGolemEntity.createAttributes()
        );
        FabricDefaultAttributeRegistry.register(
                ModEntities.HOLLOW_WARDEN,
                HollowWardenEntity.createAttributes()
        );
        FabricDefaultAttributeRegistry.register(
                ModEntities.END_FAMILIAR,
                EndFamiliarEntity.createAttributes()
        );
        FabricDefaultAttributeRegistry.register(
                ModEntities.SHULKER_SOVEREIGN,
                ShulkerSovereignEntity.createAttributes()
        );
        FabricDefaultAttributeRegistry.register(
                ModEntities.BASTION_WARLORD,
                BastionWarlordEntity.createAttributes()
        );

        EndFamiliarSpawner.register();
        BossRushCommand.register();

        LOGGER.info("Boss Rush initialized (boss 5/5: The Bastion Warlord — roster complete)");
    }
}
