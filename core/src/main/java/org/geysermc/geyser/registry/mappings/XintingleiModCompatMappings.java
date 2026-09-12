/*
 * Copyright (c) 2026 Xintinglei
 */
package org.geysermc.geyser.registry.mappings;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.block.custom.CustomBlockState;
import org.geysermc.geyser.api.block.custom.NonVanillaCustomBlockData;
import org.geysermc.geyser.api.block.custom.component.BoxComponent;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.block.custom.component.GeometryComponent;
import org.geysermc.geyser.api.block.custom.component.MaterialInstance;
import org.geysermc.geyser.api.block.custom.nonvanilla.JavaBlockState;
import org.geysermc.geyser.api.block.custom.nonvanilla.JavaBoundingBox;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.component.geyser.GeyserBlockPlacer;
import org.geysermc.geyser.api.item.custom.v2.component.geyser.GeyserItemDataComponents;
import org.geysermc.geyser.api.item.custom.v2.component.java.JavaItemDataComponents;
import org.geysermc.geyser.api.item.custom.v2.NonVanillaCustomItemDefinition;
import org.geysermc.geyser.api.predicate.item.ItemMatchPredicate;
import org.geysermc.geyser.api.util.CreativeCategory;
import org.geysermc.geyser.api.util.Identifier;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the registry manifest produced by XintingleiBedrockBridge on the Fabric backend.
 *
 * <p>The manifest deliberately contains Java network IDs rather than protocol guesses. That
 * makes mod items resolve as normal Bedrock data-driven items instead of falling back to AIR.
 * Crop and rail states carry an empty collision list, so Bedrock clients cannot become caught in
 * the full-cube fallback that Geyser would otherwise have to use for unknown Fabric blocks.</p>
 */
public final class XintingleiModCompatMappings {
    public static final String MANIFEST_FILE = "xintinglei-mod-compat.json";

    private static final Gson GSON = new Gson();
    private static CompatManifest manifest;

    private XintingleiModCompatMappings() {
    }

    public static void registerBlocks(GeyserDefineCustomBlocksEvent event) {
        CompatManifest data = manifest();
        if (data == null) {
            return;
        }
        for (BlockStateDefinition state : data.states()) {
            NonVanillaCustomBlockData block = blockData(state, data.displayNameFor(state.identifier()));
            event.register(block);
            event.registerOverride(javaState(state), block.defaultBlockState());
        }
        GeyserImpl.getInstance().getLogger().info("Xintinglei mod compatibility registered " + data.states().size()
            + " Fabric block states from " + MANIFEST_FILE + ".");
    }

    public static void registerItems(GeyserDefineCustomItemsEvent event) {
        CompatManifest data = manifest();
        if (data == null) {
            return;
        }
        for (ItemDefinition item : data.items()) {
            Identifier itemId = Identifier.of(item.identifier());
            NonVanillaCustomItemDefinition.Builder definition = NonVanillaCustomItemDefinition.builder(itemId, item.rawId())
                .displayName(item.displayName())
                .bedrockOptions(CustomItemBedrockOptions.builder()
                    .icon(textureKey(item.identifier()))
                    .creativeCategory(CreativeCategory.ITEMS))
                .component(JavaItemDataComponents.MAX_STACK_SIZE, Math.max(1, Math.min(99, item.maxCount())));
            if (item.maxDamage() > 0) {
                definition.component(JavaItemDataComponents.MAX_DAMAGE, item.maxDamage());
            }
            BlockStateDefinition placedBlock = data.defaultStateFor(item.identifier());
            if (placedBlock != null) {
                definition.component(GeyserItemDataComponents.BLOCK_PLACER,
                    GeyserBlockPlacer.of(Identifier.of(placedBlock.namespace(), customBlockName(placedBlock)), true));
            }
            event.register(definition.build());
        }
        GeyserImpl.getInstance().getLogger().info("Xintinglei mod compatibility registered " + data.items().size()
            + " Fabric items from " + MANIFEST_FILE + ".");
        registerDrinks(event);
    }

    /**
     * DrinksDataPack items are vanilla honey bottles distinguished by the 1.21.4
     * custom_model_data string component. Register one predicate-backed definition
     * per drink so Geyser can select the correct Bedrock icon without changing the
     * server-side datapack or treating every honey bottle as the same item.
     */
    private static void registerDrinks(GeyserDefineCustomItemsEvent event) {
        for (DrinkDefinition drink : DRINKS) {
            Identifier bedrockIdentifier = Identifier.of("drinks", drink.id());
            CustomItemDefinition definition = CustomItemDefinition.builder(
                    bedrockIdentifier, Identifier.of("minecraft", "honey_bottle"))
                .displayName(drink.displayName())
                .bedrockOptions(CustomItemBedrockOptions.builder()
                    .icon("xintinglei_drinks_" + drink.id())
                    .creativeCategory(CreativeCategory.ITEMS))
                .predicate(ItemMatchPredicate.customModelData(0, "drinks:" + drink.id()))
                .build();
            event.register(Identifier.of("minecraft", "honey_bottle"), definition);
        }
        GeyserImpl.getInstance().getLogger().info("Xintinglei DrinksDataPack compatibility registered " + DRINKS.size()
            + " honey-bottle custom model items.");
    }

    private static final List<DrinkDefinition> DRINKS = List.of(
        new DrinkDefinition("coffee", "Coffee"),
        new DrinkDefinition("energy_drink", "Energy Drink"),
        new DrinkDefinition("herbal_tea", "Herbal Tea"),
        new DrinkDefinition("berry_juice", "Berry Juice"),
        new DrinkDefinition("mint_cooler", "Mint Cooler"),
        new DrinkDefinition("miner_soda", "Miner Soda"),
        new DrinkDefinition("ocean_tonic", "Ocean Tonic"),
        new DrinkDefinition("blaze_brew", "Blaze Brew"),
        new DrinkDefinition("monster_black", "Monster Black"),
        new DrinkDefinition("monster_white", "Monster White"),
        new DrinkDefinition("monster_green", "Monster Green"),
        new DrinkDefinition("monster_pink", "Monster Pink"),
        new DrinkDefinition("apple_carrot_juice", "Apple Carrot Juice"),
        new DrinkDefinition("clear_soda", "Clear Soda"),
        new DrinkDefinition("vodka", "Vodka"),
        new DrinkDefinition("almond_water", "Almond Water"),
        new DrinkDefinition("bean_juice", "Bean Juice"),
        new DrinkDefinition("mega_boba_tea", "Mega Boba Tea")
    );

    private record DrinkDefinition(String id, String displayName) {
    }

    private static NonVanillaCustomBlockData blockData(BlockStateDefinition state, String displayName) {
        List<BoxComponent> collisions = new ArrayList<>();
        for (JavaBoundingBox box : state.collision()) {
            collisions.add(toBedrockBox(box));
        }
        MaterialInstance.Builder material = MaterialInstance.builder().texture(state.texture());
        // Java crop/rail textures contain transparent pixels. Bedrock's default
        // material is opaque, which renders the transparent background as a
        // solid square unless alpha testing is explicitly enabled.
        if ("cross".equals(state.geometry())) {
            material.renderMethod("alpha_test");
        }
        CustomBlockComponents components = CustomBlockComponents.builder()
            .displayName(displayName)
            .geometry(GeometryComponent.builder()
                .identifier("cross".equals(state.geometry()) ? "geometry.xintinglei_cross" : "minecraft:geometry.full_block")
                .build())
            .materialInstance("*", material.build())
            .collisionBoxes(collisions)
            .selectionBox(collisions.isEmpty() ? BoxComponent.fullBox() : collisions.get(0))
            .destructibleByMining(Math.max(0f, state.blockHardness()))
            .build();
        return NonVanillaCustomBlockData.builder()
            .namespace(state.namespace())
            .name(customBlockName(state))
            .components(components)
            .build();
    }

    private static JavaBlockState javaState(BlockStateDefinition state) {
        return JavaBlockState.builder()
            .identifier(state.javaIdentifier())
            .javaId(state.rawId())
            .stateGroupId(state.stateGroupId())
            .blockHardness(Math.max(0f, state.blockHardness()))
            .waterlogged(state.waterlogged())
            .collision(state.collision().toArray(JavaBoundingBox[]::new))
            .canBreakWithHand(state.canBreakWithHand())
            .build();
    }

    private static BoxComponent toBedrockBox(JavaBoundingBox box) {
        return new BoxComponent(
            (float) ((box.middleX() - box.sizeX() / 2d) * 16d - 8d),
            (float) ((box.middleY() - box.sizeY() / 2d) * 16d),
            (float) ((box.middleZ() - box.sizeZ() / 2d) * 16d - 8d),
            (float) (box.sizeX() * 16d), (float) (box.sizeY() * 16d), (float) (box.sizeZ() * 16d)
        );
    }

    private static String customBlockName(BlockStateDefinition state) {
        return state.path().replace('/', '_').replace('-', '_') + "_state_" + state.rawId();
    }

    private static String textureKey(String identifier) {
        int separator = identifier.indexOf(':');
        String namespace = identifier.substring(0, separator);
        String path = identifier.substring(separator + 1);
        return "xintinglei_" + namespace + "_" + path.replace('/', '_').replace('-', '_');
    }

    private static CompatManifest manifest() {
        if (manifest != null) {
            return manifest;
        }
        Path path = GeyserImpl.getInstance().getBootstrap().getConfigFolder().resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(path)) {
            GeyserImpl.getInstance().getLogger().warning("Xintinglei mod compatibility is inactive: copy the Fabric export to " + path);
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("schema") || root.get("schema").getAsInt() < 1 || !root.has("items") || !root.has("block_states")) {
                throw new IllegalArgumentException("unsupported manifest schema");
            }
            manifest = CompatManifest.parse(root);
            return manifest;
        } catch (Exception exception) {
            GeyserImpl.getInstance().getLogger().error("Could not load Xintinglei mod compatibility manifest " + path + "; no mod items or blocks will be registered", exception);
            return null;
        }
    }

    private record ItemDefinition(String identifier, int rawId, String displayName, int maxCount, int maxDamage) {
    }

    private record BlockDefinition(String identifier, int defaultStateRawId, String displayName) {
    }

    private record BlockStateDefinition(String identifier, int rawId, int stateGroupId, float blockHardness,
                                        boolean waterlogged, boolean canBreakWithHand, String geometry,
                                        String texture, Map<String, String> properties, List<JavaBoundingBox> collision) {
        String namespace() {
            return identifier.substring(0, identifier.indexOf(':'));
        }

        String path() {
            return identifier.substring(identifier.indexOf(':') + 1);
        }

        String javaIdentifier() {
            if (properties.isEmpty()) {
                return identifier;
            }
            StringBuilder result = new StringBuilder(identifier).append('[');
            properties.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                if (result.charAt(result.length() - 1) != '[') {
                    result.append(',');
                }
                result.append(entry.getKey()).append('=').append(entry.getValue());
            });
            return result.append(']').toString();
        }
    }

    private record CompatManifest(List<ItemDefinition> items, List<BlockStateDefinition> states,
                                  Map<String, BlockDefinition> blocks) {
        static CompatManifest parse(JsonObject root) {
            List<ItemDefinition> items = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("items")) {
                JsonObject item = element.getAsJsonObject();
                items.add(new ItemDefinition(item.get("identifier").getAsString(), item.get("raw_id").getAsInt(),
                    string(item, "display_name", item.get("identifier").getAsString()), integer(item, "max_count", 64),
                    integer(item, "max_damage", 0)));
            }
            List<BlockStateDefinition> states = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("block_states")) {
                JsonObject state = element.getAsJsonObject();
                String identifier = state.get("identifier").getAsString();
                Map<String, String> properties = new HashMap<>();
                if (state.has("properties")) {
                    state.getAsJsonObject("properties").entrySet().forEach(entry -> properties.put(entry.getKey(), entry.getValue().getAsString()));
                }
                List<JavaBoundingBox> collision = new ArrayList<>();
                if (state.has("collision")) {
                    for (JsonElement collisionElement : state.getAsJsonArray("collision")) {
                        JsonObject box = collisionElement.getAsJsonObject();
                        collision.add(new JavaBoundingBox(number(box, "middle_x", .5), number(box, "middle_y", .5),
                            number(box, "middle_z", .5), number(box, "size_x", 1), number(box, "size_y", 1), number(box, "size_z", 1)));
                    }
                } else if (!isNonSolid(identifier, properties)) {
                    collision.add(new JavaBoundingBox(.5, .5, .5, 1, 1, 1));
                }
                states.add(new BlockStateDefinition(identifier, state.get("raw_id").getAsInt(),
                    integer(state, "state_group_id", integer(state, "block_raw_id", 0)),
                    decimal(state, "block_hardness", isNonSolid(identifier, properties) ? 0 : 1),
                    bool(state, "waterlogged", false), bool(state, "can_break_with_hand", true),
                    string(state, "geometry", isNonSolid(identifier, properties) ? "cross" : "full_block"),
                    string(state, "texture", textureKey(identifier)), properties, collision));
            }
            states.sort(Comparator.comparingInt(BlockStateDefinition::rawId));
            Map<String, BlockDefinition> blocks = new HashMap<>();
            if (root.has("blocks")) {
                for (JsonElement element : root.getAsJsonArray("blocks")) {
                    JsonObject block = element.getAsJsonObject();
                    String id = block.get("identifier").getAsString();
                    blocks.put(id, new BlockDefinition(id, integer(block, "default_state_raw_id", -1), string(block, "display_name", id)));
                }
            }
            return new CompatManifest(List.copyOf(items), List.copyOf(states), Map.copyOf(blocks));
        }

        BlockStateDefinition defaultStateFor(String itemIdentifier) {
            BlockDefinition block = blocks.get(itemIdentifier);
            if (block == null) {
                return null;
            }
            return states.stream().filter(state -> state.identifier().equals(block.identifier()) && state.rawId() == block.defaultStateRawId()).findFirst()
                .orElseGet(() -> states.stream().filter(state -> state.identifier().equals(block.identifier())).findFirst().orElse(null));
        }

        String displayNameFor(String identifier) {
            return Optional.ofNullable(blocks.get(identifier)).map(BlockDefinition::displayName).orElse(identifier);
        }

        private static boolean isNonSolid(String identifier, Map<String, String> properties) {
            return identifier.contains("crop") || identifier.contains("rail") || properties.containsKey("age");
        }

        private static String string(JsonObject object, String key, String fallback) {
            return object.has(key) ? object.get(key).getAsString() : fallback;
        }

        private static int integer(JsonObject object, String key, int fallback) {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        }

        private static float decimal(JsonObject object, String key, float fallback) {
            return object.has(key) ? object.get(key).getAsFloat() : fallback;
        }

        private static double number(JsonObject object, String key, double fallback) {
            return object.has(key) ? object.get(key).getAsDouble() : fallback;
        }

        private static boolean bool(JsonObject object, String key, boolean fallback) {
            return object.has(key) ? object.get(key).getAsBoolean() : fallback;
        }
    }
}
