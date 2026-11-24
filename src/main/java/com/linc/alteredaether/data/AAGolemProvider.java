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
package com.linc.alteredaether.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.linc.alteredaether.AlteredAether;
import net.minecraft.data.PackOutput;

import java.util.Map;

/**
 * The gravitite golem's files: the pebble's models (tinted its network's colour, and a dull one for a pebble out of
 * reach or whose network is gone), the spawn egg's,
 * the golem's drops, and its language lines (see {@link #language}).
 */
public final class AAGolemProvider extends JsonDataProvider {
    private static final String M = "message." + AlteredAether.MOD_ID + ".golem.";
    private static final String PEBBLE = "item." + AlteredAether.MOD_ID + ".storage_pebble";

    public AAGolemProvider(PackOutput output) {
        super(output, "gravitite golem");
    }

    @Override
    protected void generate() {
        // a grey chip tinted its network's colour (see GolemClient's colour handler), with an untinted glint on top
        JsonObject pebble = generated(AlteredAether.MOD_ID + ":item/storage_pebble");
        pebble.getAsJsonObject("textures").addProperty("layer1", AlteredAether.MOD_ID + ":item/storage_pebble_glint");
        JsonObject predicate = new JsonObject();
        predicate.addProperty(AlteredAether.MOD_ID + ":dull", 1);
        JsonObject override = new JsonObject();
        override.add("predicate", predicate);
        override.addProperty("model", AlteredAether.MOD_ID + ":item/storage_pebble_dull");
        JsonArray overrides = new JsonArray();
        overrides.add(override);
        pebble.add("overrides", overrides);
        asset("models/item", AlteredAether.id("storage_pebble"), pebble);
        asset("models/item", AlteredAether.id("storage_pebble_dull"), generated(AlteredAether.MOD_ID + ":item/storage_pebble_dull"));
        JsonObject egg = new JsonObject();
        egg.addProperty("parent", "minecraft:item/template_spawn_egg");
        asset("models/item", AlteredAether.id("gravitite_golem_spawn_egg"), egg);
        loot();
    }

    private static JsonObject generated(String texture) {
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", texture);
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:item/generated");
        model.add("textures", textures);
        return model;
    }

    /**
     * The holystone it stood on, and about half the enchanted gravitite it was: four or five ingots with the gravitite
     * rework (its block is nine), and without it the block half the time.
     */
    private void loot() {
        JsonArray pools = new JsonArray();
        pools.add(pool("aether:holystone", null));
        JsonObject gravitite = pool(AlteredAether.MOD_ID + ":enchanted_gravitite_ingot", null);
        JsonArray entries = gravitite.getAsJsonArray("entries");
        JsonObject ingots = AALootProvider.gravititeRework(entries.get(0).getAsJsonObject(), true);
        JsonObject range = new JsonObject();
        range.addProperty("type", "minecraft:uniform");
        range.addProperty("min", 4);
        range.addProperty("max", 5);
        JsonObject count = new JsonObject();
        count.addProperty("function", "minecraft:set_count");
        count.add("count", range);
        JsonArray functions = new JsonArray();
        functions.add(count);
        ingots.add("functions", functions);
        JsonObject block = new JsonObject();
        block.addProperty("type", "minecraft:item");
        block.addProperty("name", "aether:enchanted_gravitite");
        AALootProvider.gravititeRework(block, false);
        JsonObject chance = new JsonObject();
        chance.addProperty("condition", "minecraft:random_chance");
        chance.addProperty("chance", 0.5);
        block.getAsJsonArray("conditions").add(chance);
        entries.add(block);
        pools.add(gravitite);
        JsonObject table = new JsonObject();
        table.addProperty("type", "minecraft:entity");
        table.add("pools", pools);
        table.addProperty("random_sequence", AlteredAether.MOD_ID + ":entities/gravitite_golem");
        data("loot_table/entities", AlteredAether.id("gravitite_golem"), table);
    }

    private static JsonObject pool(String item, JsonObject condition) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", item);
        JsonArray entries = new JsonArray();
        entries.add(entry);
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1.0);
        pool.addProperty("bonus_rolls", 0.0);
        pool.add("entries", entries);
        if (condition != null) {
            JsonArray conditions = new JsonArray();
            conditions.add(condition);
            pool.add("conditions", conditions);
        }
        return pool;
    }

    static void language(Map<String, String> lang) {
        lang.put("entity." + AlteredAether.MOD_ID + ".gravitite_golem", "Gravitite Golem");
        lang.put("item." + AlteredAether.MOD_ID + ".gravitite_golem_spawn_egg", "Gravitite Golem Spawn Egg");
        lang.put(PEBBLE, "Storage Pebble");
        lang.put(PEBBLE + ".unlinked", "Use a gravitite golem with an empty hand to get one of its own");
        lang.put(PEBBLE + ".dead", "Once belonged to %s");
        lang.put(PEBBLE + ".belongs", "Belongs to %s");
        lang.put(PEBBLE + ".mode", "Mode: %s");
        lang.put(PEBBLE + ".links", "Linked storage: %s / %s");
        lang.put("entity." + AlteredAether.MOD_ID + ".storage_pebble", "Storage Pebble");

        lang.put(M + "mode.tidy", "Tidy");
        lang.put(M + "mode.compact", "Compact");
        lang.put(M + "mode.tidy.desc", "each family of items kept together, as finely as there are chests for");
        lang.put(M + "mode.compact.desc", "everything packed into as few chests as it fits in");
        lang.put(M + "mode", "Mode: %s, %s");
        lang.put(M + "pebble", "%s hands you a storage pebble");
        lang.put(M + "already_carry", "You already carry a pebble of %s's network");
        lang.put(M + "pebble_unlinked", "This pebble belongs to no golem");
        lang.put(M + "pebble_dead", "This pebble's golems are gone");
        lang.put(M + "cant_hear", "%s can't hear you from here");
        lang.put(M + "other_network", "%s doesn't answer to %s's pebble");
        lang.put(M + "golem_moving", "%s must be sitting down for that");
        lang.put(M + "linked", "Linked to %s (%s / %s)");
        lang.put(M + "linked_source", "Linked to %s, to be emptied only (%s / %s)");
        lang.put(M + "linked_blocked", "Linked to %s, but it's walled in: no golem can get at it (%s / %s)");
        lang.put(M + "linked_bulk", "Linked to %s as bulk storage, for what it holds and what there is most of (%s / %s)");
        lang.put(M + "linked_misc", "Linked to %s, for the odds and ends or equipment (%s / %s)");
        lang.put(M + "linked_sink", "Linked to %s, to be fed what its filter lets through (%s / %s)");
        lang.put(M + "linked_sink_unfiltered", "Linked to %s, but it has no filter, so it is fed nothing (%s / %s)");
        lang.put(M + "unlinked", "Unlinked from %s");
        lang.put(M + "linked_all", "%s linked %s more around it (%s / %s)");
        lang.put(M + "unlinked_all", "%s unlinked %s around it");
        lang.put(M + "out_of_reach", "Out of %s's reach");
        lang.put(M + "full", "%s can't link any more storage (%s)");
        lang.put(M + "taken", "Already linked to %s");
        lang.put(M + "not_storage", "%s can't be linked");
        lang.put(M + "too_far_to_send", "That's too far to send %s (%s blocks at most)");
        lang.put(M + "cant_sit", "%s can't sit there");
        lang.put(M + "on_its_way", "%s is on its way");
        lang.put(M + "cant_reach", "%s can't reach that spot");
        lang.put(M + "dyed", "%s is %s now");
        lang.put(M + "dyed_joined", "%s is %s now, and works with its network of %s");
        lang.put(M + "network_full", "%s rests: its network already has %s golems");
        lang.put(M + "no_space", "%s has no room for %s items");
        lang.put(M + "hint.linked", "Linked to %s");
        lang.put(M + "hint.linked_blocked", "Linked to %s, but walled in");
        lang.put(M + "hint.not_linked", "Not linked");
        lang.put(M + "hint.empty_only", "Not linked (would only be emptied)");
        lang.put(M + "hint.insert_only", "Not linked (would be fed what its filter lets through)");
        lang.put(M + "hint.cant_link", "Can't be linked");

        String subtitles = "subtitles." + AlteredAether.MOD_ID + ".entity.gravitite_golem.";
        lang.put(subtitles + "hum", "Gravitite golem concentrates");
        lang.put(subtitles + "chime", "Gravitite golem chimes");
        lang.put(subtitles + "link", "Storage linked");
        lang.put(subtitles + "unlink", "Storage unlinked");
        lang.put(subtitles + "shake", "Gravitite golem shakes its head");
        lang.put(subtitles + "repel", "Gravitite golem repels");

        String jade = "config.jade.plugin_" + AlteredAether.MOD_ID + ".";
        lang.put(jade + "gravitite_golem", "Gravitite golem");
        lang.put(jade + "golem_storage", "Gravitite golem storage");
        String status = "jade." + AlteredAether.MOD_ID + ".golem.";
        lang.put(status + "mode", "Mode: %s");
        lang.put(status + "links", "Linked: %s / %s");
        lang.put(status + "linked_golems", "Linked golems: %s");
        lang.put(status + "sorted", "Sorted");
        lang.put(status + "sorting", "Sorting: %s moves left");
        lang.put(status + "paused", "Paused by redstone");
        lang.put(status + "idle", "Resting");
        lang.put(status + "moving", "On the move");
        lang.put(status + "no_space", "No room for %s items");
        lang.put(status + "out_of_reach", "%s storage out of reach");
        lang.put(status + "in_use", "Waiting: %s storage in use");
        lang.put(status + "blocked", "%s storage walled in");
        lang.put(status + "refused", "Resting: its network already has %s golems");
        lang.put(status + "encased", "Walled in: no golem can get at it");
        lang.put(status + "linked_to", "Linked to %s");
        lang.put(status + "source", "Emptied only");
        lang.put(status + "bulk", "Bulk storage");
        lang.put(status + "misc", "For the odds and ends or equipment");
        lang.put(status + "sink", "Fed what its filter lets through");
        lang.put(status + "sink_unfiltered", "Fed nothing: it has no filter");
        lang.put(status + "holds", "Kept for: %s");
        lang.put(status + "odds_and_ends", "odds and ends");
        lang.put(status + "pinned_only", "Only for: %s");

        String tips = "aether.pro_tips.line." + AlteredAether.MOD_ID + ".";
        lang.put(tips + "golem_build", "A carved pumpkin on enchanted gravitite on holystone wakes a gravitite golem.");
        lang.put(tips + "golem_pebble", "Take a storage pebble from a gravitite golem and use it on chests to have the golem sort them.");
        lang.put(tips + "golem_modes", "Sneak and use a gravitite golem with an empty hand to switch between Tidy and Compact sorting.");
        lang.put(tips + "golem_frames", "An item frame on a chest tells a gravitite golem what that chest is for. A glow item frame keeps everything else out.");

        String jei = "jei." + AlteredAether.MOD_ID + ".golem.";
        lang.put("jei." + AlteredAether.MOD_ID + ".golem_pebble", "Storage Pebble from a Golem");
        lang.put("container." + AlteredAether.MOD_ID + ".golem_network", "Golem Chest Network");
        String gui = "gui." + AlteredAether.MOD_ID + ".golem_network.";
        lang.put(gui + "search", "Search...");
        lang.put(gui + "sort", "Sort: %s");
        lang.put(gui + "sort.count_down", "most first");
        lang.put(gui + "sort.count_up", "fewest first");
        lang.put(gui + "sort.name", "A to Z");
        lang.put(gui + "sort.name_reversed", "Z to A");
        lang.put(gui + "sort.chest", "chest by chest");
        lang.put(gui + "chest", "%s (%s, %s, %s)");
        lang.put(gui + "empty", "Nothing stored");
        lang.put(gui + "no_match", "Nothing matches");
        lang.put(gui + "in_this_chest", "%s in this chest");
        lang.put(gui + "total", "%s in %s chests");
        lang.put(gui + "total_one", "%s in %s chest");
        lang.put(jei + "guide.intro", "Gravitite golems sort storage by telekinesis. What they answer to:");
        lang.put(jei + "guide.build.title", "Build: ");
        lang.put(jei + "guide.build", "a carved pumpkin (or a Deep Aether carved squash) on a block of enchanted gravitite standing on holystone. It sits where it was built, and hands its builder a storage pebble.");
        lang.put(jei + "guide.hand.title", "Empty hand on the golem: ");
        lang.put(jei + "guide.hand", "see everything its network holds. Sneak to switch between Tidy (families of items kept together) and Compact (everything in as few chests as it fits).");
        lang.put(jei + "guide.pebble.take.title", "Aether stone or glowstone dust on the golem: ");
        lang.put(jei + "guide.pebble.take", "trade one for a storage pebble, unless you already carry one for its network.");
        lang.put(jei + "guide.tidy.title", "Tidy: ");
        lang.put(jei + "guide.tidy", "items are filed by where they come from (the Aether, the overworld...), what they are (blocks, resources, food...) and what they're made of: spruce stairs, planks and logs are all spruce. Armour, tools and weapons are kept together as equipment wherever they come from. A family that fills a quarter of a chest gets a chest of its own, and chests to spare go to smaller families of two stacks or more, though one is kept free for whatever comes next; the rest share with their kin (spruce with the other wood), and what fits nowhere finer goes with the odds and ends. A new family takes the free chest nearest its kin, and a full chest spills into the free chest beside it.");
        lang.put(jei + "guide.drawers.title", "Drawers: ");
        lang.put(jei + "guide.drawers", "are bulk storage. Each drawer keeps what it holds; an empty one is given whatever fills the most stacks. A locked empty drawer is left alone, and a compacting drawer is only ever filled.");
        lang.put(jei + "guide.crate.title", "Quark crates: ");
        lang.put(jei + "guide.crate", "are each for either equipment (armour, tools and weapons) or the odds and ends, never both. Equipment gets the crates it needs first, and the odds and ends the rest, up to what a crate holds.");
        lang.put(jei + "guide.funnels.title", "Brass funnel into a machine: ");
        lang.put(jei + "guide.funnels", "link the funnel, and the golem keeps the machine fed with what the funnel's filter lets through. No filter, nothing is fed; redstone on the funnel holds it shut.");
        lang.put(jei + "guide.pebble.storage.title", "Pebble on storage: ");
        lang.put(jei + "guide.pebble.storage", "link it, or unlink it, without opening it. Hoppers and depots are only ever emptied. Look at linked storage with Jade to see what it is kept for.");
        lang.put(jei + "guide.pebble.golem.title", "Pebble on the golem: ");
        lang.put(jei + "guide.pebble.golem", "link all the storage within its reach. Sneak to unlink it all.");
        lang.put(jei + "guide.pebble.send.title", "Sneak, pebble on the top of a block: ");
        lang.put(jei + "guide.pebble.send", "send the golem to sit there.");
        lang.put(jei + "guide.search.title", "Golem Chest Network: ");
        lang.put(jei + "guide.search", "lists what the linked storage holds and how many. Click items to make the chests holding them glow, each in its own colour, for half a minute.");
        lang.put(jei + "guide.sorting.title", "Sorting: ");
        lang.put(jei + "guide.sorting", "a chest is sorted a few seconds after it changes, never while someone has it open. Items fly out of an open side of one chest and into the next.");
        lang.put(jei + "guide.dye.title", "Dye on the golem: ");
        lang.put(jei + "guide.dye", "recolours it. Golems of one colour sitting within reach of each other work as one network, up to %s; one more rests until there is room.");
        lang.put(jei + "guide.frames.title", "Item frame on a chest: ");
        lang.put(jei + "guide.frames", "it holds only that item, and everything else is moved out. A glow item frame does the same.");
        lang.put(jei + "guide.frames.shelf.title", "Item shelf on a chest: ");
        lang.put(jei + "guide.frames.shelf", "the same, for the item on the shelf.");
        lang.put(jei + "guide.frames.create.title", "Create placard on a chest: ");
        lang.put(jei + "guide.frames.create", "the same, for the item on the placard. A Create filter on one, in a frame or on a shelf stands for everything it lets through.");
        lang.put(jei + "guide.redstone.title", "Redstone: ");
        lang.put(jei + "guide.redstone", "power makes the golem rest; a pulse has it sort everything again.");
        lang.put(jei + "guide.comparator.title", "Comparator facing the block under a golem: ");
        lang.put(jei + "guide.comparator", "reads how full its network's storage is.");
        lang.put(jei + "guide.source.title", "Can't be used: ");
        lang.put(jei + "guide.source", "machines, and storage walled in on every side (it shows red).");
        lang.put(jei + "guide.health.title", "Health: ");
        lang.put(jei + "guide.health", "a golem heals slowly while sitting at home. Struck, it pushes everything around it away.");
    }
}
