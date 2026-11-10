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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * {@code /altered_aether}, for operators: find the nearest landmark or grove giant, roll a rumour map, or build a grove
 * ruin to look at. Dev runs add more under the same command (the dev source set's {@code DebugCommands}).
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
                6000, level.getRandom());
        double ms = (System.nanoTime() - start) / 1e6;
        if (site.isEmpty()) {
            source.sendFailure(Component.literal(String.format("No landmark within 6000 blocks (%.1f ms).", ms)));
            return 0;
        }
        if (source.getEntity() instanceof ServerPlayer player) {
            player.getInventory().add(RumourMapFunction.create(level, site.get(), (byte) 2));
        }
        source.sendSuccess(() -> Component.literal(String.format("A rumour of a %s at %d, %d (%.1f ms).",
                site.get().type().id(), site.get().x(), site.get().z(), ms)), false);
        return 1;
    }

    /** A green {@code [go]} that puts a teleport command in the chat box. */
    private static Component go(String command) {
        return Component.literal("[go]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)));
    }

    private AACommands() {
    }
}
