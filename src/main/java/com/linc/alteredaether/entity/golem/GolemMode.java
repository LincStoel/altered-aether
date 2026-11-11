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
package com.linc.alteredaether.entity.golem;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.entity.golem.sort.SortMode;
import net.minecraft.network.chat.Component;

/** How a golem network sorts: each kind of item kept to its own chest, or everything packed into as few as can hold it. */
public enum GolemMode {
    TIDY(SortMode.TIDY),
    COMPACT(SortMode.COMPACT);

    private final SortMode sort;

    GolemMode(SortMode sort) {
        this.sort = sort;
    }

    public SortMode sort() {
        return sort;
    }

    public GolemMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public Component title() {
        return Component.translatable("message." + AlteredAether.MOD_ID + ".golem.mode." + name().toLowerCase());
    }

    public static GolemMode byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : TIDY;
    }
}
