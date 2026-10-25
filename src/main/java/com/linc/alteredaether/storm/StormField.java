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
package com.linc.alteredaether.storm;

import com.linc.alteredaether.config.AACommonConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Where the storms are: an intensity from 0 (calm) to 1 (the heart of a storm) over x, z and time. Storm cells are
 * blobs of noise about {@code field_scale} blocks across that drift downwind at {@code drift_speed}, and the whole
 * sky swells and slackens slowly, so a peak that was quiet a minute ago can be under a storm now. Seeded from the
 * world, so every player sees the same storms.
 */
public final class StormField {
    private final SimplexNoise cells;
    private final SimplexNoise swell;
    private final double windX, windZ;

    public StormField(long seed) {
        XoroshiroRandomSource random = new XoroshiroRandomSource(seed ^ 0x7E3D5A1BC9F0L);
        this.cells = new SimplexNoise(random);
        this.swell = new SimplexNoise(random);
        double wind = random.nextDouble() * Mth.TWO_PI;
        this.windX = Math.cos(wind);
        this.windZ = Math.sin(wind);
    }

    public double intensity(double x, double z, long gameTime) {
        double scale = AACommonConfig.STORM_FIELD_SCALE.get();
        double drift = AACommonConfig.STORM_DRIFT_SPEED.get() * gameTime / 20.0;
        double nx = (x - windX * drift) / scale, nz = (z - windZ * drift) / scale;
        double n = 0.7 * cells.getValue(nx, nz) + 0.3 * cells.getValue(nx * 2.3 + 17.1, nz * 2.3 - 5.3);
        // The slow swell of the whole sky, over about a quarter of an hour.
        n += 0.25 * swell.getValue(gameTime / 20.0 / 900.0, 3.7);
        return Mth.clamp((n + 0.15) / 0.75, 0, 1);
    }
}
