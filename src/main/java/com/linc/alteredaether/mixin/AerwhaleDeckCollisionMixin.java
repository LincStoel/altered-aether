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
package com.linc.alteredaether.mixin;

import com.linc.alteredaether.entity.AerwhaleDeck;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Makes sure a moving entity collides with any aerwhale deck in its way.
 *
 * <p>Vanilla finds the deck through {@code Level.getEntities}, which NeoForge extends to multipart pieces. Lithium
 * redirects that lookup in {@code collide} to one that only searches entity sections. Part entities never live in a
 * section, so with Lithium installed players and mobs fell straight through the deck. Adding the slabs to the
 * collision list as it's stored works whichever way the list was built; without Lithium, the slabs are listed twice,
 * which changes nothing.
 */
@Mixin(Entity.class)
public abstract class AerwhaleDeckCollisionMixin {
    @ModifyVariable(method = "collide", at = @At("STORE"), ordinal = 0)
    private List<VoxelShape> altered_aether$collideWithDecks(List<VoxelShape> collisions, Vec3 movement) {
        Entity self = (Entity) (Object) this;
        return AerwhaleDeck.addCollisions(self, self.getBoundingBox().expandTowards(movement), collisions);
    }
}
