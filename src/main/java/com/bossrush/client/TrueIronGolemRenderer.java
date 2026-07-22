package com.bossrush.client;

import com.bossrush.entity.boss.TrueIronGolemEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

/**
 * Placeholder renderer using the standard zombie biped layer/texture,
 * scaled up for a bulky "golem" silhouette.
 *
 * NOTE: vanilla's own IronGolemEntityModel<T> requires its type parameter
 * to extend IronGolemEntity specifically (a compile-time generic bound),
 * so it cannot be reused for a custom entity class like this one — that
 * was tried initially and fails to compile. Same caution applies to
 * Warden's, Vex's, and Shulker's models; a generic biped is the safe
 * placeholder pattern used throughout this project until a real custom
 * model is built via Blockbench.
 */
public class TrueIronGolemRenderer extends MobEntityRenderer<TrueIronGolemEntity, BipedEntityModel<TrueIronGolemEntity>> {
    private static final Identifier TEXTURE = new Identifier("minecraft", "textures/entity/zombie/zombie.png");

    public TrueIronGolemRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE)), 1.35f);
    }

    @Override
    public Identifier getTexture(TrueIronGolemEntity entity) {
        return TEXTURE;
    }
}
