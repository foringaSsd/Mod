package com.bossrush.client;

import com.bossrush.entity.boss.TrueIronGolemEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.IronGolemEntityModel;
import net.minecraft.util.Identifier;

/**
 * Placeholder renderer: reuses vanilla's iron golem model + texture so the
 * boss renders correctly out of the box. To give it a distinct look, add
 * assets/bossrush/textures/entity/true_iron_golem.png and point TEXTURE at
 * new Identifier("bossrush", "textures/entity/true_iron_golem.png").
 */
public class TrueIronGolemRenderer extends MobEntityRenderer<TrueIronGolemEntity, IronGolemEntityModel<TrueIronGolemEntity>> {
    private static final Identifier TEXTURE = new Identifier("minecraft", "textures/entity/iron_golem/iron_golem.png");

    public TrueIronGolemRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new IronGolemEntityModel<>(ctx.getPart(EntityModelLayers.IRON_GOLEM)), 1.35f);
    }

    @Override
    public Identifier getTexture(TrueIronGolemEntity entity) {
        return TEXTURE;
    }
}
