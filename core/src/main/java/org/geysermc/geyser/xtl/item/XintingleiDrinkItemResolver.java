/*
 * Copyright (c) 2026 Xintinglei
 */

package org.geysermc.geyser.xtl.item;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.registry.type.ItemMapping;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.CustomModelData;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;

/**
 * Resolves the DrinksDataPack's honey-bottle variants before an old Fabric registry ID can
 * be mistaken for a newer vanilla or modded item by an intermediate protocol translator.
 *
 * <p>The data pack's two model markers are intentionally both accepted. The string marker is
 * authoritative; the numeric marker keeps existing stacks working through proxy chains which
 * omit {@code custom_model_data.strings}.</p>
 */
public final class XintingleiDrinkItemResolver {
    private static final String DRINK_PREFIX = "drinks:";
    private static final int FIRST_MODEL_DATA = 1001;
    private static final int LAST_MODEL_DATA = 1018;

    private XintingleiDrinkItemResolver() {
    }

    /**
     * @return the ordinary honey-bottle mapping if this is a DrinksDataPack item, otherwise
     * {@code null}. Returning the base mapping lets the extension's normal custom-item
     * predicates select the exact Bedrock drink definition.
     */
    public static @Nullable ItemMapping resolve(GeyserSession session, @Nullable DataComponents components) {
        if (!isDrink(components)) {
            return null;
        }
        return session.getItemMappings().getMapping("minecraft:honey_bottle");
    }

    private static boolean isDrink(@Nullable DataComponents components) {
        if (components == null) {
            return false;
        }
        CustomModelData modelData = components.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (modelData == null) {
            return false;
        }
        if (modelData.strings().stream().anyMatch(value -> value.startsWith(DRINK_PREFIX))) {
            return true;
        }
        for (float value : modelData.floats()) {
            if (value >= FIRST_MODEL_DATA && value <= LAST_MODEL_DATA && value == Math.rint(value)) {
                return true;
            }
        }
        return false;
    }
}
