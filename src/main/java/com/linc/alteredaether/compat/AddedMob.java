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

import java.util.List;

/**
 * A mob from another mod (or vanilla) that this mod brings into the Aether.
 *
 * @param key              config key, also the entity's path
 * @param entityId         registry id of the entity type
 * @param method           how it reaches the Aether
 * @param exclusiveDefault default for the "aether_exclusive" toggle, which strips its natural spawns everywhere else
 * @param charge           default spawn-cost charge, or 0 for none
 * @param energyBudget     default spawn-cost energy budget, or 0 for none
 */
public record AddedMob(String key, String entityId, Method method, boolean exclusiveDefault,
                       double charge, double energyBudget) {

    public enum Method {
        /** Through biome spawn lists, so the mob's own spawn rules apply. */
        NATURAL,
        /**
         * Spawns naturally through an Aether-only spawn rule (their own rule tests for minecraft:grass_block by
         * identity, which no Aether soil is), and is also placed at chunk generation by our spawn feature, so the
         * first player to find a grove sees it populated while later visitors still meet them.
         */
        NATURAL_AND_GENERATION
    }

    public String modId() {
        return entityId.substring(0, entityId.indexOf(':'));
    }

    public static final List<AddedMob> ALL = List.of(
            natural("naga", "mowziesmobs:naga", true, 0.5, 0.05),
            natural("lantern", "mowziesmobs:lantern", true, 0.5, 0.05),
            natural("wilden_hunter", "ars_nouveau:wilden_hunter", true, 0.7, 0.08),
            natural("wilden_guardian", "ars_nouveau:wilden_guardian", true, 0.7, 0.08),
            natural("wilden_stalker", "ars_nouveau:wilden_stalker", true, 0.7, 0.08),
            natural("snuffle", "snuffles:snuffle", true, 0, 0),
            // The only mob that keeps its vanilla spawns by default: breezes belong to trial chambers too.
            natural("breeze", "minecraft:breeze", false, 0.5, 0.05),
            familiar("starbuncle", "ars_nouveau:starbuncle"),
            familiar("drygmy", "ars_nouveau:drygmy"),
            familiar("whirlisprig", "ars_nouveau:whirlisprig"),
            familiar("alakarkinos", "ars_nouveau:alakarkinos"),
            familiar("blazing_weald_walker", "ars_nouveau:blazing_weald_walker"),
            familiar("cascading_weald_walker", "ars_nouveau:cascading_weald_walker"),
            familiar("flourishing_weald_walker", "ars_nouveau:flourishing_weald_walker"),
            familiar("vexing_weald_walker", "ars_nouveau:vexing_weald_walker"),
            familiar("flashing_weald_walker", "ars_elemental:flashing_weald_walker"),
            familiar("flashjack", "ars_elemental:flashjack_entity"),
            familiar("siren", "ars_elemental:siren_entity"));

    private static AddedMob natural(String key, String id, boolean exclusive, double charge, double budget) {
        return new AddedMob(key, id, Method.NATURAL, exclusive, charge, budget);
    }

    private static AddedMob familiar(String key, String id) {
        return new AddedMob(key, id, Method.NATURAL_AND_GENERATION, true, 0, 0);
    }
}
