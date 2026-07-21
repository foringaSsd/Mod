package com.bossrush.client;

import com.bossrush.entity.boss.HollowWardenEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

/**
 * Placeholder renderer. Vanilla's WardenEntityModel/WardenEntityRenderer
 * are hard-wired to WardenEntity's own animation-state fields, so they
 * can't be reused directly by a different entity class without either
 * extending WardenEntity itself or mixing those animation states in.
 * This uses a generic biped (husk) as a stand-in — swap in a real custom
 * model via Blockbench + a matching EntityModelLayer when ready.
 */
public class HollowWardenRenderer extends MobEntityRenderer<HollowWardenEntity, BipedEntityModel<HollowWardenEntity>> {
    private static final Identifier TEXTURE = new Identifier("minecraft", "textures/entity/zombie/husk.png");

    public HollowWardenRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.HUSK)), 1.4f);
    }

    @Override
    public Identifier getTexture(HollowWardenEntity entity) {
        return TEXTURE;
    }
}
