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
package com.linc.alteredaether.entity;

import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * A wild aerwhale's temper, as a horse's: how ready it is to accept a rider, from 0 to {@link #MAX}. Blue berries raise
 * it, and so does each rider it throws off. Every time it decides whether to keep a rider, it does with a chance of its
 * temper out of {@link #MAX}. It's kept as an attachment, which goes on the Aether's wild aerwhale as well as ours.
 */
public final class AerwhaleTemper {
    public static final int MAX = 100;

    public static int get(Entity whale) {
        return whale.getData(AARegistries.AERWHALE_TEMPER);
    }

    public static void set(Entity whale, int temper) {
        whale.setData(AARegistries.AERWHALE_TEMPER, Mth.clamp(temper, 0, MAX));
    }

    public static void add(Entity whale, int amount) {
        set(whale, get(whale) + amount);
    }

    /** Carries the temper over when one kind of aerwhale turns into the other. */
    public static void copy(Entity from, Entity to) {
        set(to, get(from));
    }

    /** Whether it accepts its rider this time, or a calf its feeder. */
    public static boolean rolls(Entity whale, RandomSource random) {
        return random.nextInt(MAX) < get(whale);
    }

    /** How much {@code stack} raises the temper of a wild aerwhale it's fed to, or 0 if it isn't taming food. */
    public static int forFood(ItemStack stack) {
        if (TamedAerwhale.isBreedingFood(stack)) {
            return AACommonConfig.AERWHALE_TEMPER_PER_ENCHANTED_BERRY.get();
        }
        return TamedAerwhale.isTamingFood(stack) ? AACommonConfig.AERWHALE_TEMPER_PER_BERRY.get() : 0;
    }

    private AerwhaleTemper() {
    }
}
