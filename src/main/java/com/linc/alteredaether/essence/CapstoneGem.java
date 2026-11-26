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
package com.linc.alteredaether.essence;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

/**
 * Which gem a capstone holds, for its model: the gem spot on each side takes the gem's colour. Every gem works the
 * same; a gem a pack adds to {@code #altered_aether:capstone_gems} shows as {@link #OTHER}.
 */
public enum CapstoneGem implements StringRepresentable {
    NONE("none", ""),
    ZANITE("zanite", "aether:zanite_gemstone"),
    SKYJADE("skyjade", "deep_aether:skyjade"),
    GRAVITITE("gravitite", "altered_aether:enchanted_gravitite_ingot"),
    AMBROSIUM("ambrosium", "aether:ambrosium_shard"),
    OTHER("other", "");

    private final String name;
    private final String item;

    CapstoneGem(String name, String item) {
        this.name = name;
        this.item = item;
    }

    /** The model's gem for {@code stack}, already known to be in the gem tag. */
    public static CapstoneGem of(ItemStack stack) {
        if (stack.isEmpty()) {
            return NONE;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (CapstoneGem gem : values()) {
            if (!gem.item.isEmpty() && gem.item.equals(id)) {
                return gem;
            }
        }
        return OTHER;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
