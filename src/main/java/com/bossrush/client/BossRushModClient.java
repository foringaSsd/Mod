package com.bossrush.client;

import com.bossrush.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class BossRushModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.TRUE_IRON_GOLEM, TrueIronGolemRenderer::new);
        EntityRendererRegistry.register(ModEntities.HOLLOW_WARDEN, HollowWardenRenderer::new);
        EntityRendererRegistry.register(ModEntities.END_FAMILIAR, EndFamiliarRenderer::new);
        EntityRendererRegistry.register(ModEntities.SHULKER_SOVEREIGN, ShulkerSovereignRenderer::new);
        EntityRendererRegistry.register(ModEntities.BASTION_WARLORD, BastionWarlordRenderer::new);
    }
}
