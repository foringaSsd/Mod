package com.bossrush.client;

import com.bossrush.entity.boss.EndFamiliarEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

/**
 * Placeholder renderer using the standard skeleton biped layer/texture,
 * scaled down. Kept to a "standard biped" vanilla layer (like the husk
 * one used for boss 2) rather than something like Vex, whose model layer
 * may not share the same part names as a generic BipedEntityModel and
 * could fail at runtime even though it compiles.
 */
public class EndFamiliarRenderer extends MobEntityRenderer<EndFamiliarEntity, BipedEntityModel<EndFamiliarEntity>> {
    private static final Identifier TEXTURE = new Identifier("minecraft", "textures/entity/skeleton/skeleton.png");

    public EndFamiliarRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.SKELETON)), 0.6f);
    }

    @Override
    public Identifier getTexture(EndFamiliarEntity entity) {
        return TEXTURE;
    }
}
