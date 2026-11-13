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
package com.linc.alteredaether.client.golem;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AAClientConfig;
import com.linc.alteredaether.entity.golem.GolemStorage;
import com.linc.alteredaether.entity.golem.GravititeGolem;
import com.linc.alteredaether.entity.golem.PebbleInfo;
import com.linc.alteredaether.item.PebbleLink;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.capabilities.Capabilities;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * What a player holding an active pebble sees: a ring of motes at the edge of each of its network's golems' reach, a
 * faint outline on each inventory it has linked nearby (red where no golem can get at it, yellow where pinned
 * strictly), a fuller glow on the linked storage or the network's golem under the crosshair, and whether the storage
 * looked at is linked.
 */
final class PebbleHints {
    /** Linked storage further than this shows no outline; the outline fades over the last stretch. */
    private static final double HELD_RANGE = 24, HELD_FADE = 6;
    /** The held outline's strength: faint, as it never goes away. */
    private static final float HELD_ALPHA = 0.25F;
    @Nullable
    private static BlockPos lookedAt;
    private static long lookedSince;

    private PebbleHints() {
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        GolemGlow.HELD.clear();
        if (player == null || level == null) {
            lookedAt = null;
            return;
        }
        PebbleLink link = held(player);
        PebbleInfo.Info info = link == null || link.dead() ? null : PebbleInfo.get(link.network());
        if (info == null || !PebbleInfo.bright(link, player.position())) {
            lookedAt = null;
            return;
        }
        boolean reduced = AAClientConfig.GOLEM_REDUCED_EFFECTS.get();
        if (player.tickCount % (reduced ? 12 : 4) == 0) {
            ring(level, player, info, reduced);
        }
        if (AAClientConfig.GOLEM_HELD_LINKS.get()) {
            outline(player, info);
        }
        look(mc, level, info);
    }

    @Nullable
    private static PebbleLink held(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(AARegistries.STORAGE_PEBBLE.get())) {
                return stack.get(AARegistries.PEBBLE_LINK.get());
            }
        }
        return null;
    }

    /**
     * Motes on the circle each golem reaches, about one a block, only the part of it near the player: a dotted line
     * on the ground marking the edge of its reach.
     */
    private static void ring(ClientLevel level, LocalPlayer player, PebbleInfo.Info info, boolean reduced) {
        double range = info.range();
        int points = (int) (Math.PI * 2 * range / (reduced ? 2.5 : 1.0));
        for (BlockPos home : info.homes()) {
            double cx = home.getX() + 0.5, cz = home.getZ() + 0.5, y = home.getY() + 0.15;
            for (int i = 0; i < points; i++) {
                double angle = (i + level.random.nextDouble() * 0.5) * Math.PI * 2 / points;
                double x = cx + Math.cos(angle) * range, z = cz + Math.sin(angle) * range;
                double dx = x - player.getX(), dz = z - player.getZ();
                if (dx * dx + dz * dz > 40 * 40) {
                    continue;
                }
                level.addParticle(new DustParticleOptions(GravititeGolem.GRAVITITE, 1.5F), x, y + 0.1, z, 0, 0.005, 0);
            }
        }
    }

    /** The faint outline on every linked inventory near the player. */
    private static void outline(LocalPlayer player, PebbleInfo.Info info) {
        int color = AAClientConfig.golemGlowColor();
        for (BlockPos pos : info.links()) {
            double distance = Math.sqrt(pos.distToCenterSqr(player.getEyePosition()));
            if (distance > HELD_RANGE) {
                continue;
            }
            float alpha = HELD_ALPHA * (float) Math.min(1, (HELD_RANGE - distance) / HELD_FADE);
            int edge = info.blocked().contains(pos) ? GolemGlow.BLOCKED_COLOR
                    : info.strict().contains(pos) ? GolemGlow.STRICT_COLOR : color;
            GolemGlow.HELD.add(new GolemGlow.Held(pos, edge, alpha));
        }
    }

    /**
     * Glows the linked storage under the crosshair (both halves) or the network's golem under it, for as long as the
     * look lasts; and says whether the storage looked at is linked, once each time the look lands on new storage.
     */
    private static void look(Minecraft mc, ClientLevel level, PebbleInfo.Info info) {
        int color = AAClientConfig.golemGlowColor();
        Entity entity = mc.crosshairPickEntity;
        if (entity instanceof GravititeGolem golem && info.members().contains(golem.getId())) {
            GolemGlow.look(List.of(), golem.getId(), color);
        }
        HitResult hit = mc.hitResult;
        BlockPos pos = hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null;
        if (pos == null || level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) == null) {
            lookedAt = null;
            return;
        }
        BlockState state = level.getBlockState(pos);
        BlockPos partner = partner(level, pos, state);
        boolean linked = info.links().contains(pos) || partner != null && info.links().contains(partner);
        if (linked) {
            List<BlockPos> both = new ArrayList<>(List.of(pos));
            if (partner != null) {
                both.add(partner);
            }
            boolean blocked = info.blocked().contains(pos) || partner != null && info.blocked().contains(partner);
            GolemGlow.look(both, -1, blocked ? GolemGlow.BLOCKED_COLOR : color);
        }
        long now = level.getGameTime();
        if (pos.equals(lookedAt) && now - lookedSince < 60) {
            return;
        }
        boolean fresh = !pos.equals(lookedAt);
        lookedAt = pos.immutable();
        lookedSince = now;
        if (!fresh) {
            return;
        }
        String key;
        if (linked) {
            key = info.blocked().contains(pos) || partner != null && info.blocked().contains(partner)
                    ? "hint.linked_blocked" : "hint.linked";
        } else if (state.is(GolemStorage.BLACKLIST) && !state.is(GolemStorage.LINKABLE)) {
            key = "hint.cant_link";
        } else if (state.is(GolemStorage.EMPTY_ONLY) && !state.is(GolemStorage.LINKABLE)) {
            key = "hint.empty_only";
        } else {
            key = "hint.not_linked";
        }
        mc.gui.setOverlayMessage(Component.translatable("message." + AlteredAether.MOD_ID + ".golem." + key, info.name()), false);
    }

    @Nullable
    private static BlockPos partner(ClientLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return pos.relative(ChestBlock.getConnectedDirection(state));
        }
        return null;
    }
}
