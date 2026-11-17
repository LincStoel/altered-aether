/*
 * Altered Aether
 * Copyright (C) 2026 Linc Stoel
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package com.linc.alteredaether.entity.golem.group;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where an item sits in the golems' family tree: its dimension (Aether, overworld, nether, end), its category
 * (blocks, resources, equipment, food, mechanisms, items), a material group within that (wood, stone, metals,
 * meals...) and the material itself (spruce, andesite, iron...). Tidy mode keeps families together as finely as
 * there are chests for.
 *
 * <p>The material of a block is that of what it is made from, followed back through crafting and stonecutting
 * recipes ({@link RecipeGraph}), then read off tags (dirt, sand, gravel), then off its name ({@link MaterialTokens}):
 * hollow and stripped spruce logs, spruce stairs and spruce planks are all spruce. Food is raw if no recipe of any kind
 * makes it, a meal if one does. Tags override each step for pack makers: {@code altered_aether:golem_origin/<dimension>}
 * and {@code altered_aether:golem_category/<category>}.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class GolemPaths {
    public static final List<String> DIMENSIONS = List.of("overworld", "aether", "nether", "end");
    public static final List<String> CATEGORIES = List.of("blocks", "resources", "equipment", "food", "mechanisms",
            "items");

    /**
     * An item's place in the tree.
     *
     * @param dimension one of {@link #DIMENSIONS}
     * @param category  one of {@link #CATEGORIES}
     * @param group     a material group within the category
     * @param type      the material
     */
    public record ItemPath(String dimension, String category, String group, String type) {
        /**
         * The families it sits in, from the top. Equipment is one family wherever it comes from, so armour, tools and
         * weapons stay together when they don't get chests of their own; everything else is filed by dimension first.
         */
        public List<String> names() {
            return category.equals("equipment") ? List.of(category, group, type) : List.of(dimension, category, group, type);
        }

        @Override
        public String toString() {
            return String.join("/", names());
        }
    }

    private static final Map<String, TagKey<Item>> ORIGINS = new ConcurrentHashMap<>();
    private static final Map<String, TagKey<Item>> CATEGORY_TAGS = new ConcurrentHashMap<>();
    static {
        for (String dimension : DIMENSIONS) {
            ORIGINS.put(dimension, own("golem_origin/" + dimension));
        }
        for (String category : CATEGORIES) {
            CATEGORY_TAGS.put(category, own("golem_category/" + category));
        }
    }

    private static final TagKey<Item> ORES = c("ores"), RAW = c("raw_materials"), INGOTS = c("ingots"),
            NUGGETS = c("nuggets"), GEMS = c("gems"), DUSTS = c("dusts"), STORAGE_BLOCKS = c("storage_blocks"),
            TOOLS = c("tools"), ARMORS = c("armors"), SEEDS = c("seeds"), CROPS = c("crops"), DYES = c("dyes"),
            DYED = c("dyed"), GLASS = c("glass_blocks"), PANES = c("glass_panes"), STONES = c("stones"),
            COBBLESTONES = c("cobblestones"), SANDS = c("sands"), GRAVELS = c("gravels"), FERTILIZERS = c("fertilizers");
    /** Things mobs drop and other crafting odds, kept as one group of resources. */
    private static final List<TagKey<Item>> DROPS = List.of(c("strings"), c("rods"), c("slime_balls"), c("bones"),
            c("feathers"), c("leathers"), c("eggs"), c("gunpowders"), c("ender_pearls"));
    /** The resource tags whose sub-tags ({@code c:ingots/iron}) name a material. */
    private static final List<String> MATERIAL_TAGS = List.of("ores", "raw_materials", "ingots", "nuggets", "gems",
            "dusts", "storage_blocks", "raw_blocks");
    /** Words food is cooked or prepared with: cooked beef is beef. */
    private static final List<String> FOOD_WORDS = List.of("cooked_", "baked_", "roasted_", "raw_", "golden_",
            "glistering_", "_slice", "_stew", "_soup", "_pie");

    private static final Map<Item, ItemPath> CACHE = new ConcurrentHashMap<>();
    @Nullable
    private static volatile Index index;

    private static TagKey<Item> c(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    private static TagKey<Item> own(String path) {
        return TagKey.create(Registries.ITEM, AlteredAether.id(path));
    }

    private GolemPaths() {
    }

    /** What the recipes say, worked out once per data load. */
    private record Index(RecipeGraph graph, Set<String> made, Set<String> woods) {
    }

    /** The place {@code stack} sorts in. */
    public static ItemPath path(ItemStack stack) {
        Item item = stack.getItem();
        if (stack.has(DataComponents.FOOD) != item.components().has(DataComponents.FOOD)) {
            // made edible (or not) by its components: not the item's usual place
            return compute(stack);
        }
        return CACHE.computeIfAbsent(item, i -> compute(stack));
    }

    /** {@code item}'s recipe chain back to its material, for the debug command. */
    public static List<String> chain(Item item) {
        return index().graph.chain(id(item));
    }

    /**
     * Where {@code stack} sorts within a chest: dimension, category and group, then its material, then the item,
     * then its components.
     */
    public static long order(ItemStack stack, ItemPath path) {
        long dimension = Math.max(0, DIMENSIONS.indexOf(path.dimension())) & 0xF;
        long category = Math.max(0, CATEGORIES.indexOf(path.category())) & 0xF;
        long group = (path.group().hashCode() & 0xFF);
        long type = path.type().hashCode() & 0xFFF;
        long item = BuiltInRegistries.ITEM.getId(stack.getItem()) & 0xFFFFF;
        long components = ItemStack.hashItemAndComponents(stack) & 0x3FF;
        return dimension << 58 | category << 54 | group << 46 | type << 34 | item << 14 | components;
    }

    private static Index index() {
        Index current = index;
        if (current != null) {
            return current;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        List<RecipeGraph.Recipe> recipes = new ArrayList<>();
        Set<String> made = new HashSet<>();
        if (server != null) {
            RecipeManager manager = server.getRecipeManager();
            HolderLookup.Provider registries = server.registryAccess();
            for (RecipeHolder<?> holder : manager.getRecipes()) {
                ItemStack result;
                try {
                    result = holder.value().getResultItem(registries);
                } catch (RuntimeException e) {
                    continue;
                }
                if (result.isEmpty()) {
                    continue;
                }
                made.add(id(result.getItem()));
                boolean stonecutter = holder.value() instanceof StonecutterRecipe;
                if (!stonecutter && !(holder.value() instanceof CraftingRecipe)) {
                    continue;
                }
                List<List<String>> ingredients = new ArrayList<>();
                for (Ingredient ingredient : holder.value().getIngredients()) {
                    List<String> options = new ArrayList<>();
                    for (ItemStack option : ingredient.getItems()) {
                        String optionId = id(option.getItem());
                        if (!options.contains(optionId)) {
                            options.add(optionId);
                        }
                    }
                    ingredients.add(options);
                }
                recipes.add(new RecipeGraph.Recipe(holder.id().toString(), stonecutter, id(result.getItem()), ingredients));
            }
        }
        Set<String> woods = new HashSet<>();
        WoodType.values().forEach(wood -> {
            String name = wood.name();
            woods.add(name.substring(name.indexOf(':') + 1));
        });
        current = new Index(new RecipeGraph(recipes), made, woods);
        if (server != null) {
            index = current;
        }
        return current;
    }

    private static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static String path(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static ItemPath compute(ItemStack stack) {
        Index index = index();
        Item item = stack.getItem();
        Item root = BuiltInRegistries.ITEM.get(ResourceLocation.parse(index.graph.root(id(item))));
        ItemStack rootStack = root == Items.AIR ? stack : new ItemStack(root);
        String category = category(stack);
        String group;
        String type;
        switch (category) {
            case "equipment" -> {
                group = equipmentGroup(stack);
                String path = path(item);
                int split = path.lastIndexOf('_');
                type = split > 0 ? path.substring(0, split) : path;
            }
            case "food" -> {
                if (stack.has(DataComponents.FOOD)) {
                    group = index.made.contains(id(item)) ? "meals" : "raw";
                } else {
                    group = "farming";
                }
                type = foodType(path(item));
            }
            case "resources" -> {
                String material = taggedMaterial(stack);
                if (material != null) {
                    group = stack.is(GEMS) ? "gems" : "metals";
                    type = material;
                } else {
                    group = DROPS.stream().anyMatch(stack::is) ? "drops" : stack.is(DYES) ? "dyes" : "other";
                    type = MaterialTokens.strip(path(item));
                }
            }
            case "blocks" -> {
                group = blockGroup(stack, rootStack, index);
                type = blockType(stack, rootStack, group, index);
            }
            case "mechanisms" -> {
                group = redstone(stack) ? "redstone" : "machines";
                type = MaterialTokens.material(path(item), index.woods);
            }
            default -> {
                group = BuiltInRegistries.ITEM.getKey(item).getNamespace();
                type = MaterialTokens.strip(path(item));
            }
        }
        return new ItemPath(dimension(stack, rootStack), category, group, type);
    }

    private static String dimension(ItemStack stack, ItemStack root) {
        for (String dimension : DIMENSIONS) {
            if (stack.is(ORIGINS.get(dimension))) {
                return dimension;
            }
        }
        for (String dimension : DIMENSIONS) {
            if (root.is(ORIGINS.get(dimension))) {
                return dimension;
            }
        }
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        String rootNamespace = BuiltInRegistries.ITEM.getKey(root.getItem()).getNamespace();
        List<? extends String> aether = AACommonConfig.GOLEM_AETHER_NAMESPACES.get();
        if (aether.contains(namespace) || aether.contains(rootNamespace)) {
            return "aether";
        }
        return "overworld";
    }

    private static String category(ItemStack stack) {
        for (String category : CATEGORIES) {
            if (stack.is(CATEGORY_TAGS.get(category))) {
                return category;
            }
        }
        Item item = stack.getItem();
        if (stack.is(TOOLS) || stack.is(ARMORS) || item instanceof TieredItem || item instanceof ArmorItem
                || stack.getMaxStackSize() == 1 && stack.isDamageableItem()) {
            return "equipment";
        }
        if (stack.has(DataComponents.FOOD) || stack.is(SEEDS) || stack.is(CROPS) || stack.is(FERTILIZERS)
                || item == Items.BONE_MEAL) {
            return "food";
        }
        for (String tag : MATERIAL_TAGS) {
            if (stack.is(c(tag))) {
                return "resources";
            }
        }
        if (stack.is(DYES) || DROPS.stream().anyMatch(stack::is)) {
            return "resources";
        }
        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (redstone(stack) || block instanceof EntityBlock && !(block instanceof SignBlock)
                    && !(block instanceof BannerBlock) && !(block instanceof BedBlock)
                    && !(block instanceof AbstractSkullBlock)) {
                return "mechanisms";
            }
            return "blocks";
        }
        return "items";
    }

    private static boolean redstone(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        Block block = blockItem.getBlock();
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock) {
            return false;
        }
        BlockState state = block.defaultBlockState();
        return state.isSignalSource() || state.hasProperty(BlockStateProperties.POWERED)
                || block instanceof DiodeBlock || block instanceof PistonBaseBlock || block instanceof BaseRailBlock;
    }

    private static String equipmentGroup(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ArmorItem || stack.is(ARMORS)) {
            return "armor";
        }
        if (item instanceof SwordItem || item instanceof BowItem || item instanceof CrossbowItem
                || item instanceof TridentItem || stack.is(c("tools/melee_weapon")) || stack.is(c("tools/ranged_weapon"))) {
            return "weapons";
        }
        if (item instanceof TieredItem || stack.is(TOOLS)) {
            return "tools";
        }
        return "other";
    }

    private static String foodType(String path) {
        String out = path;
        for (String word : FOOD_WORDS) {
            if (word.endsWith("_") && out.startsWith(word) && out.length() > word.length()) {
                out = out.substring(word.length());
            } else if (word.startsWith("_") && out.endsWith(word) && out.length() > word.length()) {
                out = out.substring(0, out.length() - word.length());
            }
        }
        return out;
    }

    /** The material a sub-tag names, {@code iron} for {@code c:ingots/iron}, or null. */
    @Nullable
    private static String taggedMaterial(ItemStack stack) {
        return stack.getTags()
                .map(tag -> tag.location())
                .filter(location -> location.getNamespace().equals("c"))
                .map(ResourceLocation::getPath)
                .filter(path -> {
                    int slash = path.indexOf('/');
                    return slash > 0 && MATERIAL_TAGS.contains(path.substring(0, slash));
                })
                .map(path -> path.substring(path.indexOf('/') + 1))
                .sorted()
                .findFirst()
                .orElse(null);
    }

    private static String blockGroup(ItemStack stack, ItemStack root, Index index) {
        if (stack.is(DYED) || stack.is(ItemTags.WOOL) || stack.is(ItemTags.WOOL_CARPETS) || stack.is(ItemTags.TERRACOTTA)) {
            return "dyed";
        }
        if (stack.is(GLASS) || stack.is(PANES) || root.is(GLASS)) {
            return "glass";
        }
        if (root.is(ItemTags.LOGS) || root.is(ItemTags.PLANKS) || stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS)
                || index.woods.contains(MaterialTokens.material(path(root.getItem()), index.woods))) {
            return "wood";
        }
        if (stack.is(ItemTags.LEAVES) || stack.is(ItemTags.SAPLINGS) || stack.is(ItemTags.FLOWERS)) {
            return "plants";
        }
        if (stack.is(ItemTags.DIRT) || root.is(ItemTags.DIRT) || stack.is(SANDS) || root.is(SANDS) || stack.is(GRAVELS)
                || root.is(GRAVELS)) {
            return "earth";
        }
        if (stack.is(STONES) || root.is(STONES) || stack.is(COBBLESTONES) || root.is(COBBLESTONES)
                || root.is(ItemTags.STONE_BRICKS)) {
            return "stone";
        }
        SoundType sound = soundOf(root);
        if (sound == SoundType.WOOD || sound == SoundType.NETHER_WOOD || sound == SoundType.CHERRY_WOOD
                || sound == SoundType.BAMBOO_WOOD) {
            return "wood";
        }
        if (sound == SoundType.STONE || sound == SoundType.DEEPSLATE || sound == SoundType.DEEPSLATE_BRICKS
                || sound == SoundType.POLISHED_DEEPSLATE || sound == SoundType.TUFF || sound == SoundType.TUFF_BRICKS
                || sound == SoundType.NETHER_BRICKS || sound == SoundType.NETHERRACK || sound == SoundType.BASALT
                || sound == SoundType.CALCITE || sound == SoundType.DRIPSTONE_BLOCK) {
            return "stone";
        }
        if (sound == SoundType.GRASS || sound == SoundType.CROP || sound == SoundType.AZALEA_LEAVES
                || sound == SoundType.CHERRY_LEAVES || sound == SoundType.VINE || sound == SoundType.CAVE_VINES) {
            return "plants";
        }
        if (sound == SoundType.GRAVEL || sound == SoundType.SAND || sound == SoundType.ROOTED_DIRT
                || sound == SoundType.MUD || sound == SoundType.PACKED_MUD) {
            return "earth";
        }
        return "other";
    }

    @Nullable
    private static SoundType soundOf(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock().defaultBlockState().getSoundType();
        }
        return null;
    }

    private static String blockType(ItemStack stack, ItemStack root, String group, Index index) {
        if (stack.is(ItemTags.DIRT) || root.is(ItemTags.DIRT)) {
            return "dirt";
        }
        if (stack.is(SANDS) || root.is(SANDS)) {
            return "sand";
        }
        if (stack.is(GRAVELS) || root.is(GRAVELS)) {
            return "gravel";
        }
        String rootPath = path(root.getItem());
        if (group.equals("dyed") || group.equals("glass")) {
            return withoutColour(path(stack.getItem()));
        }
        return MaterialTokens.material(rootPath, index.woods);
    }

    /** {@code red_stained_glass} as {@code stained_glass}: the colour is what dyed blocks have in common, not what. */
    private static String withoutColour(String path) {
        for (DyeColor colour : DyeColor.values()) {
            String prefix = colour.getName().toLowerCase(Locale.ROOT) + "_";
            if (path.startsWith(prefix) && path.length() > prefix.length()) {
                return path.substring(prefix.length());
            }
        }
        return path;
    }

    // ---------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        CACHE.clear();
        index = null;
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) manager -> {
            CACHE.clear();
            index = null;
        });
    }
}
