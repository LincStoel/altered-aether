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
 * Lies down to rest for twenty to sixty seconds, sometimes rolling in the dirt, and gets up at once when hurt or when
 * something else takes over (a stampede sets it running).
 */
public class AerbisonRestGoal extends Goal {
    private final Aerbison bison;
    private int remaining;
    private int wallowAt;

    public AerbisonRestGoal(Aerbison bison) {
        this.bison = bison;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return bison.getRandom().nextInt(900) == 0 && bison.getState() == Aerbison.State.IDLE && bison.onGround()
                && bison.getTarget() == null && !bison.isInWaterOrBubble() && bison.getNavigation().isDone();
    }

    @Override
    public void start() {
        bison.getNavigation().stop();
        bison.setState(Aerbison.State.LYING);
        remaining = 400 + bison.getRandom().nextInt(800);
        wallowAt = bison.getRandom().nextInt(3) == 0 ? 80 + bison.getRandom().nextInt(remaining - 160) : -1;
    }

    @Override
    public boolean canContinueToUse() {
        return remaining > 0 && bison.getState() == Aerbison.State.LYING && bison.getTarget() == null
                && bison.hurtTime == 0 && bison.getLastHurtByMob() == null;
    }

    @Override
    public void tick() {
        remaining--;
        if (wallowAt >= 0 && remaining == wallowAt) {
            bison.play(AerbisonTiming.EVENT_WALLOW);
        }
    }

    @Override
    public void stop() {
        if (bison.getState() == Aerbison.State.LYING) {
            bison.setState(Aerbison.State.IDLE);
        }
    }
}
