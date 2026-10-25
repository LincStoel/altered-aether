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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pictures of generated terrain, for judging shapes without a client: a shaded map seen from above, and elevations
 * seen from the south and from the east, each block coloured by its map colour and fading with distance. Written as
 * PNGs to {@code renders/} in the server directory.
 */
final class WorldRender {
    private static final int SCALE = 2;
    private static final int SKY = 0xB8D4F0;

    /** Renders the square of blocks from (minX, minZ) to (maxX, maxZ) inclusive, which must be generated. */
    static void render(ServerLevel level, String name, int minX, int minZ, int maxX, int maxZ) {
        int width = maxX - minX + 1, depth = maxZ - minZ + 1;
        int bottom = level.getMinBuildHeight(), height = level.getHeight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Every column's blocks, top first, read once.
        int[][] tops = new int[width][depth], columnColour = new int[width][depth];
        BufferedImage top = new BufferedImage(width * SCALE, depth * SCALE, BufferedImage.TYPE_INT_RGB);
        BufferedImage south = new BufferedImage(width * SCALE, height * SCALE, BufferedImage.TYPE_INT_RGB);
        BufferedImage east = new BufferedImage(depth * SCALE, height * SCALE, BufferedImage.TYPE_INT_RGB);
        int[][] southColour = new int[width][height], eastColour = new int[depth][height];
        int[][] southDepth = new int[width][height], eastDepth = new int[depth][height];
        for (int[] row : southDepth) {
            java.util.Arrays.fill(row, Integer.MAX_VALUE);
        }
        for (int[] row : eastDepth) {
            java.util.Arrays.fill(row, Integer.MAX_VALUE);
        }
        for (int i = 0; i < width; i++) {
            for (int k = 0; k < depth; k++) {
                tops[i][k] = Integer.MIN_VALUE;
                for (int y = bottom + height - 1; y >= bottom; y--) {
                    BlockState state = level.getBlockState(pos.set(minX + i, y, minZ + k));
                    if (state.isAir()) {
                        continue;
                    }
                    int colour = colour(level, state, pos);
                    if (tops[i][k] == Integer.MIN_VALUE) {
                        tops[i][k] = y;
                        columnColour[i][k] = colour;
                    }
                    int row = y - bottom;
                    // Seen from the south, nearer means larger z; from the east, larger x.
                    int fromSouth = depth - 1 - k, fromEast = width - 1 - i;
                    if (fromSouth < southDepth[i][row]) {
                        southDepth[i][row] = fromSouth;
                        southColour[i][row] = colour;
                    }
                    if (fromEast < eastDepth[k][row]) {
                        eastDepth[k][row] = fromEast;
                        eastColour[k][row] = colour;
                    }
                }
            }
        }
        int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
        for (int[] row : tops) {
            for (int y : row) {
                if (y != Integer.MIN_VALUE) {
                    lowest = Math.min(lowest, y);
                    highest = Math.max(highest, y);
                }
            }
        }
        for (int i = 0; i < width; i++) {
            for (int k = 0; k < depth; k++) {
                int colour = SKY;
                if (tops[i][k] != Integer.MIN_VALUE) {
                    double t = highest == lowest ? 0.5 : (tops[i][k] - lowest) / (double) (highest - lowest);
                    // Light from the north-west, so slopes read.
                    int west = i > 0 && tops[i - 1][k] != Integer.MIN_VALUE ? tops[i - 1][k] : tops[i][k];
                    int north = k > 0 && tops[i][k - 1] != Integer.MIN_VALUE ? tops[i][k - 1] : tops[i][k];
                    double slope = ((tops[i][k] - west) + (tops[i][k] - north)) * 0.08;
                    colour = shade(columnColour[i][k], 0.6 + 0.5 * t + Math.max(-0.3, Math.min(0.3, slope)));
                }
                fill(top, i, k, colour);
            }
        }
        elevation(south, southColour, southDepth, width, height, depth);
        elevation(east, eastColour, eastDepth, depth, height, width);
        try {
            Path folder = level.getServer().getServerDirectory().resolve("renders");
            Files.createDirectories(folder);
            ImageIO.write(top, "png", folder.resolve(name + "_top.png").toFile());
            ImageIO.write(south, "png", folder.resolve(name + "_south.png").toFile());
            ImageIO.write(east, "png", folder.resolve(name + "_east.png").toFile());
            AlteredAether.LOGGER.info("Rendered {} ({}..{}, {}..{}, heights {}..{}) to {}", name, minX, maxX, minZ, maxZ,
                    lowest, highest, folder.toAbsolutePath());
        } catch (IOException e) {
            AlteredAether.LOGGER.error("Couldn't write the render of {}", name, e);
        }
    }

    private static void elevation(BufferedImage image, int[][] colours, int[][] depths, int width, int height,
                                  int range) {
        for (int i = 0; i < width; i++) {
            for (int row = 0; row < height; row++) {
                int colour = SKY;
                if (depths[i][row] != Integer.MAX_VALUE) {
                    colour = blend(colours[i][row], SKY, Math.min(0.75, depths[i][row] / (double) range * 0.9));
                }
                fill(image, i, height - 1 - row, colour);
            }
        }
    }

    private static int colour(ServerLevel level, BlockState state, BlockPos pos) {
        if (!state.getFluidState().isEmpty()) {
            return MapColor.WATER.col;
        }
        MapColor map = state.getMapColor(level, pos);
        return map == MapColor.NONE ? 0x808080 : map.col;
    }

    private static void fill(BufferedImage image, int x, int y, int colour) {
        for (int dx = 0; dx < SCALE; dx++) {
            for (int dy = 0; dy < SCALE; dy++) {
                image.setRGB(x * SCALE + dx, y * SCALE + dy, colour);
            }
        }
    }

    private static int shade(int colour, double light) {
        int r = (int) Math.min(255, ((colour >> 16) & 255) * light);
        int g = (int) Math.min(255, ((colour >> 8) & 255) * light);
        int b = (int) Math.min(255, (colour & 255) * light);
        return (r << 16) | (g << 8) | b;
    }

    private static int blend(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private WorldRender() {
    }
}
