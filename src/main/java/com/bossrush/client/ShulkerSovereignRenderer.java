package com.bossrush.client;

import com.bossrush.entity.boss.ShulkerSovereignEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

/**
 * Placeholder renderer, scaled up for an imposing "guardian" silhouette.
 *
 * NOTE: this originally tried IronGolemEntityModel<T> for extra bulk, but
 * that model's type parameter is generic-bound to require T extend
 * IronGolemEntity specifically — it cannot be reused for a different
 * entity class (compile error). Same caution as TrueIronGolemRenderer;
 * a generic biped is the safe stand-in until a real Shulker-shaped
 * custom model is built via Blockbench.
 */
public class ShulkerSovereignRenderer extends MobEntityRenderer<ShulkerSovereignEntity, BipedEntityModel<ShulkerSovereignEntity>> {
    private static final Identifier TEXTURE = new Identifier("minecraft", "textures/entity/zombie/zombie.png");

    public ShulkerSovereignRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE)), 1.5f);
    }

    @Override
    public Identifier getTexture(ShulkerSovereignEntity entity) {
        return TEXTURE;
    }
}
