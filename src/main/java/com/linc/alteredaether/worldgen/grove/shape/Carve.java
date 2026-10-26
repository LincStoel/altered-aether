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
package com.linc.alteredaether.worldgen.grove.shape;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * A space cut out of a giant: a hollow, a tunnel, an alcove. Nothing of the tree is placed inside it, and wood within
 * a block or so of its surface is stripped, as heartwood laid bare. Above its floor it also clears the ground, so a
 * hollow on uneven ground has a level floor and a tunnel runs straight through.
 */
public abstract class Carve {
    protected final int floor;
    private final Bounds bounds;

    protected Carve(int floor, Bounds bounds) {
        this.floor = floor;
        this.bounds = bounds;
    }

    public Bounds bounds() {
        return bounds;
    }

    /** The lowest height the carve clears; it never digs below this. */
    public int floor() {
        return floor;
    }

    /** Positive inside, in roughly blocks. */
    public abstract double margin(double x, double y, double z);

    /** The top block of water the carve holds, or MIN_VALUE for a dry one. */
    public int water() {
        return Integer.MIN_VALUE;
    }

    /** The top of the mud bedded on the carve's floor in a column, or MIN_VALUE where the floor is bare. */
    public int mud(int x, int z) {
        return Integer.MIN_VALUE;
    }

    /** How high the mud lies on a pool's floor, column by column. */
    @FunctionalInterface
    public interface Mud {
        int top(int x, int z);
    }

    /** Whether water runs through the carve, so the wood it lays bare is stripped along the flow. */
    public boolean wet() {
        return false;
    }

    /** Which way water runs past a point of the carve's surface, or null where it has no clear way. */
    @Nullable
    public Direction.Axis flow(double x, double y, double z) {
        return null;
    }

    /** The way water runs at a point. */
    @FunctionalInterface
    public interface Flow {
        @Nullable
        Direction.Axis at(double x, double y, double z);
    }

    /**
     * This carve with water running through it: still water up to {@code water} (MIN_VALUE for none, as down a
     * stream bed the water finds its own way), and the wood round it stripped with the grain along {@code flow}.
     */
    public Carve wet(int water, Flow flow) {
        Carve dry = this;
        return new Carve(floor, new Bounds(bounds.minX(), bounds.minY() - 2, bounds.minZ(), bounds.maxX(),
                bounds.maxY(), bounds.maxZ())) {
            @Override
            public double margin(double x, double y, double z) {
                double m = dry.margin(x, y, z);
                // Under a flat floor, how deep below it: the water reaches the floor's wood too.
                if (y < floor && dry.margin(x, floor, z) > 0) {
                    return y - floor;
                }
                return m;
            }

            @Override
            public int water() {
                return water;
            }

            @Override
            public boolean wet() {
                return true;
            }

            @Nullable
            @Override
            public Direction.Axis flow(double x, double y, double z) {
                return flow.at(x, y, z);
            }
        };
    }

    /**
     * This carve holding still water up to {@code water} over a bed of mud, as a crater floods: the water stands
     * still, so it leaves the wood round it as it was.
     */
    public Carve pool(int water, Mud mud) {
        Carve dry = this;
        return new Carve(floor, bounds) {
            @Override
            public double margin(double x, double y, double z) {
                return dry.margin(x, y, z);
            }

            @Override
            public int water() {
                return water;
            }

            @Override
            public int mud(int x, int z) {
                return mud.top(x, z);
            }
        };
    }

    /**
     * A level gallery round an axis: everything from {@code inner} to {@code outer} out from (x, z), {@code height}
     * tall over {@code floor}. Cut into a trunk it leaves a street under the trunk's own overhang, open to the air.
     * It spares the spheres in {@code keep}, each {x, y, z, radius}: wood the tree has grown out into the street.
     */
    public static Carve ring(double x, double z, double inner, double outer, int floor, double height,
                             java.util.List<double[]> keep) {
        Bounds bounds = new Bounds((int) Math.floor(x - outer) - 1, floor, (int) Math.floor(z - outer) - 1,
                (int) Math.ceil(x + outer) + 1, floor + (int) Math.ceil(height) + 1, (int) Math.ceil(z + outer) + 1);
        return new Carve(floor, bounds) {
            @Override
            public double margin(double px, double py, double pz) {
                if (py < floor) {
                    return -Double.MAX_VALUE;
                }
                double r = Math.sqrt((px - x) * (px - x) + (pz - z) * (pz - z));
                double m = Math.min(Math.min(r - inner, outer - r), floor + height - py);
                for (double[] k : keep) {
                    double dx = px - k[0], dy = py - k[1], dz = pz - k[2];
                    m = Math.min(m, Math.sqrt(dx * dx + dy * dy + dz * dz) - k[3]);
                }
                return m;
            }
        };
    }

    /** The space a tube would fill, cut out instead, never below {@code floor}: a channel, a gully. */
    public static Carve tube(Tube tube, int floor) {
        return new Carve(floor, tube.bounds()) {
            @Override
            public double margin(double x, double y, double z) {
                return y < floor ? -Double.MAX_VALUE : tube.margin(x, y, z);
            }

            @Nullable
            @Override
            public Direction.Axis flow(double x, double y, double z) {
                return tube.axis(x, y, z);
            }
        };
    }

    /** An upright ellipsoid, cut off flat at {@code floor}: a chamber. */
    public static Carve chamber(double x, double y, double z, double rx, double ry, double rz, int floor) {
        return new Carve(floor, Bounds.around(x, y, z, rx, ry, rz)) {
            @Override
            public double margin(double px, double py, double pz) {
                if (py < floor) {
                    return -Double.MAX_VALUE;
                }
                double dx = (px - x) / rx, dy = (py - y) / ry, dz = (pz - z) / rz;
                return (1 - Math.sqrt(dx * dx + dy * dy + dz * dz)) * Math.min(rx, Math.min(ry, rz));
            }
        };
    }

    /**
     * A capsule from one point to another, tapering from {@code r0} to {@code r1} and never cutting below
     * {@code floor}: a fallen log's hollow, flat-floored where the floor cuts it.
     */
    public static Carve capsule(double x0, double y0, double z0, double x1, double y1, double z1, double r0,
                                double r1, int floor) {
        Bounds bounds = Bounds.around(x0, y0, z0, r0, r0, r0).union(Bounds.around(x1, y1, z1, r1, r1, r1));
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double length2 = dx * dx + dy * dy + dz * dz;
        return new Carve(floor, bounds) {
            @Override
            public double margin(double px, double py, double pz) {
                if (py < floor) {
                    return -Double.MAX_VALUE;
                }
                double t = length2 < 1e-9 ? 0 : ((px - x0) * dx + (py - y0) * dy + (pz - z0) * dz) / length2;
                t = t < 0 ? 0 : t > 1 ? 1 : t;
                double ox = px - x0 - dx * t, oy = py - y0 - dy * t, oz = pz - z0 - dz * t;
                return r0 + (r1 - r0) * t - Math.sqrt(ox * ox + oy * oy + oz * oz);
            }
        };
    }

    /**
     * A level passage from one point to another with a round-arched section, {@code halfWidth} across at the floor
     * and {@code height} tall at the crown: a tunnel through a trunk, or the slit into a hollow.
     */
    public static Carve arch(double x0, double z0, double x1, double z1, int floor, double halfWidth, double height) {
        double dx = x1 - x0, dz = z1 - z0;
        double length = Math.sqrt(dx * dx + dz * dz);
        double ux = dx / length, uz = dz / length;
        Bounds bounds = new Bounds((int) Math.floor(Math.min(x0, x1) - halfWidth) - 1, floor,
                (int) Math.floor(Math.min(z0, z1) - halfWidth) - 1, (int) Math.ceil(Math.max(x0, x1) + halfWidth) + 1,
                floor + (int) Math.ceil(height) + 1, (int) Math.ceil(Math.max(z0, z1) + halfWidth) + 1);
        return new Carve(floor, bounds) {
            @Override
            public double margin(double px, double py, double pz) {
                double h = py - floor;
                if (h < 0) {
                    return -Double.MAX_VALUE;
                }
                double ox = px - x0, oz = pz - z0;
                double along = ox * ux + oz * uz;
                double across = -ox * uz + oz * ux;
                double end = Math.min(along, length - along);
                double w = across / halfWidth, v = h / height;
                double section = (1 - Math.sqrt(w * w + v * v)) * Math.min(halfWidth, height);
                return Math.min(section, end);
            }
        };
    }
}
