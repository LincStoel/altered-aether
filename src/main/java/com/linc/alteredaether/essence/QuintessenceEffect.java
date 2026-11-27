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

import com.linc.alteredaether.config.AACommonConfig;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Quintessence, from a Quintessence Phial: an essence circle's blessing carried away. It heals a heart and mends what
 * the drinker holds as a Channel's circle does; at amplifier 1 (a Wellspring's phial) twice as often.
 */
public class QuintessenceEffect extends MobEffect {
    public QuintessenceEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xF4F1FF);
    }

    private static int interval(int amplifier) {
        return Math.max(1, AACommonConfig.CHANNEL_REGEN_TICKS.get() >> Math.min(amplifier, 4));
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % interval(amplifier) == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(1.0F);
        }
        if (entity instanceof Player player) {
            EssenceOrbBlockEntity.mend(player, 1);
        }
        return true;
    }
}
