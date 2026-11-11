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
package com.linc.alteredaether.entity.golem.sort;

/**
 * {@code count} of item {@code key} from slot {@code srcSlot} of inventory {@code srcInv} into slot {@code dstSlot} of
 * {@code dstInv}. Inventories are indexes into {@link SortSnapshot#invs()}.
 */
public record Move(int srcInv, int srcSlot, int dstInv, int dstSlot, int key, int count) {
}
