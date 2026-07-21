package com.bossrush.client;

import com.bossrush.entity.boss.BastionWarlordEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

/**
 * Placeholder renderer using the standard piglin biped layer/texture,
 * scaled up slightly for a "brute" feel — same safe-generic-biped
 * pattern used for boss 2 and boss 3's placeholder renderers.
 */
public class BastionWarlordRenderer extends MobEntityRenderer<BastionWarlordEntity, BipedEntityModel<BastionWarlordEntity>> {
    private static final Identifier TEXTURE = new Identifier("minecraft", "textures/entity/piglin/piglin.png");

    public BastionWarlordRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PIGLIN)), 0.9f);
    }

    @Override
    public Identifier getTexture(BastionWarlordEntity entity) {
        return TEXTURE;
    }
}
