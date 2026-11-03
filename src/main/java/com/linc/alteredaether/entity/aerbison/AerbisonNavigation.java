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
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Ground navigation that plans as if the bison could climb {@link Aerbison#STOMP_REACH} blocks: the path may route up a
 * ledge too tall to step or jump, and {@link GustStompGoal} throws the bison up it when it gets there. Everything else
 * about planning is the ground navigation's own, so a step of one block is simply walked.
 */
public class AerbisonNavigation extends GroundPathNavigation {
    private final Aerbison bison;

    public AerbisonNavigation(Aerbison bison, Level level) {
        super(bison, level);
        this.bison = bison;
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        nodeEvaluator = new Evaluator();
        nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }

    private final class Evaluator extends WalkNodeEvaluator {
        @Override
        public int getNeighbors(Node[] neighbors, Node node) {
            bison.planningClimb = true;
            try {
                return super.getNeighbors(neighbors, node);
            } finally {
                bison.planningClimb = false;
            }
        }
    }
}
