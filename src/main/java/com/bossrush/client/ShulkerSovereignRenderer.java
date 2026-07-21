package com.bossrush.client;

import com.bossrush.entity.boss.ShulkerSovereignEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.IronGolemEntityModel;
import net.minecraft.util.Identifier;

/**
 * Placeholder renderer, scaled up for an imposing "guardian" silhouette.
 * A real Shulker-shaped model would fit the theme better, but vanilla's
 * ShulkerEntityModel is likely bound to ShulkerEntity's own peek-animation
 * fields the same way Warden's and Vex's models are — same caution as the
 * other placeholder renderers in this project. Swap in a custom model via
 * Blockbench when ready.
 */
public class ShulkerSovereignRenderer extends MobEntityRenderer<ShulkerSovereignEntity, IronGolemEntityModel<ShulkerSovereignEntity>> {
    private static final Identifier TEXTURE = new Identifier("minecraft", "textures/entity/iron_golem/iron_golem.png");

    public ShulkerSovereignRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new IronGolemEntityModel<>(ctx.getPart(EntityModelLayers.IRON_GOLEM)), 1.5f);
    }

    @Override
    public Identifier getTexture(ShulkerSovereignEntity entity) {
        return TEXTURE;
    }
}
