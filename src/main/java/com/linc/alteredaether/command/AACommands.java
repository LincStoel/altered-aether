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
package com.linc.alteredaether.command;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.loot.RumourMapFunction;
import com.linc.alteredaether.worldgen.grove.BreakField;
import com.linc.alteredaether.worldgen.grove.Giant;
import com.linc.alteredaether.worldgen.grove.GiantType;
import com.linc.alteredaether.worldgen.grove.GroveField;
import com.linc.alteredaether.worldgen.grove.Mire;
import com.linc.alteredaether.worldgen.landmark.Landmark;
import com.linc.alteredaether.worldgen.landmark.LandmarkField;
import com.linc.alteredaether.worldgen.landmark.LandmarkType;
import com.linc.alteredaether.worldgen.ruin.RuinPlan;
import com.linc.alteredaether.worldgen.ruin.Ruins;
import com.linc.alteredaether.worldgen.sea.CloudSea;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * {@code /altered_aether}, for operators: find the nearest landmark, grove giant or cloud sea, roll a rumour map, or
 * build a grove ruin to look at. Dev runs add more under the same command (the dev source set's {@code DebugCommands}).
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class AACommands {
    public static final ResourceKey<Level> THE_AETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));

    /** The name {@code giant} takes for the nearest swamp lake round a giant. */
    public static final String MIRE = "mire";

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(AlteredAether.MOD_ID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("landmark")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(LandmarkType.values()).map(LandmarkType::id), builder))
                                .executes(AACommands::landmark)))
                .then(Commands.literal("rumour").executes(AACommands::rumour))
                .then(Commands.literal("sea").executes(AACommands::sea))
                .then(Commands.literal("ruin")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Ruins.Kind.values()).map(Ruins.Kind::id), builder))
                                .executes(AACommands::ruin)))
                .then(Commands.literal("giant")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Stream.concat(Arrays.stream(GiantType.values())
                                                .filter(t -> t != GiantType.LINK && t != GiantType.GRANDFATHER
                                                        && t != GiantType.VILLAGE_TREE && t != GiantType.BREAK_BRIDGE
                                                        && t != GiantType.YOUNG)
                                                .map(GiantType::id), Stream.of(MIRE, "break_edge", "break_chasm")), builder))
                                .executes(AACommands::giant))));
    }

    /**
     * {@code /altered_aether ruin <kind>}: builds a grove ruin of a kind centred on the caller, on the ground as it
     * lies, for a look at it. Each use draws a new one.
     */
    private static int ruin(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        var kind = Ruins.kind(StringArgumentType.getString(ctx, "kind"));
        if (kind == null) {
            source.sendFailure(Component.literal("No such ruin."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        var plan = Ruins.draw(kind, level.getRandom(), pos.getX(), pos.getZ());
        int reach = Ruins.REACH;
        plan.place(level, pos.getX() - reach, pos.getZ() - reach, pos.getX() + reach, pos.getZ() + reach,
                (x, z) -> RuinPlan.groundOf(level, x, z));
        source.sendSuccess(() -> Component.literal("Built a " + kind.id() + " ruin of " + plan.pieces().size()
                + " blocks."), false);
        return 1;
    }

    /**
     * {@code /altered_aether giant <type>}: the nearest grove giant of a type, with a link to go and see it.
     * {@code giant mire} finds the nearest swamp lake and stands you on its shore.
     */
    private static int giant(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        GroveField field = GroveField.get(THE_AETHER);
        if (field != null && StringArgumentType.getString(ctx, "type").equals(MIRE)) {
            int x = (int) source.getPosition().x, z = (int) source.getPosition().z;
            CompletableFuture.supplyAsync(() -> field.nearestMire(x, z, 20000), Util.backgroundExecutor())
                    .thenAcceptAsync(found -> {
                        if (found.isEmpty()) {
                            source.sendFailure(Component.literal("No mire within 20000 blocks."));
                            return;
                        }
                        Mire mire = found.get();
                        int sx = mire.x() + (int) Math.round(mire.radius() * 1.1);
                        String tp = "/execute in aether:the_aether run tp @s " + sx + " " + (mire.water() + 4) + " "
                                + mire.z() + " 90 15";
                        source.sendSuccess(() -> Component.literal("mire at " + mire.x() + ", " + mire.z()
                                + " water " + mire.water() + ", " + mire.islets().size() + " islets ").append(
                                go(tp)), false);
                    }, source.getServer());
            return 1;
        }
        String name = StringArgumentType.getString(ctx, "type");
        if (field != null && field.breaks() != null && name.startsWith("break_")) {
            BreakField.Kind kind;
            try {
                kind = BreakField.Kind.valueOf(name.substring(6).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                source.sendFailure(Component.literal("No such break."));
                return 0;
            }
            int x = (int) source.getPosition().x, z = (int) source.getPosition().z;
            CompletableFuture.supplyAsync(() -> field.breaks().nearest(kind, x, z, 20000), Util.backgroundExecutor())
                    .thenAcceptAsync(found -> {
                        if (found.isEmpty()) {
                            source.sendFailure(Component.literal("No " + kind.id() + " break within 20000 blocks."));
                            return;
                        }
                        var site = found.get();
                        int sx = (int) Math.round(site.x() - site.nx() * 12), sz = (int) Math.round(site.z() - site.nz() * 12);
                        String tp = "/execute in aether:the_aether run tp @s " + sx + " " + (site.ground() + 6) + " " + sz;
                        source.sendSuccess(() -> Component.literal(kind.id() + " break at " + (int) site.x() + ", "
                                + (int) site.z() + String.format(" (gap %.0f, sunk %.0f) ", site.gap(), site.sink()))
                                .append(go(tp)), false);
                    }, source.getServer());
            return 1;
        }
        GiantType type;
        try {
            type = GiantType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("No such giant."));
            return 0;
        }
        if (field == null) {
            source.sendFailure(Component.literal("The Aether has no groves."));
            return 0;
        }
        int x = (int) source.getPosition().x, z = (int) source.getPosition().z;
        source.sendSuccess(() -> Component.literal("Searching for a " + type.id() + "..."), false);
        CompletableFuture.supplyAsync(() -> field.nearest(type, x, z, 20000), Util.backgroundExecutor())
                .thenAcceptAsync(found -> {
                    if (found.isEmpty()) {
                        source.sendFailure(Component.literal("No " + type.id() + " within 20000 blocks."));
                        return;
                    }
                    Giant site = found.get();
                    String tp = "/execute in aether:the_aether run tp @s " + site.x() + " " + (site.base() + 2) + " "
                            + site.z();
                    source.sendSuccess(() -> Component.literal(type.id() + " at " + site.x() + ", " + site.z() + " ("
                            + (int) Math.hypot(site.x() - x, site.z() - z) + " blocks) ").append(go(tp)), false);
                }, source.getServer());
        return 1;
    }

    /** {@code /altered_aether landmark <type>}: the nearest landmark of a type, with a link to go and see it. */
    private static int landmark(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel aether = source.getServer().getLevel(THE_AETHER);
        LandmarkField field = aether == null ? null : LandmarkField.get(THE_AETHER);
        LandmarkType type;
        try {
            type = LandmarkType.valueOf(StringArgumentType.getString(ctx, "type").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("No such landmark."));
            return 0;
        }
        if (field == null) {
            source.sendFailure(Component.literal("The Aether has no landmarks."));
            return 0;
        }
        int x = (int) source.getPosition().x, z = (int) source.getPosition().z;
        source.sendSuccess(() -> Component.literal("Searching for a " + type.id() + "..."), false);
        CompletableFuture.supplyAsync(() -> field.nearest(type, x, z, 50000), Util.backgroundExecutor())
                .thenAcceptAsync(found -> {
                    if (found.isEmpty()) {
                        source.sendFailure(Component.literal("No " + type.id() + " within 50000 blocks."));
                        return;
                    }
                    Landmark site = found.get();
                    int y = site.visitY() == Integer.MIN_VALUE ? 160 : site.visitY();
                    String tp = "/execute in aether:the_aether run tp @s " + site.x() + " " + y + " " + site.z();
                    source.sendSuccess(() -> Component.literal(type.id() + " at " + site.x() + ", " + site.z() + " ("
                            + (int) Math.hypot(site.x() - x, site.z() - z) + " blocks) ").append(go(tp)), false);
                }, source.getServer());
        return 1;
    }

    /**
     * {@code /altered_aether rumour}: a rumour map from where you stand, as a chest there would roll one. The search
     * runs on the server thread, as it does for loot, and its time is reported.
     */
    private static int rumour(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        LandmarkField field = LandmarkField.get(level.dimension());
        if (field == null) {
            source.sendFailure(Component.literal("There are no landmarks here."));
            return 0;
        }
        int x = (int) source.getPosition().x, z = (int) source.getPosition().z;
        long start = System.nanoTime();
        Optional<Landmark> site = RumourMapFunction.find(field, Arrays.asList(LandmarkType.values()), x, z,
                RumourMapFunction.DEFAULT_RADIUS, level.getRandom());
        double ms = (System.nanoTime() - start) / 1e6;
        if (site.isEmpty()) {
            source.sendFailure(Component.literal(String.format("No landmark within %d blocks (%.1f ms).",
                    RumourMapFunction.DEFAULT_RADIUS, ms)));
            return 0;
        }
        if (source.getEntity() instanceof ServerPlayer player) {
            player.getInventory().add(RumourMapFunction.create(level, site.get(), (byte) 2));
        }
        source.sendSuccess(() -> Component.literal(String.format("A rumour of a %s at %d, %d (%.1f ms).",
                site.get().type().id(), site.get().x(), site.get().z(), ms)), false);
        return 1;
    }

    /**
     * {@code /altered_aether sea}: the nearest open cloud sea (tall Aether only), with a link that stands you on its
     * sheet, or puts you in the air above it where no clear patch of sheet turns up close by.
     */
    private static int sea(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel aether = source.getServer().getLevel(THE_AETHER);
        CloudSea sea = CloudSea.terrain(THE_AETHER);
        if (aether == null || sea == null) {
            source.sendFailure(Component.literal("The Aether has no cloud seas (they need the tall Aether)."));
            return 0;
        }
        int x = (int) source.getPosition().x, z = (int) source.getPosition().z;
        source.sendSuccess(() -> Component.literal("Searching for a cloud sea..."), false);
        CompletableFuture.supplyAsync(() -> nearestSea(aether, sea, x, z), Util.backgroundExecutor())
                .thenAcceptAsync(found -> {
                    if (found == null) {
                        source.sendFailure(Component.literal("No cloud sea within " + SEA_RADIUS + " blocks."));
                        return;
                    }
                    String tp = "/execute in aether:the_aether run tp @s " + found.getX() + " " + found.getY() + " "
                            + found.getZ();
                    source.sendSuccess(() -> Component.literal("cloud sea at " + found.getX() + ", " + found.getZ()
                            + " (" + (int) Math.hypot(found.getX() - x, found.getZ() - z) + " blocks) ")
                            .append(go(tp)), false);
                }, source.getServer());
        return 1;
    }

    private static final int SEA_RADIUS = 20000;

    /**
     * The nearest open sea, ring by ring outward on a 64-block grid, at a column near it whose sheet is whole and has
     * open air on top, to stand on. Where the first few points of sea found have no such column (a landmass fills
     * them), the air above the first. Null if there's no sea within {@link #SEA_RADIUS}.
     */
    private static BlockPos nearestSea(ServerLevel aether, CloudSea sea, int x, int z) {
        int step = 64, tries = 0;
        BlockPos first = null;
        for (int r = 0; r * step <= SEA_RADIUS; r++) {
            for (int i = -r; i <= r; i++) {
                for (int[] p : new int[][]{{i, -r}, {i, r}, {-r, i}, {r, i}}) {
                    int px = x + p[0] * step, pz = z + p[1] * step;
                    if (sea.weight(px, pz) <= 0.95) {
                        continue;
                    }
                    BlockPos footing = seaFooting(aether, sea, px, pz);
                    if (footing != null) {
                        return footing;
                    }
                    if (first == null) {
                        first = new BlockPos(px, aether.getChunkSource().getGenerator().getBaseHeight(px, pz,
                                Heightmap.Types.WORLD_SURFACE_WG, aether, aether.getChunkSource().randomState()) + 2,
                                pz);
                    }
                    if (++tries >= 24) {
                        return first;
                    }
                }
            }
        }
        return first;
    }

    @org.jetbrains.annotations.Nullable
    private static BlockPos seaFooting(ServerLevel aether, CloudSea sea, int x, int z) {
        var generator = aether.getChunkSource().getGenerator();
        var random = aether.getChunkSource().randomState();
        int[] span = new int[2];
        for (int r = 0; r <= 4; r++) {
            for (int i = -r; i <= r; i++) {
                for (int[] p : new int[][]{{i, -r}, {i, r}, {-r, i}, {r, i}}) {
                    int px = x + p[0] * 12, pz = z + p[1] * 12;
                    double w = sea.weight(px, pz);
                    if (w < 0.9 || !sea.span(px, pz, w, span) || span[1] - span[0] < 3) {
                        continue;
                    }
                    var column = generator.getBaseColumn(px, pz, aether, random);
                    boolean clear = true;
                    for (int y = span[0]; y <= span[1] + 3 && clear; y++) {
                        clear = column.getBlock(y).isAir();
                    }
                    if (clear) {
                        return new BlockPos(px, span[1] + 1, pz);
                    }
                }
            }
        }
        return null;
    }

    /** A green {@code [go]} that puts a teleport command in the chat box. */
    private static Component go(String command) {
        return Component.literal("[go]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)));
    }

    private AACommands() {
    }
}
