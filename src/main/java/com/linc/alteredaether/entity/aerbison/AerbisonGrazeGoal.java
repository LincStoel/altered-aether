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
package com.linc.alteredaether.entity.aerbison;

import com.linc.alteredaether.entity.Aerbison;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Lowers its head to graze, as a sheep does but without touching the grass: the animation, and a calf grows a little
 * from the bite.
 */
public class AerbisonGrazeGoal extends Goal {
    private final Aerbison bison;
    private int ticks;

    public AerbisonGrazeGoal(Aerbison bison) {
        this.bison = bison;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return bison.getRandom().nextInt(bison.isBaby() ? 50 : 1000) == 0
                && bison.getState() == Aerbison.State.IDLE && bison.onGround()
                && bison.getTarget() == null && !bison.isInWaterOrBubble();
    }

    @Override
    public void start() {
        ticks = AerbisonTiming.GRAZE_TICKS;
        bison.getNavigation().stop();
        bison.play(AerbisonTiming.EVENT_GRAZE);
    }

    @Override
    public boolean canContinueToUse() {
        return ticks > 0 && bison.getTarget() == null;
    }

    @Override
    public void tick() {
        ticks--;
        if (ticks == AerbisonTiming.GRAZE_TICKS - AerbisonTiming.GRAZE_BITE_TICK && bison.isBaby()) {
            bison.ageUp(60);
        }
    }
}
