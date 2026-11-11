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
package com.linc.alteredaether.client.golem;

import com.linc.alteredaether.entity.golem.GravititeGolem;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** The hum of a golem concentrating, following it, fading in and out with its concentration. */
final class GolemHumSound extends AbstractTickableSoundInstance {
    private final GravititeGolem golem;

    GolemHumSound(GravititeGolem golem) {
        super(AARegistries.GOLEM_HUM.get(), SoundSource.NEUTRAL, RandomSource.create());
        this.golem = golem;
        looping = true;
        delay = 0;
        volume = 0.01F;
        x = golem.getX();
        y = golem.getY();
        z = golem.getZ();
    }

    @Override
    public void tick() {
        if (golem.isRemoved()) {
            stop();
            return;
        }
        x = golem.getX();
        y = golem.getY() + 0.8;
        z = golem.getZ();
        volume = Math.max(0.01F, 0.45F * golem.concentrate);
        if (golem.concentrate <= 0.01F && golem.state() != GravititeGolem.CONCENTRATING) {
            stop();
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    GravititeGolem golem() {
        return golem;
    }
}
