/*
 * Copyright (c) 2026 Xintinglei
 */

package org.geysermc.geyser.xtl.item;

import org.checkerframework.checker.nullness.qual.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.geysermc.geyser.registry.type.ItemMapping;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.CustomModelData;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final String TRANSLATION_PREFIX = "item.drinks.";
    private static final int FIRST_MODEL_DATA = 1001;
    private static final int LAST_MODEL_DATA = 1018;
    private static final Set<String> LOGGED_RESOLUTIONS = ConcurrentHashMap.newKeySet();

    private XintingleiDrinkItemResolver() {
    }

    /**
     * @return the ordinary honey-bottle mapping and exact Bedrock definition if this is a
     * DrinksDataPack item, otherwise {@code null}.
     */
    public static @Nullable Resolution resolve(GeyserSession session, @Nullable DataComponents components) {
        String drinkId = drinkId(components);
        if (drinkId == null) {
            return null;
        }
        ItemMapping base = session.getItemMappings().getMapping("minecraft:honey_bottle");
        if (base == null) {
            return null;
        }
        String bedrockIdentifier = DRINK_PREFIX + drinkId;
        for (ItemDefinition definition : session.getItemMappings().getItemDefinitions().values()) {
            if (bedrockIdentifier.equals(definition.getIdentifier())) {
                if (LOGGED_RESOLUTIONS.add(drinkId)) {
                    session.getGeyser().getLogger().info("[xintinglei-drinks] Resolved " + drinkId
                        + " from the Java item component data.");
                }
                return new Resolution(base, definition);
            }
        }
        return null;
    }

    private static @Nullable String drinkId(@Nullable DataComponents components) {
        if (components == null) {
            return null;
        }

        // The custom name survives Java protocol translators more reliably than the newer
        // custom-model-data component. It is the authoritative fallback for existing stacks.
        String nameId = drinkIdFromName(components.get(DataComponentTypes.CUSTOM_NAME));
        if (nameId != null) {
            return nameId;
        }

        CustomModelData modelData = components.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (modelData == null) {
            return null;
        }
        for (String value : modelData.strings()) {
            if (value.startsWith(DRINK_PREFIX)) {
                return value.substring(DRINK_PREFIX.length());
            }
        }
        for (float value : modelData.floats()) {
            if (value >= FIRST_MODEL_DATA && value <= LAST_MODEL_DATA && value == Math.rint(value)) {
                return idForModelData((int) value);
            }
        }
        return null;
    }

    private static @Nullable String drinkIdFromName(@Nullable Component component) {
        if (component == null) {
            return null;
        }
        if (component instanceof TranslatableComponent translatable
            && translatable.key().startsWith(TRANSLATION_PREFIX)) {
            return translatable.key().substring(TRANSLATION_PREFIX.length());
        }
        // ViaVersion can flatten an unknown translation to its key and wrap it in an empty root
        // component. Search every child rather than assuming the identifying component is root.
        if (component instanceof TextComponent text && text.content().startsWith(TRANSLATION_PREFIX)) {
            return text.content().substring(TRANSLATION_PREFIX.length());
        }
        for (Component child : component.children()) {
            String childId = drinkIdFromName(child);
            if (childId != null) {
                return childId;
            }
        }
        return null;
    }

    private static @Nullable String idForModelData(int modelData) {
        return switch (modelData) {
            case 1001 -> "coffee";
            case 1002 -> "energy_drink";
            case 1003 -> "herbal_tea";
            case 1004 -> "berry_juice";
            case 1005 -> "mint_cooler";
            case 1006 -> "miner_soda";
            case 1007 -> "ocean_tonic";
            case 1008 -> "blaze_brew";
            case 1009 -> "monster_black";
            case 1010 -> "monster_white";
            case 1011 -> "monster_green";
            case 1012 -> "monster_pink";
            case 1013 -> "apple_carrot_juice";
            case 1014 -> "clear_soda";
            case 1015 -> "vodka";
            case 1016 -> "almond_water";
            case 1017 -> "bean_juice";
            case 1018 -> "mega_boba_tea";
            default -> null;
        };
    }

    public record Resolution(ItemMapping baseMapping, ItemDefinition definition) {
    }
}
