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
package com.linc.alteredaether.compat;

import com.linc.alteredaether.config.AACommonConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * A data condition that holds when a mod is loaded and its switch under {@code [compat]} in the common config is on.
 * Every file that depends on another mod carries it instead of {@code neoforge:mod_loaded}, so the config can turn an
 * integration off without removing the mod. Mods with no switch of their own are treated as always enabled.
 */
public record ModEnabled(String modid) implements ICondition {
    public static final MapCodec<ModEnabled> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("modid").forGetter(ModEnabled::modid)
    ).apply(i, ModEnabled::new));

    @Override
    public boolean test(IContext context) {
        return ModList.get().isLoaded(modid) && AACommonConfig.compat(modid);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
