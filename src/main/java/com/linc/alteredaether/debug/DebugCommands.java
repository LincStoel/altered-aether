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
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.List;

/**
 * {@code /altered_aether report [radius] [step]} prints a {@link WorldgenReport} for the Aether around the caller.
 * Setting the system property {@code altered_aether.report} to {@code radius,step} runs one around the origin when the
 * server starts and then stops the server, so a dev run can check worldgen headlessly.
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
                                                IntegerArgumentType.getInteger(ctx, "step")))))));
    }

    private static int report(CommandContext<CommandSourceStack> ctx, int radius, int step) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel aether = source.getServer().getLevel(THE_AETHER);
        if (aether == null) {
            source.sendFailure(Component.literal("The Aether isn't loaded."));
            return 0;
        }
        int x = (int) source.getPosition().x, z = (int) source.getPosition().z;
        List<String> lines = WorldgenReport.run(aether, x, z, radius, step);
        lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return 1;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        String spec = System.getProperty("altered_aether.report", "");
        if (spec.isBlank()) {
            return;
        }
        MinecraftServer server = event.getServer();
        String[] parts = spec.split(",");
        int radius = Integer.parseInt(parts[0].trim());
        int step = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 32;
        ServerLevel aether = server.getLevel(THE_AETHER);
        if (aether != null) {
            WorldgenReport.run(aether, 0, 0, radius, step);
        } else {
            AlteredAether.LOGGER.error("Worldgen report: the Aether isn't loaded");
        }
        server.halt(false);
    }

    private DebugCommands() {
    }
}
