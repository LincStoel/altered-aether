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
package com.linc.alteredaether.worldgen.landmark;

import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.worldgen.TerrainProbe;
import com.linc.alteredaether.worldgen.grove.Giant;
import com.linc.alteredaether.worldgen.grove.GroveBuilder;
import com.linc.alteredaether.worldgen.grove.Mire;
import com.linc.alteredaether.worldgen.grove.MireBuilder;
import com.linc.alteredaether.worldgen.grove.VillageTree;
import com.linc.alteredaether.worldgen.ruin.RuinPlan;
import com.linc.alteredaether.worldgen.ruin.Ruins;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The deserted village tree, a landmark of the Skyroot Cathedral: the tree itself is drawn and built the way the
 * grove's giants are ({@link VillageTree}), and round its foot lie the ruins of the stone houses its people built
 * before they moved into the tree. This finds it a broad, level stand well inside a cathedral, clear of the
 * Grandfather.
 */
record VillageTreeSite(int x, int z, Giant giant, List<RuinPlan> ruins, List<RuinPlan> drowned,
                       @Nullable Mire mire, TerrainProbe probe) implements Landmark {
    @Nullable
    static VillageTreeSite tryAt(LandmarkField field, int x, int z, RandomSource random) {
        TerrainProbe probe = field.probe();
        if (!field.ringSuits(LandmarkType.VILLAGE_TREE, x, z, 48, 7)) {
            return null;
        }
        if (field.nearest(LandmarkType.GRANDFATHER, x, z, 400).filter(g -> Math.hypot(g.x() - x, g.z() - z)
                < g.extent() + 80).isPresent()) {
            return null;
        }
        int centre = probe.surface(x, z);
        if (centre == Integer.MIN_VALUE) {
            return null;
        }
        int low = centre, held = 0;
        int tolerance = (int) Math.round(10 * field.vertical());
        for (int d = 0; d < 8; d++) {
            double angle = d * Math.PI / 4;
            for (double r : new double[]{20, 36}) {
                int g = probe.surfaceNear(x + (int) Math.round(Math.cos(angle) * r),
                        z + (int) Math.round(Math.sin(angle) * r), centre, centre - 40, centre + 40);
                if (g != Integer.MIN_VALUE && Math.abs(g - centre) <= tolerance) {
                    held++;
                    low = Math.min(low, g);
                }
            }
        }
        if (held < 14) {
            return null;
        }
        boolean nitwit = random.nextDouble() < AACommonConfig.VILLAGE_NITWIT_CHANCE.get();
        // The swamp's roll draws on a stream of its own, so the tree and its ruins come out the same either way.
        boolean swamp = new XoroshiroRandomSource(x * 0x9E3779B9L + 17, z).nextDouble()
                < AACommonConfig.VILLAGE_MIRE_CHANCE.get();
        Giant giant = VillageTree.sketch(probe, random, field.vertical(), field.buildTop(), x, z, low, nitwit,
                swamp);
        // The stone houses of before, fallen round the foot: mostly cottages, now and then a keep, spaced apart.
        List<RuinPlan> ruins = new ArrayList<>();
        List<Double> taken = new ArrayList<>();
        int count = 3 + random.nextInt(4);
        for (int i = 0; i < count * 3 && ruins.size() < count; i++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            if (taken.stream().anyMatch(t -> Math.abs(Mth.wrapDegrees((t - a) * Mth.RAD_TO_DEG)) < 35)) {
                continue;
            }
            double d = 36 + random.nextDouble() * 18;
            int rx = x + (int) Math.round(Math.cos(a) * d), rz = z + (int) Math.round(Math.sin(a) * d);
            int g = probe.surfaceNear(rx, rz, low, low - 16, low + 16);
            if (g == Integer.MIN_VALUE) {
                continue;
            }
            taken.add(a);
            ruins.add(random.nextDouble() < 0.8 ? Ruins.cottage(random, rx, rz, 0.6 + random.nextDouble() * 0.4, 0.25)
                    : Ruins.keep(random, rx, rz));
        }
        // In a swamp, about half the ruins out in the water have sunk into it; the rest, and any on the bank, stand
        // on islets of their own.
        Mire mire = giant.mire();
        List<RuinPlan> drowned = new ArrayList<>();
        if (mire != null) {
            List<Mire.Islet> islets = new ArrayList<>();
            for (RuinPlan ruin : ruins) {
                double cx = (ruin.bounds().minX() + ruin.bounds().maxX() + 1) / 2.0;
                double cz = (ruin.bounds().minZ() + ruin.bounds().maxZ() + 1) / 2.0;
                double half = Math.hypot(ruin.bounds().maxX() - ruin.bounds().minX() + 1,
                        ruin.bounds().maxZ() - ruin.bounds().minZ() + 1) / 2;
                if (mire.wet((int) Math.floor(cx), (int) Math.floor(cz)) && random.nextBoolean()) {
                    drowned.add(ruin);
                    continue;
                }
                int g = probe.surface((int) Math.floor(cx), (int) Math.floor(cz));
                islets.add(new Mire.Islet(cx, cz, half / 0.78 + 1.5, random.nextDouble() * Mth.TWO_PI,
                        Math.max(g, mire.water() + 1)));
            }
            mire = mire.withIslets(islets);
        }
        return new VillageTreeSite(x, z, giant, List.copyOf(ruins), List.copyOf(drowned), mire, probe);
    }

    @Override
    public LandmarkType type() {
        return LandmarkType.VILLAGE_TREE;
    }

    @Override
    public double extent() {
        double far = giant.extent();
        for (RuinPlan ruin : ruins) {
            far = Math.max(far, Math.max(Math.max(x - ruin.bounds().minX(), ruin.bounds().maxX() - x),
                    Math.max(z - ruin.bounds().minZ(), ruin.bounds().maxZ() - z)));
        }
        return mire == null ? far : Math.max(far, mire.extent());
    }

    @Override
    public void build(WorldGenLevel level, ChunkPos chunk) {
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ(), x1 = chunk.getMaxBlockX(), z1 = chunk.getMaxBlockZ();
        // As in the groves: the lake first, then the tree built into it, and last the lake's growth and its seal.
        MireBuilder.Waters waters = mire != null && mire.touches(x0 - 1, z0 - 1, x1 + 1, z1 + 1)
                ? new MireBuilder.Waters(List.of(mire)) : MireBuilder.Waters.NONE;
        if (!waters.isEmpty()) {
            MireBuilder.dig(level, x0, z0, x1, z1, waters);
        }
        GroveBuilder.paint(level, x0, z0, x1, z1, giant);
        GroveBuilder.build(level, x0, z0, x1, z1, giant, waters);
        GroveBuilder.furnish(level, x0, z0, x1, z1, giant);
        GroveBuilder.hangVines(level, x0, z0, x1, z1, giant);
        // The ruins stand on the bare terrain as the probe reads it, so every chunk agrees on their ground; in a swamp,
        // on their islets, raised clear of the water where the lake would drown them.
        for (RuinPlan ruin : ruins) {
            if (ruin.bounds().maxX() >= x0 && ruin.bounds().minX() <= x1 && ruin.bounds().maxZ() >= z0
                    && ruin.bounds().minZ() <= z1) {
                ruin.place(level, x0, z0, x1, z1, (px, pz) -> ground(px, pz), drowned.contains(ruin));
            }
        }
        if (!waters.isEmpty()) {
            MireBuilder.dress(level, x0, z0, x1, z1, waters);
            MireBuilder.seal(level, x0, z0, x1, z1, waters);
        }
        for (BlockPos home : giant.points("nitwit")) {
            if (home.getX() >= x0 && home.getX() <= x1 && home.getZ() >= z0 && home.getZ() <= z1) {
                nitwit(level, home);
            }
        }
    }

    /**
     * The ground a ruin stands on in a column, as the plan leaves it, so every chunk agrees: the probe's terrain, an
     * islet raised clear of the water, the lake's bed under water, or a bank cut back.
     */
    private int ground(int px, int pz) {
        int g = probe.surface(px, pz);
        if (mire == null || g == Integer.MIN_VALUE) {
            return g;
        }
        if (mire.islet(px, pz)) {
            return Math.max(g, mire.water() + 1);
        }
        if (mire.wet(px, pz)) {
            return mire.bed(px, pz);
        }
        return Math.min(g, mire.bankTop(px, pz));
    }

    /** The one villager who never left: a nitwit, who stays put. */
    private static void nitwit(WorldGenLevel level, BlockPos home) {
        Villager villager = EntityType.VILLAGER.create(level.getLevel());
        if (villager == null) {
            return;
        }
        villager.moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0);
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(home), MobSpawnType.STRUCTURE, null);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NITWIT));
        villager.setPersistenceRequired();
        level.addFreshEntityWithPassengers(villager);
    }

    @Override
    public List<BlockPos> points(String kind) {
        return giant.points(kind);
    }

    @Override
    public String detail() {
        return giant.points("house").size() + " houses, " + ruins.size() + " ruins"
                + (giant.points("nitwit").isEmpty() ? "" : ", a nitwit at home")
                + (mire == null ? "" : ", in a swamp" + (drowned.isEmpty() ? "" : ", " + drowned.size() + " drowned"));
    }

    @Override
    public int visitY() {
        return giant.points("street").stream().findFirst().map(p -> p.getY() + 1).orElse(giant.base() + 12);
    }
}
