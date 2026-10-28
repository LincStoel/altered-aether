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
package com.linc.alteredaether.worldgen.ruin;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.worldgen.LootChest;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The stonework the pilgrims' forebears left in the groves, long fallen: the shells of cottages like a Roman temple on
 * a moor, a broken keep on a hill, a ring of standing stones, and, very rarely, one great stone head sunk to its chin.
 * All holystone, weathered and mossy; nothing in them is lit. Each is drawn as a {@link RuinPlan} round a centre.
 */
public final class Ruins {
    public enum Kind {
        COTTAGE, KEEP, CIRCLE, HEAD;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What a ruin's cellar or a head's cache holds. */
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            AlteredAether.id("chests/grove/ruin"));

    /** The furthest any ruin reaches from its centre, sideways. */
    public static final int REACH = 14;

    public static RuinPlan draw(Kind kind, RandomSource random, int x, int z) {
        return switch (kind) {
            case COTTAGE -> cottage(random, x, z, random.nextDouble(), 0.25);
            case KEEP -> keep(random, x, z);
            case CIRCLE -> circle(random, x, z);
            case HEAD -> head(random, x, z);
        };
    }

    // ------------------------------------------------------------------------------------------------ cottage --

    /**
     * The shell of a cottage: walls broken to a different height all round, a gap where they have fallen right down, a
     * doorway and window gaps, sometimes a gable end still standing. Inside, a trodden floor with the grass coming
     * back, and either the stubs of posts down both sides or a hearth at the far end. {@code decay} from 0 to 1 is
     * how far gone it is; {@code cellar} the odds of a chest buried under its floor.
     */
    public static RuinPlan cottage(RandomSource random, int cx, int cz, double decay, double cellar) {
        Draft d = new Draft(random);
        boolean alongX = random.nextBoolean();
        int w = 7 + random.nextInt(3), l = 9 + random.nextInt(5);
        int wall = 3 + random.nextInt(3);
        boolean gable = random.nextDouble() < 0.4 * (1 - decay);
        int door = l / 2, doorSide = random.nextBoolean() ? 0 : w - 1;
        // Where the wall has come right down: a run of the perimeter.
        int perimeter = 2 * (w + l) - 4;
        int breach = random.nextInt(perimeter), breachLength = 2 + random.nextInt(3) + (int) Math.round(decay * 4);
        double phase = random.nextDouble() * Mth.TWO_PI;
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < l; j++) {
                int x = alongX ? cx - l / 2 + j : cx - w / 2 + i, z = alongX ? cz - w / 2 + i : cz - l / 2 + j;
                boolean edge = i == 0 || i == w - 1 || j == 0 || j == l - 1;
                if (!edge) {
                    // The floor, trodden bare in places and grown over in others.
                    double r = d.hash(x, 0, z);
                    if (r < 0.4) {
                        d.put(x, 0, z, d.block("aether:aether_dirt_path", Blocks.DIRT_PATH));
                    } else if (r < 0.55) {
                        d.put(x, 0, z, d.block("aether:aether_dirt", Blocks.DIRT));
                    }
                    continue;
                }
                int t = around(i, j, w, l);
                int gap = Math.floorMod(t - breach, perimeter);
                double profile = 0.55 + 0.3 * Math.sin(t * 0.55 + phase) + 0.25 * Math.sin(t * 1.7 + phase * 2);
                int h = (int) Math.round(wall * Mth.clamp(1 - decay * 0.6 + (profile - 0.55) * (0.5 + decay), 0.2, 1.2));
                if (gap < breachLength) {
                    h = gap == 0 || gap == breachLength - 1 ? 1 : 0;
                }
                boolean corner = (i == 0 || i == w - 1) && (j == 0 || j == l - 1);
                if (corner) {
                    h = Math.max(h, wall - 1);
                }
                if (gable && j == 0 && h >= wall - 1) {
                    h = wall + Math.min(i, w - 1 - i);
                }
                boolean doorway = i == doorSide && (j == door || j == door + 1);
                for (int dy = 0; dy <= h; dy++) {
                    if (dy > 0 && doorway) {
                        break;
                    }
                    // Window gaps along the long walls.
                    if (dy == 2 && h >= 3 && (i == 0 || i == w - 1) && j % 3 == 1 && j > 1 && j < l - 2
                            && !doorway) {
                        continue;
                    }
                    d.put(x, dy, z, d.wallStone(x, dy, z));
                }
                if (h >= 2 && d.hash(x, 9, z) < 0.25) {
                    d.put(x, h + 1, z, d.slab(x, h + 1, z));
                }
            }
        }
        // Inside: two rows of post stubs, as down the nave of a temple, or a hearth against the far wall.
        if (random.nextBoolean()) {
            for (int j = 2; j < l - 2; j += 2) {
                for (int i : new int[]{2, w - 3}) {
                    if (random.nextDouble() < 0.65) {
                        int x = alongX ? cx - l / 2 + j : cx - w / 2 + i, z = alongX ? cz - w / 2 + i : cz - l / 2 + j;
                        int tall = 1 + (random.nextDouble() < 0.4 ? 1 : 0);
                        for (int dy = 1; dy <= tall; dy++) {
                            d.put(x, dy, z, d.block("aether:skyroot_fence", Blocks.OAK_FENCE));
                        }
                    }
                }
            }
        } else {
            int j = l - 2;
            for (int i = w / 2 - 1; i <= w / 2 + 1; i++) {
                int x = alongX ? cx - l / 2 + j : cx - w / 2 + i, z = alongX ? cz - w / 2 + i : cz - l / 2 + j;
                d.put(x, 1, z, i == w / 2 ? d.slab(x, 1, z) : d.block("aether:holystone_bricks", Blocks.STONE_BRICKS));
            }
        }
        // Stones fallen out of the walls.
        int fallen = 3 + random.nextInt(5);
        for (int k = 0; k < fallen; k++) {
            double a = random.nextDouble() * Mth.TWO_PI, r = Math.max(w, l) / 2.0 + random.nextDouble() * 3;
            int x = cx + (int) Math.round(Math.cos(a) * r), z = cz + (int) Math.round(Math.sin(a) * r);
            d.put(x, 1, z, random.nextDouble() < 0.5 ? d.slab(x, 1, z) : d.wallStone(x, 1, z));
        }
        if (random.nextDouble() < cellar) {
            int i = 1 + random.nextInt(w - 2), j = 1 + random.nextInt(l - 2);
            int x = alongX ? cx - l / 2 + j : cx - w / 2 + i, z = alongX ? cz - w / 2 + i : cz - l / 2 + j;
            d.chest(x, -1, z, x, z, Direction.Plane.HORIZONTAL.getRandomDirection(random));
        }
        return d.done("cottage");
    }

    /** A wall column's place round a w by l rectangle's edge, counted from a corner. */
    private static int around(int i, int j, int w, int l) {
        if (i == 0) {
            return j;
        }
        if (j == l - 1) {
            return l - 1 + i;
        }
        if (i == w - 1) {
            return l - 1 + w - 1 + (l - 1 - j);
        }
        return 2 * (l - 1) + w - 1 + (w - 1 - i);
    }

    // ---------------------------------------------------------------------------------------------------- keep --

    /**
     * What is left of a keep: one tower standing far above everything round it, its top jagged and one face broken
     * away in a long diagonal, slits up its faces; stretches of curtain wall running off from it, crumbling lower as
     * they go; and the fallen stone heaped on the broken side.
     */
    public static RuinPlan keep(RandomSource random, int cx, int cz) {
        Draft d = new Draft(random);
        int s = 6 + random.nextInt(2);
        int tall = 10 + random.nextInt(7);
        int x0 = cx - s / 2, z0 = cz - s / 2;
        int broken = random.nextInt(4);
        for (int i = 0; i < s; i++) {
            for (int j = 0; j < s; j++) {
                boolean edge = i == 0 || i == s - 1 || j == 0 || j == s - 1;
                int x = x0 + i, z = z0 + j;
                if (!edge) {
                    if (d.hash(x, 1, z) < 0.3) {
                        d.put(x, 1, z, d.wallStone(x, 1, z));
                    }
                    continue;
                }
                int face = j == 0 ? 0 : i == s - 1 ? 1 : j == s - 1 ? 2 : 3;
                int along = face == 0 || face == 2 ? i : j;
                int h = tall - (int) (d.hash(x, 3, z) * 3);
                if (face == broken) {
                    // Broken away in a long diagonal down this face.
                    h = Math.max(1, (int) Math.round(tall * (0.2 + 0.6 * along / (double) (s - 1))));
                }
                boolean slit = along == s / 2;
                for (int dy = 0; dy <= h; dy++) {
                    if (slit && dy > 2 && dy % 4 == 0) {
                        continue;
                    }
                    d.put(x, dy, z, d.wallStone(x, dy, z));
                }
            }
        }
        // Curtain walls from one or two corners.
        int runs = 1 + random.nextInt(2);
        for (int k = 0; k < runs; k++) {
            Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            int sx = dir.getStepX() > 0 ? x0 + s - 1 : x0, sz = dir.getStepZ() > 0 ? z0 + s - 1 : z0;
            if (random.nextBoolean()) {
                sx = dir.getStepX() == 0 ? (random.nextBoolean() ? x0 : x0 + s - 1) : sx;
                sz = dir.getStepZ() == 0 ? (random.nextBoolean() ? z0 : z0 + s - 1) : sz;
            }
            int length = 6 + random.nextInt(6);
            int start = 3 + random.nextInt(3);
            for (int n = 1; n <= length; n++) {
                int x = sx + dir.getStepX() * n, z = sz + dir.getStepZ() * n;
                int h = (int) Math.round(start * (1 - n / (double) (length + 1)) + (d.hash(x, 4, z) - 0.5) * 2);
                if (d.hash(x, 5, z) < 0.12) {
                    h = 0;
                }
                for (int dy = 0; dy <= h; dy++) {
                    d.put(x, dy, z, d.wallStone(x, dy, z));
                }
            }
        }
        // The fallen stone, heaped on the broken side.
        Direction out = Direction.from2DDataValue(new int[]{2, 3, 0, 1}[broken]);
        int heap = 6 + random.nextInt(6);
        for (int k = 0; k < heap; k++) {
            int reach = s / 2 + 1 + random.nextInt(4);
            int side = random.nextInt(s) - s / 2;
            int x = cx + out.getStepX() * reach + (out.getStepX() == 0 ? side : 0);
            int z = cz + out.getStepZ() * reach + (out.getStepZ() == 0 ? side : 0);
            d.put(x, 1, z, random.nextDouble() < 0.4 ? d.slab(x, 1, z) : d.wallStone(x, 1, z));
        }
        return d.done("keep");
    }

    // -------------------------------------------------------------------------------------------------- circle --

    /**
     * A ring of standing stones, as at Stonehenge: sarsens round a circle, some still joined by their lintels, some
     * fallen outward, some gone; within, a horseshoe of great trilithons opening one way, and a fallen altar stone at
     * the heart. Each stone stands on the ground at its own centre, so it stays whole on a slope.
     */
    public static RuinPlan circle(RandomSource random, int cx, int cz) {
        Draft d = new Draft(random);
        double radius = 8 + random.nextDouble() * 2.5;
        int count = (int) Math.round(Mth.TWO_PI * radius / 3.3);
        double phase = random.nextDouble() * Mth.TWO_PI;
        int[] heights = new int[count];
        int[] state = new int[count]; // 0 standing, 1 fallen, 2 gone
        for (int k = 0; k < count; k++) {
            double r = random.nextDouble();
            state[k] = r < 0.15 ? 2 : r < 0.28 ? 1 : 0;
            heights[k] = 4 + random.nextInt(2);
        }
        // Lintels across neighbouring standing stones, the pair levelled to carry it.
        boolean[] lintel = new boolean[count];
        for (int k = 0; k < count; k++) {
            int n = (k + 1) % count;
            if (state[k] == 0 && state[n] == 0 && random.nextDouble() < 0.4 && !lintel[(k + count - 1) % count]) {
                lintel[k] = true;
                heights[n] = heights[k];
            }
        }
        for (int k = 0; k < count; k++) {
            double a = phase + k * Mth.TWO_PI / count;
            double px = cx + Math.cos(a) * radius, pz = cz + Math.sin(a) * radius;
            int ax = Mth.floor(px), az = Mth.floor(pz);
            double tx = -Math.sin(a), tz = Math.cos(a);
            if (state[k] == 2) {
                // A stump of it, or nothing.
                if (random.nextBoolean()) {
                    d.put(ax, 1, az, ax, az, d.megalith(ax, 1, az));
                }
                continue;
            }
            if (state[k] == 1) {
                // Fallen outward, lying on the turf, as much of it as shows above the grass.
                int length = Math.min(heights[k], REACH - 2 - (int) Math.ceil(radius));
                for (int n = 0; n < length; n++) {
                    for (int side = 0; side <= 1; side++) {
                        int x = Mth.floor(px + Math.cos(a) * (n + 1) + tx * side);
                        int z = Mth.floor(pz + Math.sin(a) * (n + 1) + tz * side);
                        d.put(x, 1, z, ax, az, d.megalith(x, 1, z));
                    }
                }
                continue;
            }
            for (int side = 0; side <= 1; side++) {
                int x = Mth.floor(px + tx * side), z = Mth.floor(pz + tz * side);
                for (int dy = 0; dy <= heights[k]; dy++) {
                    d.put(x, dy, z, ax, az, d.megalith(x, dy, z));
                }
            }
            if (lintel[k]) {
                int n = (k + 1) % count;
                double b = phase + n * Mth.TWO_PI / count;
                double qx = cx + Math.cos(b) * radius + tx, qz = cz + Math.sin(b) * radius + tz;
                for (double t = 0; t <= 1.0; t += 0.2) {
                    int x = Mth.floor(Mth.lerp(t, px, qx)), z = Mth.floor(Mth.lerp(t, pz, qz));
                    d.put(x, heights[k] + 1, z, ax, az, d.megalith(x, heights[k] + 1, z));
                }
            }
        }
        // The horseshoe of trilithons, open toward one side.
        int trilithons = 3 + random.nextInt(3);
        double open = random.nextDouble() * Mth.TWO_PI;
        for (int k = 0; k < trilithons; k++) {
            double a = open + Math.PI / 3 + k * (4 * Math.PI / 3) / Math.max(1, trilithons - 1);
            double px = cx + Math.cos(a) * radius * 0.45, pz = cz + Math.sin(a) * radius * 0.45;
            int ax = Mth.floor(px), az = Mth.floor(pz);
            double tx = -Math.sin(a), tz = Math.cos(a);
            int h = 5 + random.nextInt(2);
            boolean standing = random.nextDouble() < 0.8;
            for (int side = -1; side <= 1; side++) {
                int x = Mth.floor(px + tx * side * 1.2), z = Mth.floor(pz + tz * side * 1.2);
                if (side != 0) {
                    for (int dy = 0; dy <= (standing ? h : 2); dy++) {
                        d.put(x, dy, z, ax, az, d.megalith(x, dy, z));
                    }
                }
                if (standing) {
                    d.put(x, h + 1, z, ax, az, d.megalith(x, h + 1, z));
                }
            }
        }
        // The altar stone, fallen flat at the heart.
        double a = random.nextDouble() * Mth.TWO_PI;
        for (int n = -1; n <= 1; n++) {
            int x = Mth.floor(cx + Math.cos(a) * n), z = Mth.floor(cz + Math.sin(a) * n);
            d.put(x, 1, z, cx, cz, d.megalith(x, 1, z));
        }
        return d.done("circle");
    }

    // ---------------------------------------------------------------------------------------------------- head --

    /**
     * One great stone head, sunk to its chin in the turf: heavy brow over deep sockets, a long straight nose, thin
     * pressed lips, a jutting chin and long ears, leaning back a little as if the ground had settled under it. Moss
     * creeps over it and grass has taken its crown. A small cache lies buried under its chin.
     */
    public static RuinPlan head(RandomSource random, int cx, int cz) {
        return head(random, cx, cz, Direction.Plane.HORIZONTAL.getRandomDirection(random));
    }

    /** One great stone head gazing toward {@code facing}, so a row of them can all look the same way. */
    public static RuinPlan head(RandomSource random, int cx, int cz, Direction facing) {
        Draft d = new Draft(random);
        int tall = 12 + random.nextInt(3);
        Direction right = facing.getClockWise();
        double lean = random.nextDouble() * Math.tan(15 * Mth.DEG_TO_RAD);
        int brow0 = (int) Math.round(tall * 0.62), brow1 = (int) Math.round(tall * 0.70);
        int eye0 = (int) Math.round(tall * 0.52), eye1 = brow0 - 1;
        int nose0 = (int) Math.round(tall * 0.36), tip1 = (int) Math.round(tall * 0.45);
        int lip0 = (int) Math.round(tall * 0.2), lip1 = (int) Math.round(tall * 0.28), mouth = (lip0 + lip1) / 2;
        int chin1 = (int) Math.round(tall * 0.12);
        int ear0 = (int) Math.round(tall * 0.3), ear1 = (int) Math.round(tall * 0.68);
        // Local frame: u across the face, w back from it, v up from the chin.
        for (int v = -3; v <= tall; v++) {
            int shift = (int) Math.round(Math.max(0, v) * lean);
            for (int u = -4; u <= 4; u++) {
                for (int w = -2; w <= 6; w++) {
                    if (!head(u, v, w, tall, brow0, brow1, eye0, eye1, nose0, tip1, lip0, lip1, mouth, chin1, ear0,
                            ear1)) {
                        continue;
                    }
                    int ww = w + shift - 3;
                    int x = cx + right.getStepX() * u - facing.getStepX() * ww;
                    int z = cz + right.getStepZ() * u - facing.getStepZ() * ww;
                    boolean crown = !head(u, v + 1, w, tall, brow0, brow1, eye0, eye1, nose0, tip1, lip0, lip1, mouth,
                            chin1, ear0, ear1);
                    BlockState stone = crown && v == tall && d.hash(x, v, z) < 0.65
                            ? d.block("aether:aether_grass_block", Blocks.GRASS_BLOCK)
                            : d.hash(x, v + 40, z) < (v >= tall - 1 || v <= 1 ? 0.6 : 0.25)
                            ? d.block("aether:mossy_holystone", Blocks.MOSSY_COBBLESTONE)
                            : d.block("aether:holystone", Blocks.STONE);
                    d.put(x, v, z, cx, cz, stone);
                }
            }
        }
        int x = cx + facing.getStepX() * 4, z = cz + facing.getStepZ() * 4;
        d.chest(x, -2, z, cx, cz, facing);
        return d.done("head");
    }

    private static boolean head(int u, int v, int w, int tall, int brow0, int brow1, int eye0, int eye1, int nose0,
                                int tip1, int lip0, int lip1, int mouth, int chin1, int ear0, int ear1) {
        int au = Math.abs(u);
        if (v < -3 || v > tall) {
            return false;
        }
        if (v < 0) {
            // Neck and shoulders, under the ground.
            return au <= 3 && w >= 0 && w <= 6;
        }
        if (au == 4) {
            return v >= ear0 && v <= ear1 && (w == 2 || w == 3);
        }
        if (w < 0) {
            if (w == -2) {
                return u == 0 && v >= nose0 && v <= tip1;
            }
            if (v >= brow0 && v <= brow1) {
                return true;
            }
            if (u == 0 && v >= nose0 && v < brow0) {
                return true;
            }
            if (au <= 2 && v >= lip0 && v <= lip1 && v != mouth) {
                return true;
            }
            return au <= 2 && v <= chin1;
        }
        if (w == 0 && v >= eye0 && v <= eye1 && (au == 1 || au == 2)) {
            return false;
        }
        if (w >= 5 && au == 3 || w == 6 && au >= 2) {
            return false;
        }
        return v < tall || au < 3 && w > 0 && w < 6;
    }

    // --------------------------------------------------------------------------------------------------- draft --

    /** A ruin being drawn: its pieces, and the weathered stone it is drawn in. */
    private static final class Draft {
        final RandomSource random;
        final long salt;
        final List<RuinPlan.Piece> pieces = new ArrayList<>();

        Draft(RandomSource random) {
            this.random = random;
            this.salt = random.nextLong();
        }

        void put(int x, int dy, int z, BlockState state) {
            put(x, dy, z, x, z, state);
        }

        void put(int x, int dy, int z, int ax, int az, BlockState state) {
            pieces.add(new RuinPlan.Piece(x, dy, z, ax, az, state, null));
        }

        void chest(int x, int dy, int z, int ax, int az, Direction facing) {
            pieces.add(new RuinPlan.Piece(x, dy, z, ax, az,
                    LootChest.facing(facing), LOOT));
        }

        double hash(int x, int y, int z) {
            long h = mix(salt ^ mix(x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z));
            return (h >>> 11) * 0x1.0p-53;
        }

        /** Dressed stone, the bricks mossy low down and long since patched with rough holystone. */
        BlockState wallStone(int x, int dy, int z) {
            double r = hash(x, dy, z);
            double moss = dy <= 1 ? 0.45 : 0.25;
            if (r < moss) {
                return block("aether:mossy_holystone", Blocks.MOSSY_COBBLESTONE);
            }
            if (r < moss + 0.45) {
                return block("aether:holystone_bricks", Blocks.STONE_BRICKS);
            }
            return block("aether:holystone", Blocks.STONE);
        }

        BlockState slab(int x, int dy, int z) {
            BlockState slab = hash(x, dy + 17, z) < 0.5 ? block("aether:mossy_holystone_slab", Blocks.MOSSY_COBBLESTONE_SLAB)
                    : block("aether:holystone_brick_slab", Blocks.STONE_BRICK_SLAB);
            return slab.hasProperty(SlabBlock.TYPE) ? slab.setValue(SlabBlock.TYPE, SlabType.BOTTOM) : slab;
        }

        /** Rough unworked stone for the standing stones. */
        BlockState megalith(int x, int dy, int z) {
            return hash(x, dy + 31, z) < 0.45 ? block("aether:mossy_holystone", Blocks.MOSSY_COBBLESTONE)
                    : block("aether:holystone", Blocks.STONE);
        }

        BlockState block(String id, Block fallback) {
            return Ruins.block(id, fallback);
        }

        RuinPlan done(String kind) {
            return new RuinPlan(kind, pieces, salt);
        }
    }

    static BlockState block(String id, Block fallback) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElse(fallback).defaultBlockState();
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Nullable
    public static Kind kind(String id) {
        for (Kind kind : Kind.values()) {
            if (kind.id().equals(id)) {
                return kind;
            }
        }
        return null;
    }

    private Ruins() {
    }
}
