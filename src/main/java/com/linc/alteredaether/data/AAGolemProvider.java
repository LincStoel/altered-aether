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
 * The gravitite golem's files: the pebble's models (a dull one for a pebble whose golem is gone), the spawn egg's,
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
        JsonObject pebble = generated(AlteredAether.MOD_ID + ":item/storage_pebble");
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

    /** The holystone it stood on, and half the time the enchanted gravitite it was. */
    private void loot() {
        JsonArray pools = new JsonArray();
        pools.add(pool("aether:holystone", null));
        JsonObject chance = new JsonObject();
        chance.addProperty("condition", "minecraft:random_chance");
        chance.addProperty("chance", 0.5);
        pools.add(pool("aether:enchanted_gravitite", chance));
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
        lang.put(PEBBLE + ".distance", "%s blocks away");
        lang.put(PEBBLE + ".use", "Use on storage to link or unlink it");
        lang.put(PEBBLE + ".send", "Sneak and use on the top of a block to send the golem there");
        lang.put(PEBBLE + ".throw", "Use in the air to throw it, and it shatters");
        lang.put("entity." + AlteredAether.MOD_ID + ".storage_pebble", "Storage Pebble");

        lang.put(M + "mode.tidy", "Tidy");
        lang.put(M + "mode.compact", "Compact");
        lang.put(M + "mode.tidy.desc", "each kind of item kept to its own chest");
        lang.put(M + "mode.compact.desc", "everything packed into as few chests as it fits in");
        lang.put(M + "mode", "Mode: %s, %s");
        lang.put(M + "pebble", "%s hands you a storage pebble");
        lang.put(M + "already_carry", "You already carry %s's pebble");
        lang.put(M + "pebble_unlinked", "This pebble belongs to no golem");
        lang.put(M + "pebble_dead", "This pebble's golem is gone");
        lang.put(M + "cant_hear", "%s can't hear you from here");
        lang.put(M + "links", "%s: %s / %s storage linked, %s");
        lang.put(M + "linked", "Linked to %s (%s / %s)");
        lang.put(M + "linked_source", "Linked to %s, to be emptied only (%s / %s)");
        lang.put(M + "unlinked", "Unlinked from %s");
        lang.put(M + "out_of_reach", "Out of %s's reach");
        lang.put(M + "full", "%s can't link any more storage (%s)");
        lang.put(M + "taken", "Already linked to %s");
        lang.put(M + "not_storage", "%s can't be linked");
        lang.put(M + "too_far_to_send", "That's too far to send %s (%s blocks at most)");
        lang.put(M + "cant_sit", "%s can't sit there");
        lang.put(M + "on_its_way", "%s is on its way");
        lang.put(M + "cant_reach", "%s can't reach that spot");
        lang.put(M + "golems_linked", "%s and %s now work together");
        lang.put(M + "golems_unlinked", "%s and %s now work apart");
        lang.put(M + "golems_not_seated", "Both golems must be sitting down to link");
        lang.put(M + "golems_too_far", "%s and %s sit too far apart (%s blocks at most)");
        lang.put(M + "golems_too_many", "A network holds at most %s golems");
        lang.put(M + "none_stored", "No %s in linked storage");
        lang.put(M + "found", "%s: %s in %s chests");
        lang.put(M + "found_one", "%s: %s in %s chest");
        lang.put(M + "no_space", "%s has no room for %s items");
        lang.put(M + "hint.linked", "Linked to %s");
        lang.put(M + "hint.not_linked", "Not linked");
        lang.put(M + "hint.empty_only", "Not linked (would only be emptied)");
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
        lang.put(status + "linked_to", "Linked to %s");
        lang.put(status + "source", "Emptied only");
        lang.put(status + "pinned", "Pinned: %s");
        lang.put(status + "pinned_only", "Only for: %s");

        String tips = "aether.pro_tips.line." + AlteredAether.MOD_ID + ".";
        lang.put(tips + "golem_build", "A carved pumpkin on enchanted gravitite on holystone wakes a gravitite golem.");
        lang.put(tips + "golem_pebble", "Take a storage pebble from a gravitite golem and use it on chests to have the golem sort them.");
        lang.put(tips + "golem_modes", "Sneak and use a gravitite golem with an empty hand to switch between Tidy and Compact sorting.");
        lang.put(tips + "golem_frames", "An item frame on a chest tells a gravitite golem what that chest is for. A glow item frame keeps everything else out.");

        String jei = "jei." + AlteredAether.MOD_ID + ".golem.";
        lang.put(jei + "build", "Gravitite golems sort storage by telekinesis. Build one by placing a carved pumpkin (or a Deep Aether carved squash) on a block of enchanted gravitite standing on holystone. It sits where it was built.");
        lang.put(jei + "pebble", "Use a golem with an empty hand to take its storage pebble. Use the pebble on chests, barrels and other storage to link them; use it again to unlink. Hoppers and depots are only ever emptied. Machines can't be linked.");
        lang.put(jei + "controls", "Sneak and use the golem with an empty hand to switch modes: Tidy keeps each kind of item to its own chest, Compact packs everything into as few as it fits in. Use the golem with any item to find where it is stored; use its own pebble on it to see everything linked.");
        lang.put(jei + "more", "An item frame on a chest pins that item (and its family) to the chest. A glow item frame or Create placard pins only that item, and the golem moves everything else out wherever it has room. Sneak and use the pebble on the top of a block to send the golem there; that becomes its new seat. A redstone signal makes it rest. Use another golem's pebble on a golem to make them work together. Ambrosium heals it.");
    }
}
