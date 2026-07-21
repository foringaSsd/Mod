package com.bossrush.item;

import com.bossrush.BossRushMod;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Guaranteed trophy drops, one per boss. These are plain vanilla-style
 * items (no Apotheosis/Zenith dependency) so they're safe to reference
 * from your own datapack recipes/advancements on top of whatever
 * progression system you're running.
 */
public class ModItems {
    public static Item GOLEM_CORE;
    public static Item WARDEN_CORE;
    public static Item FAMILIAR_CORE;
    public static Item SOVEREIGN_CORE;
    public static Item WARLORD_CORE;

    public static void register() {
        GOLEM_CORE = registerItem("golem_core");
        WARDEN_CORE = registerItem("warden_core");
        FAMILIAR_CORE = registerItem("familiar_core");
        SOVEREIGN_CORE = registerItem("sovereign_core");
        WARLORD_CORE = registerItem("warlord_core");

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
            entries.add(GOLEM_CORE);
            entries.add(WARDEN_CORE);
            entries.add(FAMILIAR_CORE);
            entries.add(SOVEREIGN_CORE);
            entries.add(WARLORD_CORE);
        });
    }

    private static Item registerItem(String path) {
        return Registry.register(
                Registries.ITEM,
                new Identifier(BossRushMod.MOD_ID, path),
                new Item(new FabricItemSettings().maxCount(16))
        );
    }
}
