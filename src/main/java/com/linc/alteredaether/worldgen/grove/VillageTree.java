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
package com.linc.alteredaether.worldgen.grove;

import com.linc.alteredaether.worldgen.TerrainProbe;
import com.linc.alteredaether.worldgen.grove.shape.Blob;
import com.linc.alteredaether.worldgen.grove.shape.Carve;
import com.linc.alteredaether.worldgen.grove.shape.Part;
import com.linc.alteredaether.worldgen.grove.shape.Trunk;
import com.linc.alteredaether.worldgen.grove.shape.Tube;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * The deserted village tree: a squat skyroot, wider than it is tall, shaped like a turnip, its people long gone. Its
 * roots flare out into a broad foot; at about ten blocks up a street runs right round it, cut back into the trunk under
 * the overhang of the bulb above, with a railing along the ledge at its edge. Off the street, rounded houses have been
 * grown into the heartwood, each doorway swelling out in a burl as if the tree had healed round it, and two passages
 * lead in to an atrium at the core, a tall dome where the village ate at long tables. A ramp winds up inside the wall
 * to a second, narrower gallery, a few more houses and a balcony over the atrium, and from there a ladder climbs to
 * the top of the bulb, where the villagers' fields lie overgrown round its neck. Over it all a low canopy spreads
 * ninety blocks wide. Half the time it stands in a broad swamp.
 * <p>
 * Everything the villagers left is sparse: a bed here, a workstation there, crates of junk, lanterns hanging dark over
 * the street. Now and then a nitwit still lives in one of the houses, the only one who never left.
 */
public final class VillageTree {
    /** Draws the village tree standing on {@code ground} at (x, z). */
    public static Giant sketch(TerrainProbe probe, RandomSource random, double vertical, int buildTop, int x, int z,
                               int ground, boolean nitwit, boolean mire) {
        Sketch s = new Sketch(probe, random, vertical, buildTop, x, z, ground);
        s.arching = true;
        int base = ground;
        double radius = s.range(25.0, 27.0);
        int street = base + Math.max(8, (int) Math.round(10 * vertical));
        int upper = street + 11;
        int top = Math.max(upper + 16, s.rise(base, s.range(44, 50)));
        double phase = random.nextDouble() * Mth.TWO_PI;

        // The broad foot, its roots flaring out over the ground, and the bulb above the street, narrowing to a short
        // neck under the crown. The bulb's flare at its foot is the overhang that roofs the street.
        Trunk foot = s.trunk(new Trunk(x, z, base, street - 1, radius, 1.0, 1.0, 0, 0, 12, 0.05, phase, 0.15, 0.3,
                0.7, 18, Part.Role.LOG));
        Trunk bulb = s.trunk(new Trunk(x, z, street - 1, top, radius, 0.3, 1.5, 0, 0, 12, 0.05, phase, 0.15, 0.1,
                0.2, 0, Part.Role.LOG));
        s.footRadius = radius * 1.3;
        s.buttresses(x, z, radius * 1.05, 11, s.range(12, 16));

        // The crown: low and very wide, one great dome from the neck and six lesser ones leaning far out from the
        // shoulders.
        s.crown(x, top, z, s.range(38, 44), 3.4, 0, 0);
        for (int i = 0; i < 6; i++) {
            double a = phase + i * Mth.TWO_PI / 6 + s.range(-0.25, 0.25);
            double cos = Math.cos(a), sin = Math.sin(a);
            double r = bulb.radiusAt(top - 8) * 0.8;
            s.crown(x + cos * r, top - 8, z + sin * r, s.range(26, 31), 2.4, cos * 1.1, sin * 1.1);
        }

        // The street, cut back into the trunk all round, and the gallery above; cut once the burls round the
        // doorways are known, which they spare.
        double inner = radius - 8;
        double upperRadius = bulb.radiusAt(upper);
        double upperInner = upperRadius - 6;
        List<double[]> burls = new ArrayList<>(), upperBurls = new ArrayList<>();
        s.point("street", x + Math.cos(phase) * (inner + 4), street, z + Math.sin(phase) * (inner + 4));

        // Round the street: slots for houses, the ramp and the two passages to the hall.
        int houses = s.range(6, 8);
        int slots = houses + 4;
        double[] angles = new double[slots];
        for (int i = 0; i < slots; i++) {
            angles[i] = phase + (i + 0.5) * Mth.TWO_PI / slots + s.range(-0.08, 0.08);
        }
        // The ramp takes two slots, for room to climb.
        int passage1 = slots / 3 + 1, passage2 = 2 * slots / 3 + 1;
        double houseRing = inner - 4.5;
        List<double[]> homes = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            if (i <= 1 || i == passage1 || i == passage2) {
                continue;
            }
            homes.add(house(s, angles[i], houseRing, inner, street, burls));
        }

        // The atrium at the core, where the village ate together: a tall dome rising through both storeys, the
        // village's altar at its head.
        double hall = Math.min(8, houseRing - 3.3 - 2.0);
        int dome = upper + 4 - (street + 2);
        s.carve(Carve.chamber(x, street + 2, z, hall, dome, hall, street));
        for (int i : new int[]{passage1, passage2}) {
            double a = angles[i];
            s.carve(Carve.arch(x, z, x + Math.cos(a) * (inner + 1), z + Math.sin(a) * (inner + 1), street, 1.3, 3.6));
        }
        atrium(s, street, hall, dome, angles[passage1] + Math.PI, new double[]{angles[passage1], angles[passage2]});
        s.point("hall", x, street, z);

        // The ramp, winding up inside the wall from the street to the gallery.
        double rampRadius = inner - 2;
        double a0 = angles[0] - 0.2, a1 = angles[1] + 0.2;
        int climb = upper - street;
        s.carve(Carve.arch(x + Math.cos(a0) * (inner + 1), z + Math.sin(a0) * (inner + 1), x + Math.cos(a0) * rampRadius,
                z + Math.sin(a0) * rampRadius, street, 1.2, 3.4));
        for (int k = 0; k < climb; k++) {
            double ta = Mth.lerp(k / (double) climb, a0, a1), tb = Mth.lerp((k + 1.5) / climb, a0, a1);
            s.carve(Carve.arch(x + Math.cos(ta) * rampRadius, z + Math.sin(ta) * rampRadius,
                    x + Math.cos(tb) * rampRadius, z + Math.sin(tb) * rampRadius, street + k, 1.2, 3.4));
        }
        s.carve(Carve.arch(x + Math.cos(a1) * rampRadius, z + Math.sin(a1) * rampRadius,
                x + Math.cos(a1) * (upperInner + 1.5), z + Math.sin(a1) * (upperInner + 1.5), upper, 1.2, 3.4));

        // A few houses on the gallery, away from where the ramp comes up.
        int high = s.range(2, 3);
        double step = (Mth.TWO_PI - 0.9) / (high + 1);
        for (int i = 0; i < high; i++) {
            double a = a1 + (i + 1) * step;
            homes.add(house(s, a, upperInner - 3.5, upperInner, upper, upperBurls));
        }
        // Between the ramp and the first of them, a balcony looks out over the atrium.
        balcony(s, a1 + step / 2, upperInner, upper, street, hall, dome);
        // Between the first two, a ladder climbs a shaft to the top of the bulb, where the fields were.
        fields(s, bulb, a1 + step * 1.5, upperInner, upper);
        s.carve(Carve.ring(x, z, inner, radius + 8, street, 5, burls));
        s.carve(Carve.ring(x, z, upperInner, radius + 8, upper, 4.5, upperBurls));

        // The ways up from the ground: stairs climbing through the foot to the street's edge.
        int stairs = s.range(2, 3);
        List<Double> ways = new ArrayList<>();
        List<double[]> feet = new ArrayList<>();
        for (int i = 0; i < stairs; i++) {
            double a = angles[i == 0 ? passage1 : i == 1 ? passage2 : slots - 1] + Math.PI / slots;
            ways.add(a);
            double cos = Math.cos(a), sin = Math.sin(a);
            int rise = street - base;
            double from = radius + 13, to = radius - 2;
            for (int k = 0; k < rise; k++) {
                double ra = Mth.lerp(k / (double) rise, from, to), rb = Mth.lerp((k + 1.5) / rise, from, to);
                s.carve(Carve.arch(x + cos * ra, z + sin * ra, x + cos * rb, z + sin * rb, base + 1 + k, 1.4, 3.8));
            }
            s.point("entrance", x + cos * from, base + 1, z + sin * from);
            feet.add(new double[]{x + cos * from, z + sin * from});
        }

        // The railing along the ledge, with gaps where it has fallen and where the stairs come up.
        int posts = (int) Math.round(Mth.TWO_PI * radius);
        for (int i = 0; i < posts; i++) {
            double a = phase + i * Mth.TWO_PI / posts;
            boolean stair = false;
            for (double w : ways) {
                stair |= Math.abs(Mth.wrapDegrees((a - w) * Mth.RAD_TO_DEG)) < 9;
            }
            if (stair || GroveBuilder.hash(s.x * 31L + s.z, i, 0, 0) < 0.3) {
                continue;
            }
            double r = surface(foot, a, street - 1) - 0.7;
            Furnish.rail(s, Mth.floor(x + Math.cos(a) * r), street, Mth.floor(z + Math.sin(a) * r), 1);
        }
        int upperPosts = (int) Math.round(Mth.TWO_PI * upperRadius);
        for (int i = 0; i < upperPosts; i++) {
            double a = phase + i * Mth.TWO_PI / upperPosts;
            if (GroveBuilder.hash(s.x * 37L + s.z, i, 1, 0) < 0.35
                    || Math.abs(Mth.wrapDegrees((a - a1) * Mth.RAD_TO_DEG)) < 10) {
                continue;
            }
            double r = surface(bulb, a, upper - 1) - 0.7;
            Furnish.rail(s, Mth.floor(x + Math.cos(a) * r), upper, Mth.floor(z + Math.sin(a) * r), 1);
        }

        // Lanterns hung over the street, half of them gone; a few crates and workstations left out along it.
        int lanterns = (int) Math.round(Mth.TWO_PI * (inner + 4) / 12);
        for (int i = 0; i < lanterns; i++) {
            double a = phase + i * Mth.TWO_PI / lanterns;
            if (s.random.nextDouble() < 0.5) {
                double r = inner + s.range(2.5, 5.0);
                Furnish.hanging(s, Mth.floor(x + Math.cos(a) * r), street + 2, Mth.floor(z + Math.sin(a) * r),
                        s.range(1, 2));
            }
        }
        int litter = s.range(3, 5);
        for (int i = 0; i < litter; i++) {
            int slot = s.random.nextInt(slots);
            double a = angles[slot] + Math.PI / slots;
            double r = inner + 0.6;
            if (s.random.nextBoolean()) {
                Furnish.crate(s, x + Math.cos(a) * r, street, z + Math.sin(a) * r, a);
            } else {
                Furnish.station(s, x + Math.cos(a) * r, street, z + Math.sin(a) * r, a);
            }
        }

        // The one who stayed.
        if (nitwit && !homes.isEmpty()) {
            double[] home = homes.get(s.random.nextInt(homes.size()));
            s.point("nitwit", home[0], home[1], home[2]);
        }

        // Half the time it stands in a broad swamp, on an islet round its foot, with a landing at the foot of each
        // stair. The swamp reaches out as far as the ground lets it, so on a small island it is a crescent. It draws
        // on a stream of its own, so the tree itself is drawn just the same either way.
        if (mire && probe != null) {
            RandomSource wet = new XoroshiroRandomSource(x * 37L + 5, z);
            double islet = radius + 15;
            double lake = islet + Mth.lerp(wet.nextDouble(), 18, 28);
            double depth = Math.max(1.5, Mth.lerp(wet.nextDouble(), 1.8, 3.0) * vertical);
            // The ground round so broad a swamp rises and falls more than round a giant's, so it may stand higher
            // over the water, and the bank is cut back twice as far to meet it.
            Mire planned = GroveField.planSwamp(probe, vertical, x, z, ground, islet, lake, depth,
                    (int) Math.round(Math.max(8, 14 * vertical)), wet);
            if (planned != null) {
                List<Mire.Islet> landings = new ArrayList<>();
                for (double[] landing : feet) {
                    landings.add(new Mire.Islet(landing[0], landing[1], 5, wet.nextDouble() * Mth.TWO_PI, ground));
                }
                s.mire = planned.withIslets(landings).withBank(Mire.BANK_WIDTH * 2);
                s.mireRoots(s.mire, wet);
            }
        }

        // Young skyroots round its foot, the village's orchards gone wild.
        int young = s.range(5, 9);
        for (int i = 0; i < young; i++) {
            double a = s.random.nextDouble() * Mth.TWO_PI, d = radius + s.range(16, 38);
            double px = x + Math.cos(a) * d, pz = z + Math.sin(a) * d;
            int g = s.groundAt(px, pz);
            if (g == Integer.MIN_VALUE) {
                continue;
            }
            // Out in the swamp they stand in the water, rooted in its bed.
            if (s.mire != null && s.mire.wet(Mth.floor(px), Mth.floor(pz))) {
                g = s.mire.bed(Mth.floor(px), Mth.floor(pz));
            }
            int t = g + s.range(9, 15);
            s.tube(Tube.line(px, g, pz, px + s.range(-1, 1), t, pz + s.range(-1, 1), 1.2, 0.85, Part.Role.LOG));
            s.crown(px, t, pz, s.range(6, 8), 1.0, 0, 0);
        }
        // Its roots break through the island's underside, as the Grandfather's do.
        RandomSource roots = new XoroshiroRandomSource(x * 31L, z);
        UnderRoots.sketch(s, roots, 2 + roots.nextInt(2), Double.MAX_VALUE);
        return s.done(GiantType.VILLAGE_TREE, random.nextLong());
    }

    /**
     * One house, grown into the heartwood off a street at a bearing: a rounded room, an arched door with a burl
     * swelling round it, a round window beside it, and what was left in it. Returns its floor's centre.
     */
    private static double[] house(Sketch s, double angle, double ring, double wall, int floor,
                                  List<double[]> burls) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        double hx = s.x + cos * ring, hz = s.z + sin * ring;
        double room = s.range(2.9, 3.3);
        s.carve(Carve.chamber(hx, floor + 1.5, hz, room, 3.2, room, floor));
        // The burl the tree grew round the doorway, standing out into the street.
        double bx = s.x + cos * (wall + 0.4), bz = s.z + sin * (wall + 0.4);
        s.part(new Blob(bx, floor + 2.2, bz, 2.4, 3.2, 2.4, 0.15, s.random.nextDouble() * Mth.TWO_PI,
                Part.Role.BARK));
        burls.add(new double[]{bx, floor + 2.2, bz, 2.6});
        s.carve(Carve.arch(hx, hz, s.x + cos * (wall + 3.2), s.z + sin * (wall + 3.2), floor, 0.9, 2.7));
        // The window, to one side of the door.
        double side = angle + (s.random.nextBoolean() ? 0.17 : -0.17);
        s.carve(Carve.chamber(s.x + Math.cos(side) * (wall - 0.3), floor + 2.2, s.z + Math.sin(side) * (wall - 0.3),
                1.6, 0.8, 1.6, floor + 1));
        Furnish.house(s, hx, floor, hz, angle);
        return new double[]{hx, floor, hz};
    }

    /**
     * The atrium's furnishing: two long plank tables running from the doors up to the altar, seats along both sides
     * with gaps where they have gone, lanterns set on the tables and hung round the walls, a great lantern on a long
     * chain from the crown, glowstone dripping from the dome, and with Deep Aether, totems set into the walls.
     * {@code head} is the bearing of the altar, {@code doors} the bearings of the passages in.
     */
    private static void atrium(Sketch s, int floor, double hall, int dome, double head, double[] doors) {
        Direction along = Furnish.horizontal(head), across = along.getClockWise();
        int altar = (int) (hall - 1.8);
        Furnish.place(s, s.x + along.getStepX() * altar, floor, s.z + along.getStepZ() * altar, "aether:altar");
        for (int side : new int[]{-3, 3}) {
            for (int t = -4; t <= 3; t++) {
                int tx = s.x + along.getStepX() * t + across.getStepX() * side;
                int tz = s.z + along.getStepZ() * t + across.getStepZ() * side;
                Furnish.table(s, tx, floor, tz);
                for (int seat : new int[]{-1, 1}) {
                    if (s.random.nextDouble() < 0.3) {
                        continue;
                    }
                    Furnish.seat(s, tx + across.getStepX() * seat, floor, tz + across.getStepZ() * seat,
                            seat > 0 ? across : across.getOpposite());
                }
                if (t == -2 || t == 2) {
                    Furnish.standingLantern(s, tx, floor + 1, tz);
                }
            }
        }
        int crown = floor + 2 + dome;
        Furnish.hanging(s, s.x, crown - 8, s.z, 6);
        int lamps = s.range(5, 7);
        double phase = s.random.nextDouble() * Mth.TWO_PI;
        for (int i = 0; i < lamps; i++) {
            double a = phase + i * Mth.TWO_PI / lamps;
            double r = hall - 2.5;
            Furnish.hanging(s, (int) Math.round(s.x + Math.cos(a) * r), floor + 6,
                    (int) Math.round(s.z + Math.sin(a) * r), s.range(2, 4));
        }
        int drips = s.range(4, 7);
        for (int i = 0; i < drips; i++) {
            double a = s.random.nextDouble() * Mth.TWO_PI, r = s.range(1.5, hall - 3);
            Furnish.glowstone(s, (int) Math.round(s.x + Math.cos(a) * r), crown - 8,
                    (int) Math.round(s.z + Math.sin(a) * r), s.range(1, 3));
        }
        // Totems round the wall at head height, clear of the doors.
        int totems = 8;
        for (int i = 0; i < totems; i++) {
            double a = head + i * Mth.TWO_PI / totems;
            boolean door = false;
            for (double d : doors) {
                door |= Math.abs(Mth.wrapDegrees((a - d) * Mth.RAD_TO_DEG)) < 25;
            }
            if (door) {
                continue;
            }
            // The first block out along the bearing that the dome leaves standing.
            double cos = Math.cos(a), sin = Math.sin(a);
            for (double r = hall - 1; r < hall + 2; r += 0.25) {
                int nx = (int) Math.round(s.x + cos * r), nz = (int) Math.round(s.z + sin * r);
                if (Math.hypot(nx - s.x, nz - s.z) >= hall) {
                    Furnish.totem(s, nx, floor + 2, nz, Furnish.horizontal(a + Math.PI));
                    break;
                }
            }
        }
    }

    /**
     * A balcony off the upper gallery at a bearing, its floor running out to the atrium's wall and stopping there
     * behind a rail, looking down on the tables.
     */
    private static void balcony(Sketch s, double angle, double gallery, int upper, int street, double hall,
                                int dome) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        // The floor ends where the dome is widest under it; the arch runs on in to where the narrower dome over the
        // floor opens, so nothing walls the balcony off.
        double dy = (upper - 1 - (street + 2)) / (double) dome, high = (upper + 2 - (street + 2)) / (double) dome;
        double edge = hall * Math.sqrt(Math.max(0, 1 - dy * dy));
        double open = Math.min(edge - 0.5, hall * Math.sqrt(Math.max(0, 1 - high * high)) - 1);
        s.carve(Carve.arch(s.x + cos * (gallery + 1), s.z + sin * (gallery + 1), s.x + cos * open,
                s.z + sin * open, upper, 1.2, 3.0));
        for (int k = -1; k <= 1; k++) {
            double r = edge + 0.8;
            Furnish.rail(s, (int) Math.round(s.x + cos * r - sin * k), upper,
                    (int) Math.round(s.z + sin * r + cos * k), 1);
        }
    }

    /**
     * The villagers' fields on top of the bulb, round under the crown: a ladder up a shaft from the upper gallery at
     * a bearing, coming out among them, and patches of old tilled ground in rings round the neck, mostly overgrown.
     */
    private static void fields(Sketch s, Trunk bulb, double angle, double gallery, int upper) {
        int lx = (int) Math.round(s.x + Math.cos(angle) * (gallery - 0.3));
        int lz = (int) Math.round(s.z + Math.sin(angle) * (gallery - 0.3));
        int roof = roof(bulb, lx, lz, upper);
        if (roof > upper + 3) {
            s.carve(Carve.tube(Tube.line(lx, upper, lz, lx, roof + 2, lz, 0.8, 0.8, Part.Role.LOG), upper));
            Furnish.ladder(s, lx, upper, lz, roof - upper + 1, Furnish.horizontal(angle));
            s.point("fields", lx, roof + 1, lz);
        }
        double neck = bulb.radiusAt(bulb.top()) + 1.5, outer = gallery + 1.5;
        int patches = s.range(4, 6);
        java.util.Set<Long> taken = new java.util.HashSet<>();
        taken.add(net.minecraft.core.BlockPos.asLong(lx, 0, lz));
        for (int p = 0; p < patches; p++) {
            // The first patch lies where the ladder comes out.
            double pa = p == 0 ? angle : s.random.nextDouble() * Mth.TWO_PI;
            double r0 = p == 0 ? outer - 3 : s.range(neck, outer - 2);
            double r1 = Math.min(outer, r0 + s.range(2.0, 4.0));
            double half = s.range(3.0, 6.0) / r0;
            Furnish.Crop crop = Furnish.crop(s);
            int reach = (int) Math.ceil(r1) + 1;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double r = Math.hypot(dx, dz);
                    if (r < r0 || r > r1 || Math.abs(Mth.wrapDegrees((Math.atan2(dz, dx) - pa) * Mth.RAD_TO_DEG))
                            > half * Mth.RAD_TO_DEG) {
                        continue;
                    }
                    int fx = s.x + dx, fz = s.z + dz;
                    int top = roof(bulb, fx, fz, upper);
                    if (top <= upper + 6 || taken.contains(net.minecraft.core.BlockPos.asLong(fx, 0, fz))
                            || s.random.nextDouble() < 0.2
                            || covered(s, bulb, fx, top, fz)) {
                        continue;
                    }
                    Furnish.field(s, fx, top, fz, crop, taken);
                }
            }
        }
    }

    /** The top of a trunk's wood in a column, searched down to {@code floor}; MIN_VALUE if there is none. */
    private static int roof(Trunk trunk, int x, int z, int floor) {
        for (int y = trunk.top(); y > floor; y--) {
            if (trunk.margin(x, y, z) > 0) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** Whether any other wood lies over a column within a prop's reach above {@code top}. */
    private static boolean covered(Sketch s, Part except, int x, int top, int z) {
        for (Part part : s.wood) {
            if (part == except || !part.bounds().column(x, z)) {
                continue;
            }
            for (int y = top + 1; y <= top + Prop.REACH; y++) {
                if (part.bounds().height(y) && part.margin(x, y, z) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How far out from a trunk's axis its surface lies, at a bearing and a height. */
    private static double surface(Trunk trunk, double angle, double y) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        double lo = 0, hi = trunk.radius() * 2.5;
        for (int i = 0; i < 14; i++) {
            double mid = (lo + hi) / 2;
            if (trunk.margin(trunk.x() + cos * mid, y, trunk.z() + sin * mid) > 0) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private VillageTree() {
    }
}
