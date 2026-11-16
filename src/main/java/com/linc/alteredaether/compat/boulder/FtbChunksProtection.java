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
package com.linc.alteredaether.compat.boulder;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * FTB Chunks: a boulder never drops on, or settles in, a claimed chunk whose team has turned off mob griefing or
 * explosion damage to terrain, the two settings that say a claim is protected from griefing.
 *
 * <p>Called by reflection, so there is nothing to compile against and an FTB update that moves its API can't stop the
 * game from loading: if its API can't be found, this is never added and boulders ignore claims (with a warning); if a
 * call fails later, the spot is treated as protected.
 */
final class FtbChunksProtection implements BoulderProtection.Check {
    static final String MOD = "ftbchunks";

    private final Method api;
    private final Method isManagerLoaded;
    private final Method getManager;
    private final Method getChunk;
    private final Method getTeamData;
    private final Method allowMobGriefing;
    private final Method canExplosionsDamageTerrain;
    private final Constructor<?> chunkDimPos;
    private boolean warned;

    private FtbChunksProtection() throws ReflectiveOperationException {
        Class<?> apiClass = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI");
        Class<?> apiInterface = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI$API");
        Class<?> managerClass = Class.forName("dev.ftb.mods.ftbchunks.api.ClaimedChunkManager");
        Class<?> chunkClass = Class.forName("dev.ftb.mods.ftbchunks.api.ClaimedChunk");
        Class<?> dataClass = Class.forName("dev.ftb.mods.ftbchunks.api.ChunkTeamData");
        Class<?> posClass = Class.forName("dev.ftb.mods.ftblibrary.math.ChunkDimPos");
        api = apiClass.getMethod("api");
        isManagerLoaded = apiInterface.getMethod("isManagerLoaded");
        getManager = apiInterface.getMethod("getManager");
        getChunk = managerClass.getMethod("getChunk", posClass);
        getTeamData = chunkClass.getMethod("getTeamData");
        allowMobGriefing = dataClass.getMethod("allowMobGriefing");
        canExplosionsDamageTerrain = dataClass.getMethod("canExplosionsDamageTerrain");
        chunkDimPos = posClass.getConstructor(ResourceKey.class, int.class, int.class);
    }

    /** Called from the boulder manager's setup, only when FTB Chunks is loaded. */
    static void register() {
        try {
            BoulderProtection.add(new FtbChunksProtection());
        } catch (ReflectiveOperationException | LinkageError e) {
            AlteredAether.LOGGER.warn("FTB Chunks is loaded but its API wasn't as expected, so falling boulders will not"
                    + " respect its claims: {}", e.toString());
        }
    }

    @Override
    public boolean allowed(ServerLevel level, BlockPos pos) {
        if (!AACommonConfig.compat(MOD)) {
            return true;
        }
        try {
            Object ftb = api.invoke(null);
            if (!(Boolean) isManagerLoaded.invoke(ftb)) {
                return false;
            }
            Object claim = getChunk.invoke(getManager.invoke(ftb), chunkDimPos.newInstance(level.dimension(),
                    pos.getX() >> 4, pos.getZ() >> 4));
            if (claim == null) {
                return true;
            }
            Object team = getTeamData.invoke(claim);
            return (Boolean) allowMobGriefing.invoke(team) && (Boolean) canExplosionsDamageTerrain.invoke(team);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warned) {
                warned = true;
                AlteredAether.LOGGER.warn("Could not ask FTB Chunks about a claim, so treating land as protected: {}",
                        e.toString());
            }
            return false;
        }
    }
}
