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

import com.google.gson.JsonObject;
import com.linc.alteredaether.AlteredAether;
import net.minecraft.data.PackOutput;

import java.util.Map;

/** What falling boulders (see compat/boulder) need besides code: the damage they do, and the words for it. */
public final class AABoulderProvider extends JsonDataProvider {
    /** The damage type's name, which is also the end of its death message's key. */
    public static final String DAMAGE = "boulder";

    public AABoulderProvider(PackOutput output) {
        super(output, "boulders");
    }

    @Override
    protected void generate() {
        JsonObject type = new JsonObject();
        type.addProperty("message_id", AlteredAether.MOD_ID + "_" + DAMAGE);
        type.addProperty("scaling", "never");
        type.addProperty("exhaustion", 0.1);
        data("damage_type", AlteredAether.id(DAMAGE), type);
    }

    /** The lang entries, added to the mod's one file by AABlockProvider. */
    static void language(Map<String, String> lang) {
        lang.put("death.attack." + AlteredAether.MOD_ID + "_" + DAMAGE, "%1$s was flattened by a falling boulder");
    }
}
