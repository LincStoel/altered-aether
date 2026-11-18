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
package com.linc.alteredaether.debug;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.command.AACommands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import com.linc.alteredaether.worldgen.basin.BasinField;
import com.linc.alteredaether.worldgen.basin.AtollSite;
import com.linc.alteredaether.worldgen.basin.BasinSite;
import com.linc.alteredaether.worldgen.basin.BasinType;
import com.linc.alteredaether.worldgen.grove.Giant;
import com.linc.alteredaether.worldgen.grove.GiantType;
import com.linc.alteredaether.worldgen.grove.GroveField;
import com.linc.alteredaether.worldgen.grove.Mire;
import com.linc.alteredaether.worldgen.landmark.Landmark;
import com.linc.alteredaether.worldgen.landmark.LandmarkField;
import com.linc.alteredaether.worldgen.landmark.LandmarkType;
import com.linc.alteredaether.worldgen.sea.CloudSea;
import com.linc.alteredaether.worldgen.skirt.SkirtField;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The dev runs' half of {@code /altered_aether}, added to the released commands in {@link AACommands}:
 * {@code report [radius] [step]} prints a {@link WorldgenReport} for the Aether around the caller, {@code debug} what the
 * worldgen thinks of where the caller stands, and {@code fly} a {@link Flight}. Setting the system property
 * {@code altered_aether.report} to {@code radius,step} runs a report around the origin when the server starts and then
 * stops the server, so a dev run can check worldgen headlessly; {@code altered_aether.bench} does the same for a
 * benchmark.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class DebugCommands {
    private static final ResourceKey<Level> THE_AETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(AlteredAether.MOD_ID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("report")
                        .executes(ctx -> report(ctx, 2048, 32))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(16, 32768))
                                .executes(ctx -> report(ctx, IntegerArgumentType.getInteger(ctx, "radius"), 32))
                                .then(Commands.argument("step", IntegerArgumentType.integer(1, 1024))
                                        .executes(ctx -> report(ctx, IntegerArgumentType.getInteger(ctx, "radius"),
                                                IntegerArgumentType.getInteger(ctx, "step"))))))
                .then(Commands.literal("debug").executes(DebugCommands::here))
                .then(Commands.literal("boulder")
                        .executes(ctx -> BoulderDebug.drop(ctx.getSource(), null, null))
                        .then(Commands.argument("stone", net.minecraft.commands.arguments.blocks.BlockStateArgument.block(event.getBuildContext()))
                                .executes(ctx -> BoulderDebug.drop(ctx.getSource(),
                                        net.minecraft.commands.arguments.blocks.BlockStateArgument.getBlock(ctx, "stone").getState(), null))
                                .then(Commands.argument("ore", net.minecraft.commands.arguments.blocks.BlockStateArgument.block(event.getBuildContext()))
                                        .executes(ctx -> BoulderDebug.drop(ctx.getSource(),
                                                net.minecraft.commands.arguments.blocks.BlockStateArgument.getBlock(ctx, "stone").getState(),
                                                net.minecraft.commands.arguments.blocks.BlockStateArgument.getBlock(ctx, "ore").getState())))))
                .then(Commands.literal("boulderores").executes(ctx -> BoulderDebug.ores(ctx.getSource())))
                .then(Commands.literal("golempath")
                        .executes(ctx -> golemPath(ctx, null))
                        .then(Commands.argument("item", net.minecraft.commands.arguments.item.ItemArgument.item(event.getBuildContext()))
                                .executes(ctx -> golemPath(ctx,
                                        net.minecraft.commands.arguments.item.ItemArgument.getItem(ctx, "item").createItemStack(1, false)))))
                .then(Commands.literal("rumourwarm").executes(DebugCommands::rumourWarm))
                .then(Commands.literal("rumourbiomes").executes(DebugCommands::rumourBiomes))
                .then(Commands.literal("fly")
                        .then(Commands.argument("from", net.minecraft.commands.arguments.coordinates.ColumnPosArgument.columnPos())
                                .then(Commands.argument("to", net.minecraft.commands.arguments.coordinates.ColumnPosArgument.columnPos())
                                        .then(Commands.argument("speed", IntegerArgumentType.integer(1, 200))
                                                .executes(ctx -> fly(ctx, 10))
                                                .then(Commands.argument("radius", IntegerArgumentType.integer(2, 32))
                                                        .executes(ctx -> fly(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))))));
    }

    /**
     * {@code /altered_aether golempath [item]}: where tidy mode files an item (the held one if none is named), and the
     * recipe chain its material was read from.
     */
    private static int golemPath(CommandContext<CommandSourceStack> ctx, @javax.annotation.Nullable net.minecraft.world.item.ItemStack named)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        net.minecraft.world.item.ItemStack stack = named != null ? named : source.getPlayerOrException().getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("Hold an item or name one."));
            return 0;
        }
        var path = com.linc.alteredaether.entity.golem.group.GolemPaths.path(stack);
        var chain = com.linc.alteredaether.entity.golem.group.GolemPaths.chain(stack.getItem());
        source.sendSuccess(() -> Component.literal(BuiltInRegistries.ITEM.getKey(stack.getItem()) + ": " + path
                + "\n  made from: " + String.join(" <- ", chain)), false);
        return 1;
    }

    /** The made-up player {@code rumourwarm} searches ahead for. */
    private static final java.util.UUID RUMOUR_SCOUT = java.util.UUID.nameUUIDFromBytes("rumour-scout".getBytes());

    /**
     * {@code /altered_aether rumourwarm}: runs the rumour warm-up from the caller's position as if a player stood there,
     * and says whether it's finished. Repeat until it is, then time {@code /altered_aether rumour} at the same spot.
     */
    private static int rumourWarm(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        LandmarkField field = LandmarkField.get(source.getLevel().dimension());
        if (field == null) {
            source.sendFailure(Component.literal("There are no landmarks here."));
            return 0;
        }
        int x = (int) source.getPosition().x, z = (int) source.getPosition().z;
        com.linc.alteredaether.loot.RumourWarmup.warm(source.getLevel(), field, RUMOUR_SCOUT, x, z);
        boolean settled = com.linc.alteredaether.loot.RumourWarmup.settled(source.getLevel(), field,
                RUMOUR_SCOUT, x, z);
        source.sendSuccess(() -> Component.literal(settled ? "Warmed." : "Still searching ahead."), false);
        return settled ? 1 : 0;
    }

    /**
     * {@code /altered_aether rumourbiomes}: times the grove searches a rumour map from the caller's position makes, one
     * per archwood grove (Ars Nouveau's). A repeat in the same cell, or after {@code rumourwarm}, is a cache hit.
     */
    private static int rumourBiomes(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        int x = (int) source.getPosition().x, z = (int) source.getPosition().z;
        StringBuilder out = new StringBuilder();
        for (ResourceLocation biome : com.linc.alteredaether.loot.RumourMapFunction.ARCHWOOD_GROVES) {
            if (!com.linc.alteredaether.loot.RumourMapFunction.registered(level, biome)) {
                continue;
            }
            long start = System.nanoTime();
            var found = com.linc.alteredaether.loot.RumourMapFunction.nearestBiome(level, biome, x, z,
                    com.linc.alteredaether.loot.RumourMapFunction.DEFAULT_RADIUS);
            double ms = (System.nanoTime() - start) / 1e6;
            out.append(String.format("%s %s (%.1f ms); ", biome.getPath(),
                    found.map(pos -> pos.getX() + "," + pos.getZ()).orElse("none"), ms));
        }
        String report = out.isEmpty() ? "No archwood groves registered." : out.toString();
        source.sendSuccess(() -> Component.literal(report), false);
        return 1;
    }

    /** {@code /altered_aether fly <from> <to> <speed> [radius]}: see {@link Flight}. */
    private static int fly(CommandContext<CommandSourceStack> ctx, int radius) {
        var from = net.minecraft.commands.arguments.coordinates.ColumnPosArgument.getColumnPos(ctx, "from");
        var to = net.minecraft.commands.arguments.coordinates.ColumnPosArgument.getColumnPos(ctx, "to");
        if (!Flight.start(ctx.getSource().getLevel(), from.x(), from.z(), to.x(), to.z(),
                IntegerArgumentType.getInteger(ctx, "speed"), radius)) {
            ctx.getSource().sendFailure(Component.literal("A flight is already under way."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Flying; progress goes to the log."), false);
        return 1;
    }

    /**
     * {@code /altered_aether debug}: what the worldgen thinks of the caller's position. The biome and the climate
     * point that chose it, the region mask, the basin and landmark there if any, and the storm.
     */
    private static int here(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        List<String> lines = new ArrayList<>();
        lines.add("Biome " + level.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("?"));
        var sampler = level.getChunkSource().randomState().sampler();
        var point = sampler.sample(net.minecraft.core.QuartPos.fromBlock(pos.getX()),
                net.minecraft.core.QuartPos.fromBlock(pos.getY()), net.minecraft.core.QuartPos.fromBlock(pos.getZ()));
        lines.add(String.format("Climate: temperature %.2f, humidity %.2f, continents %.2f, erosion %.2f, weirdness %.2f",
                net.minecraft.world.level.biome.Climate.unquantizeCoord(point.temperature()),
                net.minecraft.world.level.biome.Climate.unquantizeCoord(point.humidity()),
                net.minecraft.world.level.biome.Climate.unquantizeCoord(point.continentalness()),
                net.minecraft.world.level.biome.Climate.unquantizeCoord(point.erosion()),
                net.minecraft.world.level.biome.Climate.unquantizeCoord(point.weirdness())));
        lines.add(String.format("Region mask %.3f (1 inside Altered Aether's region)",
                com.linc.alteredaether.worldgen.density.RegionBinding.maskAt(level, pos.getX(), pos.getZ())));
        BasinField basins = BasinField.get(level.dimension());
        if (basins != null) {
            List<BasinSite> sites = basins.sitesTouching(pos.getX(), pos.getZ(), pos.getX(), pos.getZ());
            sites.stream().filter(site -> site.reach(pos.getX(), pos.getZ()) < 1.5).forEach(site -> lines.add(
                    String.format("Basin: %s centred %d %d, radius %.1f, water at %d", site.type().name().toLowerCase(),
                            site.x(), site.z(), site.radius(), site.waterLevel())));
        }
        LandmarkField landmarks = LandmarkField.get(level.dimension());
        if (landmarks != null) {
            landmarks.touching(pos.getX(), pos.getZ(), pos.getX(), pos.getZ()).forEach(site -> lines.add(String.format(
                    "Landmark: %s centred %d %d, %.0f blocks away", site.type().id(), site.x(), site.z(),
                    Math.hypot(site.x() - pos.getX(), site.z() - pos.getZ()))));
        }
        boolean stormy = level.getBiome(pos).is(com.linc.alteredaether.AATags.HAS_STORMS);
        lines.add(String.format("Storm intensity %.2f%s", com.linc.alteredaether.storm.TempestStorms.intensityAt(level,
                pos.getX(), pos.getZ()), stormy ? "" : " (no storms in this biome)"));
        lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return 1;
    }

    private static int report(CommandContext<CommandSourceStack> ctx, int radius, int step) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel aether = source.getServer().getLevel(THE_AETHER);
        if (aether == null) {
            source.sendFailure(Component.literal("The Aether isn't loaded."));
            return 0;
        }
        int x = (int) source.getPosition().x, z = (int) source.getPosition().z;
        source.sendSuccess(() -> Component.literal("Sampling the Aether..."), false);
        // Off the server thread: a large report takes longer than the watchdog allows a tick.
        CompletableFuture.supplyAsync(() -> WorldgenReport.run(aether, x, z, radius, step), Util.backgroundExecutor())
                .thenAcceptAsync(lines -> lines.forEach(line -> source.sendSuccess(() -> Component.literal(line),
                        false)), source.getServer());
        return 1;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        String bench = System.getProperty("altered_aether.bench", "");
        if (!bench.isBlank()) {
            String[] parts = bench.split(",");
            benchmark(event.getServer(), Integer.parseInt(parts[0].trim()), parts.length > 1 ? parts[1].trim() : null);
        }
        String spec = System.getProperty("altered_aether.report", "");
        if (spec.isBlank()) {
            return;
        }
        MinecraftServer server = event.getServer();
        String[] parts = spec.split(",");
        int radius = Integer.parseInt(parts[0].trim());
        int step = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 32;
        ServerLevel aether = server.getLevel(THE_AETHER);
        if (aether == null) {
            AlteredAether.LOGGER.error("Worldgen report: the Aether isn't loaded");
            server.halt(false);
            return;
        }
        CompletableFuture.runAsync(() -> WorldgenReport.run(aether, 0, 0, radius, step), Util.backgroundExecutor())
                .whenCompleteAsync((done, error) -> {
                    if (error != null) {
                        AlteredAether.LOGGER.error("Worldgen report failed", error);
                    }
                    server.halt(false);
                }, server);
    }

    /**
     * What the cloud sea made of the chunks: its cloud blocks, how many sat with solid rock straight above them (a cave
     * or an overhang it crept under), and columns of open sea whose ground lies in or just above the sheet's band.
     */
    private static void seaTally(ServerLevel aether, List<ChunkPos> positions) {
        CloudSea sea = CloudSea.terrain(THE_AETHER);
        if (sea == null) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long cloud = 0, covered = 0, columns = 0, lowGround = 0;
        for (ChunkPos chunk : positions) {
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    int x = chunk.getMinBlockX() + dx, z = chunk.getMinBlockZ() + dz;
                    boolean open = sea.weight(x, z) > 0.9;
                    columns += open ? 1 : 0;
                    boolean low = false;
                    for (int y = -40; y <= 8; y++) {
                        var state = aether.getBlockState(pos.set(x, y, z));
                        if (state.is(com.aetherteam.aether.block.AetherBlocks.COLD_AERCLOUD.get())) {
                            cloud++;
                            var above = aether.getBlockState(pos.set(x, y + 1, z));
                            if (!above.isAir() && !above.is(com.aetherteam.aether.block.AetherBlocks.COLD_AERCLOUD.get())
                                    && above.isSolidRender(aether, pos)) {
                                covered++;
                            }
                        } else if (open && !state.isAir() && state.getFluidState().isEmpty()
                                && aether.getBlockState(pos.set(x, y + 1, z)).isAir()) {
                            low = true;
                        }
                    }
                    lowGround += low ? 1 : 0;
                }
            }
        }
        AlteredAether.LOGGER.info("Cloud sea: {} cloud blocks at y -40..8, {} under solid rock; {} open-sea columns, "
                + "{} with ground at y -40..8", cloud, covered, columns, lowGround);
    }

    /**
     * Pictures of the cloud skirt round one spot: the area from above and from the sides, the cloud alone, sections
     * east-west and north-south through it, and the skirt field's own grid there ({@link #skirtGrid}).
     */
    private static void skirtRenders(MinecraftServer server, ServerLevel aether, String name, BlockPos at) {
        int x = at.getX(), y = at.getY(), z = at.getZ();
        server.submit(() -> WorldRender.render(aether, name, x - 64, z - 64, x + 64, z + 64, y - 40, y + 30, 4)).join();
        server.submit(() -> WorldRender.render(aether, name + "_cloud", x - 64, z - 64, x + 64, z + 64, y - 40, y + 30, 4,
                state -> state.is(com.aetherteam.aether.block.AetherBlocks.COLD_AERCLOUD.get()))).join();
        server.submit(() -> WorldRender.render(aether, name + "_slice_x", x - 64, z, x + 64, z, y - 40, y + 30, 6)).join();
        server.submit(() -> WorldRender.render(aether, name + "_slice_z", x, z - 64, x, z + 64, y - 40, y + 30, 6)).join();
        // The biome round the spot at a few heights, east and south of it too, as the world has it.
        StringBuilder biomes = new StringBuilder();
        for (int[] d : new int[][]{{0, 0}, {32, 0}, {64, 0}, {0, 32}, {32, 32}}) {
            biomes.append(" (").append(x + d[0]).append(',').append(z + d[1]).append(')');
            for (int by : new int[]{-40, -10, 0, 20, 60, 100}) {
                biomes.append(' ').append(by).append('=').append(aether.getUncachedNoiseBiome((x + d[0]) >> 2, by >> 2,
                        (z + d[1]) >> 2).unwrapKey().map(k -> k.location().getPath()).orElse("?"));
            }
        }
        AlteredAether.LOGGER.info("Skirt spot {} biomes:{}", name, biomes);
        skirtGrid(aether, name + "_grid", x, z, 64);
    }

    /**
     * The skirt field's grid round a point, 8 pixels per grid column: sky dark, high columns grey, land shaded by its
     * top from dark (y -64) to light (y 23) and tinted by its island's verdict (green low, blue not low, yellow a
     * landmass voted on locally, magenta not judged), rim columns with a red centre (cyan where a crag collar grows),
     * the point itself outlined white.
     */
    private static void skirtGrid(ServerLevel aether, String name, int x, int z, int radius) {
        SkirtField field = SkirtField.get(THE_AETHER);
        if (field == null) {
            return;
        }
        int step = com.linc.alteredaether.worldgen.skirt.SkirtGrid.STEP, cells = radius * 2 / step + 1, px = 8;
        int gx0 = Math.floorDiv(x - radius, step), gz0 = Math.floorDiv(z - radius, step);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(cells * px, cells * px,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        for (int j = 0; j < cells; j++) {
            for (int i = 0; i < cells; i++) {
                int[] column = field.debugColumn(gx0 + i, gz0 + j);
                int top = column[0];
                java.awt.Color colour;
                if (top == com.linc.alteredaether.worldgen.skirt.SkirtGrid.SKY) {
                    colour = new java.awt.Color(0x1A1E33);
                } else if (top == com.linc.alteredaether.worldgen.skirt.SkirtGrid.HIGH) {
                    colour = new java.awt.Color(0x8A8A8A);
                } else {
                    float t = Math.max(0, Math.min(1, (top + 64) / 87f)) * 0.7f + 0.3f;
                    int[] tint = switch (column[2]) {
                        case 1 -> new int[]{80, 230, 90};
                        case 2 -> new int[]{80, 120, 240};
                        case 3 -> new int[]{230, 210, 60};
                        default -> new int[]{230, 60, 230};
                    };
                    colour = new java.awt.Color((int) (tint[0] * t), (int) (tint[1] * t), (int) (tint[2] * t));
                }
                g.setColor(colour);
                g.fillRect(i * px, j * px, px, px);
                if (column[1] != 0) {
                    g.setColor(column[1] == 2 ? java.awt.Color.CYAN : java.awt.Color.RED);
                    g.fillRect(i * px + 3, j * px + 3, 2, 2);
                }
            }
        }
        int cx = (Math.floorDiv(x, step) - gx0) * px, cz = (Math.floorDiv(z, step) - gz0) * px;
        g.setColor(java.awt.Color.WHITE);
        g.drawRect(cx - 1, cz - 1, px + 1, px + 1);
        g.dispose();
        try {
            java.nio.file.Path folder = aether.getServer().getServerDirectory().resolve("renders");
            java.nio.file.Files.createDirectories(folder);
            javax.imageio.ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
        } catch (java.io.IOException e) {
            AlteredAether.LOGGER.error("Couldn't write the skirt grid {}", name, e);
        }
    }

    /**
     * The biome groups round the benchmark, 2 blocks a pixel: other biomes grey, peaks pale blue, woodland green, dunes
     * yellow; red where other biomes are cut back from ours (36 blocks, the gap's middle width), orange where two of
     * our groups each give up half.
     */
    private static void edgeMap(ServerLevel aether, String name, int minX, int minZ, int maxX, int maxZ) {
        var field = com.linc.alteredaether.worldgen.density.BiomeEdgeField.get(THE_AETHER);
        if (field == null) {
            AlteredAether.LOGGER.warn("Edge map: no biome edge field for the Aether");
            return;
        }
        var solver = field.solver(com.linc.alteredaether.worldgen.region.BiomeGroups.GROUP, 40);
        var ours = field.solver(com.linc.alteredaether.worldgen.region.BiomeGroups.OURS, 64);
        int scale = 2, w = (maxX - minX) / scale, h = (maxZ - minZ) / scale;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(w, h,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        int[] colours = {0x808080, 0xC8DCF0, 0x3C963C, 0xE6C850};
        int[] counts = new int[4];
        int near = 0, wide = 0;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int x = minX + i * scale, z = minZ + j * scale;
                int group = solver.classAt(x, z);
                double d = solver.distance(x, z), signed = ours.signed(x, z, 1);
                counts[group]++;
                int colour = colours[group];
                if (signed < 0 && signed > -36) {
                    colour = 0xD03030;
                    near++;
                } else if (signed > 0 && d < 18 && signed - d > 4) {
                    colour = 0xE08A30;
                    wide++;
                }
                image.setRGB(i, j, colour);
            }
        }
        massMap(aether, name.replace("_groups", "_mass"), minX, minZ, maxX, maxZ);
        // A profile across the area's middle, east-west: the ground's height and how far inside a flower bowl.
        var bowl = field.solver(com.linc.alteredaether.worldgen.region.BiomeGroups.BOWL, 64);
        StringBuilder profile = new StringBuilder();
        int midZ = (minZ + maxZ) / 2;
        for (int x = minX; x <= maxX; x += 8) {
            int ground = aether.getHeight(Heightmap.Types.OCEAN_FLOOR, x, midZ);
            profile.append(String.format(" %d:%.0f", ground, bowl.inside(x, midZ, 1)));
        }
        AlteredAether.LOGGER.info("Profile {} (ground:bowl depth, every 8 blocks):{}", name, profile);
        AlteredAether.LOGGER.info("Edge map {}: other {} peaks {} woodland {} dunes {} pixels; other cut {}, ours cut {}", name, counts[0], counts[1], counts[2], counts[3], near, wide);
        try {
            java.nio.file.Path folder = aether.getServer().getServerDirectory().resolve("renders");
            java.nio.file.Files.createDirectories(folder);
            javax.imageio.ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
        } catch (java.io.IOException e) {
            AlteredAether.LOGGER.error("Couldn't write the edge map {}", name, e);
        }
    }

    /**
     * The island mass round the benchmark ({@code ColumnMassField}), 2 blocks a pixel: the thickness of island in each
     * column as grey (white is 64 blocks or more), tinted red where the smoothed mass is under 4 (shed outright) and
     * orange under 8 (partly shed). Logs a histogram of the mass.
     */
    private static void massMap(ServerLevel aether, String name, int minX, int minZ, int maxX, int maxZ) {
        var field = com.linc.alteredaether.worldgen.density.ColumnMassField.get(THE_AETHER);
        if (field == null) {
            return;
        }
        int scale = 2, w = (maxX - minX) / scale, h = (maxZ - minZ) / scale;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(w, h,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        int[] histogram = new int[9];
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int x = minX + i * scale, z = minZ + j * scale;
                double mass = field.mass(x, z), thick = field.thickness(x, z);
                histogram[Math.min(8, (int) (mass / 4))]++;
                int g = (int) Math.min(255, thick * 4);
                int colour = thick <= 0 ? 0x1A1E33 : (g << 16) | (g << 8) | g;
                if (thick > 0 && mass < 4) {
                    colour = 0xD03030;
                } else if (thick > 0 && mass < 8) {
                    colour = 0xE08A30;
                }
                image.setRGB(i, j, colour);
            }
        }
        AlteredAether.LOGGER.info("Mass map {}: pixels by mass in steps of 4 (last 32+): {}", name,
                java.util.Arrays.toString(histogram));
        try {
            java.nio.file.Path folder = aether.getServer().getServerDirectory().resolve("renders");
            java.nio.file.Files.createDirectories(folder);
            javax.imageio.ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
        } catch (java.io.IOException e) {
            AlteredAether.LOGGER.error("Couldn't write the mass map {}", name, e);
        }
    }

    /**
     * Generates every Aether chunk within {@code radius} chunks of the origin, logs how long it took, then stops
     * the server. All chunks are requested at once, so the time reflects worldgen throughput across the worker
     * threads, as when a player explores.
     */
    private static void benchmark(MinecraftServer server, int radius, String biome) {
        ServerLevel aether = server.getLevel(THE_AETHER);
        if (aether == null) {
            AlteredAether.LOGGER.error("Worldgen benchmark: the Aether isn't loaded");
            server.halt(false);
            return;
        }
        // Requested from a background thread: on the server thread, getChunkFuture blocks until the chunk is done,
        // which would stall the very thread that drives chunk generation.
        CompletableFuture.runAsync(() -> {
            // Far from any chunk generated by an earlier run, so every chunk here is generated fresh.
            BlockPos origin = new BlockPos((2000 + (int) (System.currentTimeMillis() / 1000 % 1000) * 64) * 16, 64, 0);
            List<BlockPos> spots = new ArrayList<>();
            if (biome != null && biome.startsWith("landmark:")) {
                LandmarkType type = LandmarkType.valueOf(biome.substring(9).toUpperCase(java.util.Locale.ROOT));
                LandmarkField field = LandmarkField.get(THE_AETHER);
                Landmark site = field == null ? null : field.nearest(type, origin.getX(), origin.getZ(), 60000).orElse(null);
                if (site == null) {
                    AlteredAether.LOGGER.error("Worldgen benchmark: no {} within 60000 blocks", type.id());
                    return;
                }
                origin = new BlockPos(site.x(), 64, site.z());
                AlteredAether.LOGGER.info("Worldgen benchmark: centred on {} at {} (extent {})", type.id(),
                        origin.toShortString(), String.format("%.0f", site.extent()));
            } else if (biome != null && biome.equals("giant:" + AACommands.MIRE)) {
                GroveField field = GroveField.get(THE_AETHER);
                Mire site = field == null ? null : field.nearestMire(origin.getX(), origin.getZ(), 60000).orElse(null);
                if (site == null) {
                    AlteredAether.LOGGER.error("Worldgen benchmark: no mire within 60000 blocks");
                    return;
                }
                origin = new BlockPos(site.x(), 64, site.z());
                AlteredAether.LOGGER.info("Worldgen benchmark: centred on a mire at {} (water {}, {} islets)",
                        origin.toShortString(), site.water(), site.islets().size());
            } else if (biome != null && biome.startsWith("break:")) {
                GroveField field = GroveField.get(THE_AETHER);
                var kind = com.linc.alteredaether.worldgen.grove.BreakField.Kind.valueOf(biome.substring(6)
                        .toUpperCase(java.util.Locale.ROOT));
                var site = field == null || field.breaks() == null ? null
                        : field.breaks().nearest(kind, origin.getX(), origin.getZ(), 60000).orElse(null);
                if (site == null) {
                    AlteredAether.LOGGER.error("Worldgen benchmark: no {} break within 60000 blocks", kind.id());
                    return;
                }
                origin = new BlockPos((int) site.x(), 64, (int) site.z());
                AlteredAether.LOGGER.info("Worldgen benchmark: centred on a {} break at {} (gap {}, sunk {}, radius {})",
                        kind.id(), origin.toShortString(), String.format("%.1f", site.gap()),
                        String.format("%.1f", site.sink()), String.format("%.0f", site.radius()));
            } else if (biome != null && biome.startsWith("giant:")) {
                GiantType type = GiantType.valueOf(biome.substring(6).toUpperCase(java.util.Locale.ROOT));
                GroveField field = GroveField.get(THE_AETHER);
                Giant site = field == null ? null : field.nearest(type, origin.getX(), origin.getZ(), 60000).orElse(null);
                if (site == null) {
                    AlteredAether.LOGGER.error("Worldgen benchmark: no {} within 60000 blocks", type.id());
                    return;
                }
                origin = new BlockPos(site.x(), 64, site.z());
                AlteredAether.LOGGER.info("Worldgen benchmark: centred on {} at {} (extent {})", type.id(),
                        origin.toShortString(), String.format("%.0f", site.extent()));
            } else if (biome != null && biome.startsWith("atoll:")) {
                // The nearest atoll-grid site of a type (atoll_lagoon), optionally "ring" or "double"
                // (atolls only; doubles have a grid of their own).
                String[] spec = biome.substring(6).split("/");
                BasinType type = BasinType.valueOf(spec[0].toUpperCase(java.util.Locale.ROOT));
                String shape = spec.length > 1 ? spec[1] : "";
                BasinField field = BasinField.get(THE_AETHER);
                boolean twin = shape.equals("double");
                int cell = twin ? field.doubleCell() : field.atollCell(), ox = Math.floorDiv(origin.getX(), cell);
                AtollSite found = null;
                for (int r = 0; r < 40 && found == null; r++) {
                    for (int cx = ox - r; cx <= ox + r && found == null; cx++) {
                        for (int cz = -r; cz <= r && found == null; cz++) {
                            if (Math.max(Math.abs(cx - ox), Math.abs(cz)) != r) {
                                continue;
                            }
                            found = twin ? field.doubleInCell(cx, cz).filter(s -> s.type() == type).orElse(null)
                                    : field.atollInCell(cx, cz).filter(s -> s.type() == type
                                    && (shape.isEmpty() || shape.equals("ring") == s.isRing())).orElse(null);
                        }
                    }
                }
                if (found == null) {
                    AlteredAether.LOGGER.error("Worldgen benchmark: no {} found", biome);
                    return;
                }
                origin = new BlockPos(found.x(), 64, found.z());
                AlteredAether.LOGGER.info("Worldgen benchmark: centred on {} at {}: {}", biome, origin.toShortString(),
                        found);
            } else if (biome != null && biome.equals("stack")) {
                // The nearest plateau sea stack, searched in growing squares.
                var field = com.linc.alteredaether.worldgen.landmark.StackField.get(THE_AETHER);
                com.linc.alteredaether.worldgen.landmark.StackField.Stack found = null;
                // Stacks stand off the plateau's cliffs: start from the nearest plateau.
                var plateau = aether.findClosestBiome3d(b -> b.is(com.linc.alteredaether.worldgen.AABiomes.AETHER_PLATEAU),
                        origin, 12000, 32, 64);
                if (plateau != null) {
                    origin = plateau.getFirst().atY(64);
                }
                for (int r = 128; r <= 1024 && field != null && found == null; r *= 2) {
                    var stacks = field.touching(origin.getX() - r, origin.getZ() - r, origin.getX() + r,
                            origin.getZ() + r);
                    if (!stacks.isEmpty()) {
                        int ox = origin.getX(), oz = origin.getZ();
                        found = stacks.stream().min(java.util.Comparator.comparingDouble(st ->
                                Math.hypot(st.x() - ox, st.z() - oz))).orElse(null);
                    }
                }
                if (found == null) {
                    AlteredAether.LOGGER.error("Worldgen benchmark: no stack found");
                    return;
                }
                origin = new BlockPos(found.x(), 64, found.z());
                AlteredAether.LOGGER.info("Worldgen benchmark: centred on a stack at {}: {}", origin.toShortString(),
                        found);
            } else if (biome != null && (biome.equals("lake") || biome.equals("lake:poison")
                    || biome.equals("lake:frozen"))) {
                // The nearest big lake, searched outward ring by ring over the lake grid.
                BasinType type = biome.equals("lake") ? BasinType.LAKE
                        : biome.equals("lake:frozen") ? BasinType.FROZEN_LAKE : BasinType.POISON_LAKE;
                BasinField field = BasinField.get(THE_AETHER);
                int cell = field == null ? 1 : field.lakeCell();
                int ox = Math.floorDiv(origin.getX(), cell), oz = Math.floorDiv(origin.getZ(), cell);
                BasinSite found = null;
                for (int r = 0; r < 40 && field != null && found == null; r++) {
                    for (int cx = ox - r; cx <= ox + r && found == null; cx++) {
                        for (int cz = oz - r; cz <= oz + r && found == null; cz++) {
                            if (Math.max(Math.abs(cx - ox), Math.abs(cz - oz)) == r) {
                                found = field.lakeInCell(cx, cz).filter(s -> s.type() == type).orElse(null);
                            }
                        }
                    }
                }
                if (found == null) {
                    AlteredAether.LOGGER.error("Worldgen benchmark: no {} found", biome);
                    return;
                }
                origin = new BlockPos(found.x(), 64, found.z());
                AlteredAether.LOGGER.info("Worldgen benchmark: centred on a {} at {} (outline {}, water {}, depth {})",
                        biome, origin.toShortString(), String.format("%.0f", found.radius()), found.waterLevel(),
                        String.format("%.1f", found.depth()));
            } else if (biome != null && biome.equals("skirt")) {
                // The nearest rim of a low island, which wears a cloud skirt.
                SkirtField field = SkirtField.get(THE_AETHER);
                int[] rim = field == null ? null : field.nearestRim(origin.getX(), origin.getZ(), 4000).orElse(null);
                if (rim == null) {
                    AlteredAether.LOGGER.error("Worldgen benchmark: no cloud skirt within 4000 blocks{}",
                            field == null ? " (no skirt field: is this the tall Aether?)" : "");
                    return;
                }
                origin = new BlockPos(rim[0], rim[1], rim[2]);
                AlteredAether.LOGGER.info("Worldgen benchmark: centred on a low island's rim at {}", origin.toShortString());
            } else if (biome != null && (biome.equals("sea") || biome.equals("sea:edge"))) {
                // Open cloud sea, or its border where the sheet breaks up, searched outward on a 128-block spiral.
                CloudSea sea = CloudSea.terrain(THE_AETHER);
                boolean edge = biome.equals("sea:edge");
                BlockPos found = null;
                for (int r = 0; r < 100 && sea != null && found == null; r++) {
                    for (int i = -r; i <= r && found == null; i++) {
                        for (int[] p : new int[][]{{i, -r}, {i, r}, {-r, i}, {r, i}}) {
                            int x = origin.getX() + p[0] * 128, z = origin.getZ() + p[1] * 128;
                            double w = sea.weight(x, z);
                            if (edge ? w > 0.25 && w < 0.6 : w > 0.98 && sea.weight(x + 160, z) > 0.98
                                    && sea.weight(x - 160, z) > 0.98) {
                                found = new BlockPos(x, -20, z);
                                break;
                            }
                        }
                    }
                }
                if (found == null) {
                    AlteredAether.LOGGER.error("Worldgen benchmark: no {} found{}", biome,
                            sea == null ? " (no cloud sea: is this the tall Aether?)" : "");
                    return;
                }
                origin = found;
                AlteredAether.LOGGER.info("Worldgen benchmark: centred on {} at {}", biome, origin.toShortString());
            } else if (biome != null && biome.matches("-?\\d+:-?\\d+")) {
                String[] xz = biome.split(":");
                origin = new BlockPos(Integer.parseInt(xz[0]), 64, Integer.parseInt(xz[1]));
            } else if (biome != null && biome.startsWith("spots:")) {
                // Several places at once, x:y:z;x:y:z, generated round each (the radius applies to each spot).
                for (String spot : biome.substring(6).split(";")) {
                    String[] xyz = spot.split(":");
                    spots.add(new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
                }
                origin = spots.getFirst();
            } else if (biome != null && biome.startsWith("core:")) {
                // The nearest spot well inside the biome: it holds at the centre and 96 blocks out every way.
                ResourceLocation id = ResourceLocation.parse(biome.substring(5));
                BlockPos found = null;
                for (int r = 0; r < 120 && found == null; r++) {
                    for (int i = -r; i <= r && found == null; i++) {
                        for (int[] p : new int[][]{{i, -r}, {i, r}, {-r, i}, {r, i}}) {
                            int x = origin.getX() + p[0] * 64, z = origin.getZ() + p[1] * 64;
                            boolean inside = true;
                            for (int d = 0; d < 9 && inside; d++) {
                                int sx = x + (d % 3 - 1) * 96, sz = z + (d / 3 - 1) * 96;
                                inside = aether.getUncachedNoiseBiome(sx >> 2, 16, sz >> 2).is(id);
                            }
                            if (inside) {
                                found = new BlockPos(x, 64, z);
                                break;
                            }
                        }
                    }
                }
                if (found == null) {
                    AlteredAether.LOGGER.error("Worldgen benchmark: no core of {} found", biome);
                    return;
                }
                origin = found;
                AlteredAether.LOGGER.info("Worldgen benchmark: centred on {} at {}", biome, origin.toShortString());
            } else if (biome != null) {
                ResourceLocation id = ResourceLocation.parse(biome);
                var found = aether.findClosestBiome3d(b -> b.is(id), origin, 12000, 32, 64);
                if (found == null) {
                    AlteredAether.LOGGER.error("Worldgen benchmark: no {} within 12000 blocks", biome);
                    return;
                }
                origin = found.getFirst();
                AlteredAether.LOGGER.info("Worldgen benchmark: centred on {} at {}", biome, origin.toShortString());
            }
            java.util.Set<ChunkPos> area = new java.util.LinkedHashSet<>();
            for (BlockPos spot : spots.isEmpty() ? List.of(origin) : spots) {
                ChunkPos centre = new ChunkPos(spot);
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        area.add(new ChunkPos(centre.x + x, centre.z + z));
                    }
                }
            }
            List<ChunkPos> positions = new ArrayList<>(area);
            long start = System.nanoTime();
            // Forced tickets keep each chunk loaded until it finishes; a plain request's ticket can expire first.
            // Added as tickets rather than with setChunkForced, which loads each chunk there and then, on the server
            // thread: a large area on a busy machine would hold one tick past the watchdog's limit.
            server.submit(() -> positions.forEach(p -> aether.getChunkSource()
                    .addRegionTicket(net.minecraft.server.level.TicketType.FORCED, p, 2, p))).join();
            List<CompletableFuture<ChunkResult<ChunkAccess>>> chunks = new ArrayList<>();
            for (ChunkPos p : positions) {
                chunks.add(aether.getChunkSource().getChunkFuture(p.x, p.z, ChunkStatus.FULL, true));
            }
            CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).join();
            double seconds = (System.nanoTime() - start) / 1e9;
            long generated = chunks.stream().filter(f -> f.join().isSuccess()).count();
            AlteredAether.LOGGER.info("Worldgen benchmark: {} of {} Aether chunks generated in {} s ({} ms per chunk)",
                    generated, chunks.size(), String.format("%.2f", seconds),
                    String.format("%.1f", seconds * 1000 / Math.max(1, generated)));
            server.submit(() -> surfaceTally(aether, positions)).join();
            server.submit(() -> decorationTally(aether, positions)).join();
            // Long enough for freshly placed water to tick: any basin that leaks shows up as flowing water.
            try {
                Thread.sleep(8000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            server.submit(() -> basinTally(aether, positions)).join();
            server.submit(() -> caveTally(aether, positions)).join();
            server.submit(() -> mireTally(aether, positions)).join();
            server.submit(() -> landmarkTally(aether, positions)).join();
            // The debug command at the centre, so its output can be checked headlessly.
            BlockPos centrePos = origin.atY(aether.getHeight(Heightmap.Types.MOTION_BLOCKING, origin.getX(), origin.getZ()));
            server.submit(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack()
                    .withLevel(aether).withPosition(net.minecraft.world.phys.Vec3.atCenterOf(centrePos)),
                    AlteredAether.MOD_ID + " debug")).join();
            String focus = System.getProperty("altered_aether.focus", "");
            if (Boolean.getBoolean("altered_aether.render") && !focus.isEmpty()) {
                // Close-ups of the first few of a block in the area, such as the rods crowning copper crystals.
                List<BlockPos> found = server.submit(() -> find(aether, positions, focus, 3)).join();
                for (int i = 0; i < found.size(); i++) {
                    BlockPos at = found.get(i);
                    String name = "focus_" + focus.replace(':', '_') + "_" + i;
                    server.submit(() -> WorldRender.render(aether, name, at.getX() - 14, at.getZ() - 14,
                            at.getX() + 14, at.getZ() + 14, at.getY() - 40, at.getY() + 4, 8)).join();
                    // The same with the terrain left out, as if seen through the rock: veins and roots show.
                    String bare = BuiltInRegistries.BLOCK.getKey(aether.getBlockState(at).getBlock()).getPath();
                    String family = bare.contains("_") ? bare.substring(0, bare.indexOf('_')) : bare;
                    server.submit(() -> WorldRender.render(aether, name + "_only", at.getX() - 14, at.getZ() - 14,
                            at.getX() + 14, at.getZ() + 14, at.getY() - 40, at.getY() + 4, 8,
                            state -> BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("copper")
                                    || BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains(family))).join();
                }
            }
            if (Boolean.getBoolean("altered_aether.render")) {
                int r = radius * 16;
                edgeMap(aether, (biome == null ? "bench" : biome.replace(':', '_')) + "_groups", origin.getX() - r,
                        origin.getZ() - r, origin.getX() + r, origin.getZ() + r);
                // Sections east-west and north-south through the middle, to see inside the islands (caves).
                String slice = (biome == null ? "bench" : biome.replace(':', '_')) + "_slice";
                int sx = origin.getX(), sz = origin.getZ(), sr = Math.min(r, 192);
                server.submit(() -> WorldRender.render(aether, slice + "_x", sx - sr, sz, sx + sr, sz, -64, 260, 3))
                        .join();
                server.submit(() -> WorldRender.render(aether, slice + "_z", sx, sz - sr, sx, sz + sr, -64, 260, 3))
                        .join();
                for (int i = 0; i < spots.size(); i++) {
                    skirtRenders(server, aether, "spot" + i, spots.get(i));
                }
                if (biome != null && biome.startsWith("core:")) {
                    skirtRenders(server, aether, "core", origin.atY(0));
                }
            }
            if (Boolean.getBoolean("altered_aether.render") && biome != null && biome.startsWith("sea")) {
                // The sea from above, the cloud alone, a plan through the sheet's band, and sections east-west and
                // north-south from the roots to the ground above.
                int sx = origin.getX(), sz = origin.getZ(), r = Math.min(radius * 16, 160);
                server.submit(() -> WorldRender.render(aether, "sea", sx - r, sz - r, sx + r, sz + r, -64, 127, 2))
                        .join();
                server.submit(() -> WorldRender.render(aether, "sea_cloud", sx - r, sz - r, sx + r, sz + r, -64, 127, 2,
                        state -> state.is(com.aetherteam.aether.block.AetherBlocks.COLD_AERCLOUD.get()))).join();
                server.submit(() -> WorldRender.render(aether, "sea_plan", sx - r, sz - r, sx + r, sz + r, -36, -6, 2))
                        .join();
                server.submit(() -> WorldRender.render(aether, "sea_slice_x", sx - r, sz, sx + r, sz, -64, 100, 3))
                        .join();
                server.submit(() -> WorldRender.render(aether, "sea_slice_z", sx, sz - r, sx, sz + r, -64, 100, 3))
                        .join();
                server.submit(() -> seaTally(aether, positions)).join();
            }
            if (Boolean.getBoolean("altered_aether.render") && biome != null && biome.equals("skirt")) {
                // Sections through the rim, east-west and north-south, a plan cut through the collar, and the same
                // plan with only the cloud drawn.
                int sx = origin.getX(), sz = origin.getZ(), sy = origin.getY();
                server.submit(() -> WorldRender.render(aether, "skirt_slice_x", sx - 64, sz, sx + 64, sz, sy - 40,
                        sy + 24, 4)).join();
                server.submit(() -> WorldRender.render(aether, "skirt_slice_z", sx, sz - 64, sx, sz + 64, sy - 40,
                        sy + 24, 4)).join();
                server.submit(() -> WorldRender.render(aether, "skirt_plan", sx - 64, sz - 64, sx + 64, sz + 64,
                        sy - 9, sy - 2, 4)).join();
                server.submit(() -> WorldRender.render(aether, "skirt_cloud", sx - 64, sz - 64, sx + 64, sz + 64,
                        sy - 40, sy + 24, 4, state -> state.is(com.aetherteam.aether.block.AetherBlocks.COLD_AERCLOUD.get())))
                        .join();
            }
            if (Boolean.getBoolean("altered_aether.render") && biome != null && biome.equals("giant:" + AACommands.MIRE)) {
                // Sections through the lake and a plan at its water line, so islets, roots and the bed show.
                GroveField field = GroveField.get(THE_AETHER);
                Mire site = field == null ? null : field.nearestMire(origin.getX(), origin.getZ(), 64).orElse(null);
                if (site != null) {
                    int r = (int) Math.ceil(site.extent());
                    server.submit(() -> WorldRender.render(aether, "mire_slice_x", site.x() - r, site.z(),
                            site.x() + r, site.z(), site.water() - 12, site.water() + 90, 4)).join();
                    server.submit(() -> WorldRender.render(aether, "mire_slice_z", site.x(), site.z() - r,
                            site.x(), site.z() + r, site.water() - 12, site.water() + 90, 4)).join();
                    server.submit(() -> WorldRender.render(aether, "mire_plan", site.x() - r, site.z() - r,
                            site.x() + r, site.z() + r, site.water() - 6, site.water() + 2, 4)).join();
                }
            } else if (Boolean.getBoolean("altered_aether.render") && biome != null && biome.startsWith("break:")) {
                // A section along the break's normal, through the bridge, and the same across it.
                GroveField field = GroveField.get(THE_AETHER);
                var kind = com.linc.alteredaether.worldgen.grove.BreakField.Kind.valueOf(biome.substring(6)
                        .toUpperCase(java.util.Locale.ROOT));
                var site = field == null || field.breaks() == null ? null
                        : field.breaks().nearest(kind, origin.getX(), origin.getZ(), 64).orElse(null);
                if (site != null) {
                    int cx = (int) site.x(), cz = (int) site.z(), r = 90, g = site.ground();
                    server.submit(() -> WorldRender.render(aether, "break_slice_x", cx - r, cz, cx + r, cz, g - 70,
                            g + 50, 4)).join();
                    server.submit(() -> WorldRender.render(aether, "break_slice_z", cx, cz - r, cx, cz + r, g - 70,
                            g + 50, 4)).join();
                }
            } else if (Boolean.getBoolean("altered_aether.render") && biome != null
                    && biome.equals("landmark:village_tree")) {
                // Sections through the village tree, and plans cut through its street and its upper gallery.
                LandmarkField field = LandmarkField.get(THE_AETHER);
                Landmark site = field == null ? null : field.nearest(LandmarkType.VILLAGE_TREE, origin.getX(),
                        origin.getZ(), 64).orElse(null);
                if (site != null) {
                    int r = (int) Math.ceil(site.extent());
                    int street = site.visitY() - 1;
                    server.submit(() -> WorldRender.render(aether, "village_slice_x", site.x() - r, site.z(),
                            site.x() + r, site.z(), street - 30, street + 90, 4)).join();
                    server.submit(() -> WorldRender.render(aether, "village_slice_z", site.x(), site.z() - r,
                            site.x() + r, site.z() + r, street - 30, street + 90, 4)).join();
                    server.submit(() -> WorldRender.render(aether, "village_street", site.x() - 40, site.z() - 40,
                            site.x() + 40, site.z() + 40, street - 1, street + 2, 6)).join();
                    server.submit(() -> WorldRender.render(aether, "village_upper", site.x() - 40, site.z() - 40,
                            site.x() + 40, site.z() + 40, street + 10, street + 13, 6)).join();
                    // The top of the bulb under the crown, where the fields are.
                    server.submit(() -> WorldRender.render(aether, "village_top", site.x() - 30, site.z() - 30,
                            site.x() + 30, site.z() + 30, street + 18, street + 40, 6)).join();
                    // What the village holds, by block, to check its furnishing without a client.
                    server.submit(() -> {
                        java.util.Map<String, Integer> tally = new java.util.TreeMap<>();
                        java.util.Set<String> wanted = java.util.Set.of("ladder", "aether_farmland", "wheat",
                                "carrots", "potatoes", "glowstone", "skyroot_planks", "skyroot_stairs",
                                "skyjade_lantern", "soul_lantern", "moa_totem", "aerwhale_totem", "zephyr_totem",
                                "water", "aether_mud", "altar", "squash_stem", "attached_squash_stem", "blue_squash", "green_squash",
                                "purple_squash", "berry_bush", "berry_bush_stem");
                        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                        for (int bx = site.x() - r; bx <= site.x() + r; bx++) {
                            for (int bz = site.z() - r; bz <= site.z() + r; bz++) {
                                for (int by = street - 30; by <= street + 60; by++) {
                                    String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                            .getKey(aether.getBlockState(pos.set(bx, by, bz)).getBlock()).getPath();
                                    if (wanted.contains(id)) {
                                        tally.merge(id, 1, Integer::sum);
                                    }
                                }
                            }
                        }
                        AlteredAether.LOGGER.info("Village tree holds: {} ({})", tally, site.detail());
                    }).join();
                }
            } else if (Boolean.getBoolean("altered_aether.render") && biome != null && biome.startsWith("giant:")) {
                // Sections through the giant: a slice north to south and one east to west through its centre, and a
                // plan cut just above its ground, so hollows and tunnels show.
                GroveField field = GroveField.get(THE_AETHER);
                GiantType type = GiantType.valueOf(biome.substring(6).toUpperCase(java.util.Locale.ROOT));
                Giant site = field == null ? null : field.nearest(type, origin.getX(), origin.getZ(), 64).orElse(null);
                if (site != null) {
                    String name = "giant_" + type.id();
                    int r = (int) Math.ceil(site.extent());
                    server.submit(() -> WorldRender.render(aether, name + "_slice_x", site.x() - r, site.z(),
                            site.x() + r, site.z(), site.base() - 24, site.base() + 120, 4)).join();
                    server.submit(() -> WorldRender.render(aether, name + "_slice_z", site.x(), site.z() - r,
                            site.x(), site.z() + r, site.base() - 24, site.base() + 120, 4)).join();
                    server.submit(() -> WorldRender.render(aether, name + "_plan", site.x() - r, site.z() - r,
                            site.x() + r, site.z() + r, site.base() - 2, site.base() + 3, 4)).join();
                    // The underside of its island, down to the tips of its great roots, and the same with only the
                    // wood and hanging roots drawn, so the rock hides nothing.
                    int under = Math.min(site.bounds().minY(), site.base() - 60) - 4;
                    server.submit(() -> WorldRender.render(aether, name + "_under", site.x() - r, site.z() - r,
                            site.x() + r, site.z() + r, under, site.base(), 4)).join();
                    server.submit(() -> WorldRender.render(aether, name + "_under_roots", site.x() - r, site.z() - r,
                            site.x() + r, site.z() + r, under, site.base(), 4,
                            state -> state.is(net.minecraft.tags.BlockTags.LOGS)
                                    || state.is(net.minecraft.world.level.block.Blocks.HANGING_ROOTS))).join();
                }
            }
            if (Boolean.getBoolean("altered_aether.render") && spots.isEmpty()) {
                ChunkPos first = positions.getFirst(), last = positions.getLast();
                String name = biome == null ? "bench" : biome.replace(':', '_').replace('/', '_');
                server.submit(() -> WorldRender.render(aether, name, first.getMinBlockX(), first.getMinBlockZ(),
                        last.getMaxBlockX(), last.getMaxBlockZ())).join();
            }
            server.submit(() -> positions.forEach(p -> aether.getChunkSource()
                    .removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, p, 2, p))).join();
        }, Util.backgroundExecutor()).whenCompleteAsync((done, error) -> {
            if (error != null) {
                AlteredAether.LOGGER.error("Worldgen benchmark failed", error);
            }
            server.halt(false);
        }, server);
    }

    /** Up to {@code limit} positions of a block in the given chunks, well apart. */
    private static List<BlockPos> find(ServerLevel level, List<ChunkPos> positions, String block, int limit) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (ChunkPos chunk : positions) {
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                    for (int y = level.getMaxBuildHeight() - 1; y >= level.getMinBuildHeight(); y--) {
                        var state = level.getBlockState(cursor.set(x, y, z));
                        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(block)) {
                            continue;
                        }
                        BlockPos at = cursor.immutable();
                        if (found.stream().noneMatch(p -> p.distManhattan(at) < 48)) {
                            found.add(at);
                            if (found.size() >= limit) {
                                return found;
                            }
                        }
                    }
                }
            }
        }
        return found;
    }

    /** Blocks that mark this mod's decoration; counted per biome by decorationTally. */
    private static final java.util.Set<String> MARKERS = java.util.Set.of(
            "minecraft:copper_block", "minecraft:exposed_copper", "minecraft:weathered_copper",
            "minecraft:oxidized_copper", "minecraft:lightning_rod", "aether:ambrosium_block", "minecraft:blue_ice",
            "minecraft:packed_ice", "minecraft:snow", "aether:aerogel", "minecraft:chain", "aether:ambrosium_ore",
            "aether:zanite_ore", "aether:gravitite_ore", "minecraft:iron_ore", "create:deepslate_zinc_ore",
            "altered_aether:ambrosium_campfire", "aether:ambrosium_torch", "minecraft:chest", "aether:skyroot_bed",
            "aether:skyroot_fence", "aether:aether_dirt_path", "deep_aether:skyjade_lantern", "deep_aether:ambrosium_tiki_torch",
            "deep_aether:glowing_vine", "deep_aether:aether_moss_carpet", "aether:golden_oak_log", "minecraft:lantern",
            "minecraft:torch", "minecraft:candle", "minecraft:soul_lantern", "deep_aether:skyjade_block", "dried_ghast:dried_ghast",
            "deep_aether:moa_totem", "deep_aether:zephyr_totem", "deep_aether:aerwhale_totem",
            "aether:cold_aercloud", "aether:blue_aercloud", "aether:golden_aercloud",
            "deep_aether:sterling_aercloud", "oreganized:silver_ore", "aether_overworld_ores:holystone_copper_ore",
            "aether_overworld_ores:holystone_emerald_ore", "aether_overworld_ores:holystone_gold_ore",
            "aether_overworld_ores:holystone_iron_ore", "aether_overworld_ores:holystone_lapis_ore",
            "aether_overworld_ores:holystone_nickel_ore", "aether_overworld_ores:holystone_redstone_ore",
            "aether_overworld_ores:holystone_zinc_ore", "aether_overworld_ores:holystone_coal_ore",
            "aether_overworld_ores:holystone_diamond_ore");

    /**
     * Counts the marker blocks per biome over the given chunks, and every entity by type. Boulders and the like have
     * no marker of their own: they show in the surface tally instead.
     */
    private static void decorationTally(ServerLevel level, List<ChunkPos> positions) {
        Map<String, Map<String, Integer>> byBiome = new TreeMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (ChunkPos chunk : positions) {
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                    String biome = null;
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        var state = level.getBlockState(cursor.set(x, y, z));
                        if (state.isAir()) {
                            continue;
                        }
                        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                        if (MARKERS.contains(id)) {
                            if (biome == null) {
                                biome = level.getBiome(cursor).unwrapKey().map(k -> k.location().getPath()).orElse("?");
                            }
                            byBiome.computeIfAbsent(biome, k -> new TreeMap<>()).merge(id.substring(id.indexOf(':') + 1), 1,
                                    Integer::sum);
                        }
                    }
                }
            }
        }
        byBiome.forEach((biome, blocks) -> AlteredAether.LOGGER.info("Decoration {}: {}", biome, blocks));
        Map<String, Integer> entities = new TreeMap<>();
        level.getAllEntities().forEach(entity -> entities.merge(
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(), 1, Integer::sum));
        AlteredAether.LOGGER.info("Entities: {}", entities);
        AlteredAether.LOGGER.info("Ambrosium campfire shares the campfire block entity: {}",
                net.minecraft.world.level.block.entity.BlockEntityType.CAMPFIRE.isValid(
                        com.linc.alteredaether.registry.AARegistries.AMBROSIUM_CAMPFIRE.get().defaultBlockState()));
        AlteredAether.LOGGER.info("Features placed: {}", com.linc.alteredaether.worldgen.feature.FeatureStats.snapshot());
    }

    /** The swamp lakes over the given chunks, what they are made of, and any of their water that has started to flow. */
    private static void mireTally(ServerLevel level, List<ChunkPos> positions) {
        GroveField field = GroveField.get(level.dimension());
        if (field == null) {
            return;
        }
        ChunkPos first = positions.getFirst(), last = positions.getLast();
        List<Mire> mires = field.miresTouching(first.getMinBlockX(), first.getMinBlockZ(), last.getMaxBlockX(),
                last.getMaxBlockZ());
        Map<String, Integer> blocks = new TreeMap<>();
        List<String> leaks = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (ChunkPos chunk : positions) {
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                    for (Mire mire : mires) {
                        if (mire.reach(x + 0.5, z + 0.5) > 1.3) {
                            continue;
                        }
                        for (int y = mire.water() - 16; y <= mire.water() + 2; y++) {
                            var state = level.getBlockState(cursor.set(x, y, z));
                            var fluid = state.getFluidState();
                            String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                            if (!fluid.isEmpty() && !fluid.isSource()) {
                                blocks.merge("flowing", 1, Integer::sum);
                                if (leaks.size() < 12) {
                                    leaks.add(cursor.toShortString() + " below=" + BuiltInRegistries.BLOCK.getKey(
                                            level.getBlockState(cursor.below()).getBlock()).getPath());
                                }
                            } else if (name.contains("root") || name.contains("mud") || name.equals("lily_pad")
                                    || name.contains("seagrass") || name.contains("cattail") || name.equals("water")) {
                                blocks.merge(name, 1, Integer::sum);
                            }
                        }
                        break;
                    }
                }
            }
        }
        AlteredAether.LOGGER.info("Mires: {} lakes; blocks {}", mires.size(), blocks);
        mires.forEach(mire -> AlteredAether.LOGGER.info("  mire at {} {} radius {} water {} islets {}", mire.x(),
                mire.z(), String.format("%.1f", mire.radius()), mire.water(), mire.islets().size()));
        if (!leaks.isEmpty()) {
            AlteredAether.LOGGER.info("  flowing mire water at {}", leaks);
        }
    }

    /** The basins over the given chunks, the water they hold, and any water that has since started to flow. */
    /**
     * Air shut inside the islands: gaps of air at most 20 tall between rock above and rock below in a column, split by
     * how far under the ground they lie. Mostly caves, plus the odd overhang; compare runs, not absolutes.
     */
    private static void caveTally(ServerLevel level, List<ChunkPos> positions) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long enclosed = 0, deep = 0, columns = 0, wet = 0;
        for (ChunkPos chunk : positions) {
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    int x = chunk.getMinBlockX() + dx, z = chunk.getMinBlockZ() + dz;
                    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                    int gap = 0, lastSolid = Integer.MIN_VALUE;
                    boolean seenRock = false, gapWet = false;
                    for (int y = Math.min(top, 128); y >= -64; y--) {
                        var state = level.getBlockState(pos.set(x, y, z));
                        boolean rock = state.isSolidRender(level, pos);
                        if (rock) {
                            if (seenRock && gap > 0 && gap <= 20) {
                                enclosed += gap;
                                if (top - lastSolid > 12) {
                                    deep += gap;
                                }
                                wet += gapWet ? 1 : 0;
                            }
                            seenRock = true;
                            gap = 0;
                            gapWet = false;
                            lastSolid = y;
                        } else if (seenRock) {
                            gap++;
                            gapWet |= !state.getFluidState().isEmpty();
                        }
                    }
                    columns++;
                }
            }
        }
        AlteredAether.LOGGER.info("Caves: {} enclosed air blocks over {} columns ({} more than 12 under the ground); {} "
                + "enclosed gaps hold fluid", enclosed, columns, deep, wet);
    }

    private static void basinTally(ServerLevel level, List<ChunkPos> positions) {
        BasinField field = BasinField.get(level.dimension());
        if (field == null) {
            AlteredAether.LOGGER.info("Basins: no basin field in this world");
            return;
        }
        ChunkPos first = positions.getFirst(), last = positions.getLast();
        List<BasinSite> sites = field.sitesTouching(first.getMinBlockX(), first.getMinBlockZ(), last.getMaxBlockX(),
                last.getMaxBlockZ());
        Map<String, Integer> types = new TreeMap<>();
        sites.forEach(site -> types.merge(site.type().name().toLowerCase(), 1, Integer::sum));
        Map<String, Integer> blocks = new TreeMap<>();
        List<String> leaks = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (ChunkPos chunk : positions) {
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        var state = level.getBlockState(cursor.set(x, y, z));
                        var fluid = state.getFluidState();
                        if (!fluid.isEmpty()) {
                            final int fx = x, fz = z;
                            final int fy = y;
                            boolean ours = sites.stream().anyMatch(site -> site.reach(fx, fz) < 1.3
                                    && fy >= site.waterLevel() - site.maxDepth() - 4 && fy <= site.waterLevel());
                            blocks.merge((ours ? "basin_" : "other_") + (fluid.isSource() ? "source" : "flowing"), 1,
                                    Integer::sum);
                            if (ours && !fluid.isSource() && leaks.size() < 12) {
                                leaks.add(cursor.toShortString() + " below=" + BuiltInRegistries.BLOCK.getKey(
                                        level.getBlockState(cursor.below()).getBlock()).getPath()
                                        + (sites.stream().anyMatch(site -> site.inFootprint(fx, fz)) ? " wet" : " dry"));
                            }
                        }
                        if (state.is(net.minecraft.tags.BlockTags.CORAL_BLOCKS)) {
                            blocks.merge("coral", 1, Integer::sum);
                        }
                        // What a big lake is filled with and what its shore and bed are made of.
                        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                        if (id.equals("deep_aether:poison") || id.equals("deep_aether:aether_mud")
                                || id.equals("aether:quicksoil") || id.equals("minecraft:clay")) {
                            final int lx = x, lz = z;
                            if (sites.stream().anyMatch(site -> site.isLake() && site.reach(lx, lz) < 1.3)) {
                                blocks.merge("lake_" + id.substring(id.indexOf(':') + 1), 1, Integer::sum);
                            }
                        }
                    }
                }
            }
        }
        AlteredAether.LOGGER.info("Basins: {} sites {}; fluid and reef blocks {}; rejected {}; site search {} ms",
                sites.size(), types, blocks, field.rejections(), String.format("%.0f", field.searchMillis()));
        AlteredAether.LOGGER.info("Waterfalls: {} edge springs placed",
                com.linc.alteredaether.worldgen.feature.EdgeSpringFeature.PLACED.get());
        if (!leaks.isEmpty()) {
            AlteredAether.LOGGER.info("  flowing basin water at {}", leaks);
        }
        sites.forEach(site -> AlteredAether.LOGGER.info("  {} at {} {} radius {} water {}: {}", site.type(), site.x(),
                site.z(), String.format("%.1f", site.radius()), site.waterLevel(), column(level, site)));
        sites.stream().filter(BasinSite::isLake).forEach(site -> {
            var shore = com.linc.alteredaether.worldgen.feature.BasinFillFeature.lakeShore(field, site);
            AlteredAether.LOGGER.info("  lake shore: {}", shore == null ? "grass" : BuiltInRegistries.BLOCK.getKey(
                    shore.getBlock()));
        });
    }

    /**
     * A sample column through the water, from a block over the surface down past the bed, at a point half way out
     * (the middle of a ring's river), so the tally shows what each basin is made of.
     */
    private static String column(ServerLevel level, BasinSite site) {
        StringBuilder column = new StringBuilder();
        double[] reaches = site.isRiver() ? new double[]{0} : new double[]{0.15, 0.5, 0.8};
        for (double reach : reaches) {
            int x = site.x() + (int) Math.round(site.radius() * reach), z = site.z();
            if (site.isRiver()) {
                // The river's middle, due east of the centre.
                for (int d = 0, best = Integer.MAX_VALUE; d < site.extent(); d++) {
                    double r = site.reach(site.x() + d, z);
                    if (r < 1 && (best == Integer.MAX_VALUE || r < site.reach(best, z))) {
                        best = site.x() + d;
                        x = best;
                    }
                }
            }
            column.append("[").append(String.format("%.2f", reach)).append("] ");
            column.append(level.hasChunk(x >> 4, z >> 4) ? column(level, site, x, z) : "not generated").append(' ');
        }
        return column.toString();
    }

    private static String column(ServerLevel level, BasinSite site, int x, int z) {
        StringBuilder column = new StringBuilder();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        String last = null;
        int run = 0;
        for (int y = site.waterLevel() + 1; y >= site.waterLevel() - site.maxDepth() - 8; y--) {
            String block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(cursor.set(x, y, z)).getBlock()).getPath();
            if (block.equals(last)) {
                run++;
                continue;
            }
            if (last != null) {
                column.append(last).append(run > 1 ? "x" + run : "").append(' ');
            }
            last = block;
            run = 1;
        }
        return column.append(last).append(run > 1 ? "x" + run : "").toString();
    }

    /** The landmarks reaching into the given chunks. */
    private static void landmarkTally(ServerLevel level, List<ChunkPos> positions) {
        LandmarkField field = LandmarkField.get(level.dimension());
        if (field == null) {
            AlteredAether.LOGGER.info("Landmarks: no landmark field in this world");
            return;
        }
        ChunkPos first = positions.getFirst(), last = positions.getLast();
        for (Landmark site : field.touching(first.getMinBlockX(), first.getMinBlockZ(), last.getMaxBlockX(),
                last.getMaxBlockZ())) {
            AlteredAether.LOGGER.info("Landmark {} at {} {}, extent {}, top {}", site.type().id(), site.x(), site.z(),
                    String.format("%.0f", site.extent()), site.visitY());
        }
    }

    /** The topmost block of every column in the given chunks, counted per biome: a check on the surface rules. */
    private static void surfaceTally(ServerLevel level, List<ChunkPos> positions) {
        Map<String, Map<String, Integer>> byBiome = new TreeMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (ChunkPos chunk : positions) {
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                    int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
                    if (top <= level.getMinBuildHeight()) {
                        continue;
                    }
                    cursor.set(x, top, z);
                    String biome = level.getBiome(cursor).unwrapKey().map(k -> k.location().toString()).orElse("?");
                    String block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(cursor).getBlock()).toString();
                    byBiome.computeIfAbsent(biome, k -> new TreeMap<>()).merge(block, 1, Integer::sum);
                    int dx = Math.abs(level.getHeight(Heightmap.Types.OCEAN_FLOOR, x + 2, z) - 1 - top);
                    int dz = Math.abs(level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z + 2) - 1 - top);
                    byBiome.get(biome).merge(dx >= 3 || dz >= 3 ? "~steep" : "~flat", 1, Integer::sum);
                }
            }
        }
        byBiome.forEach((biome, blocks) -> {
            int total = blocks.entrySet().stream().filter(e -> !e.getKey().startsWith("~"))
                    .mapToInt(Map.Entry::getValue).sum();
            StringBuilder line = new StringBuilder(String.format("Surface %-42s", biome));
            blocks.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(7).forEach(e ->
                    line.append(String.format(" %s %.0f%%", e.getKey().replace("minecraft:", ""),
                            100.0 * e.getValue() / total)));
            AlteredAether.LOGGER.info(line.toString());
        });
    }

    private DebugCommands() {
    }
}
