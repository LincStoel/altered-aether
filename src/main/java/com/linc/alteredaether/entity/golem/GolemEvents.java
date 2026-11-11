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
package com.linc.alteredaether.entity.golem;

import com.aetherteam.aether.block.AetherBlocks;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.advancement.AAAdvancements;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Building a gravitite golem (a carved pumpkin or squash on enchanted gravitite on holystone), and what keeps it out
 * of fights: monsters don't take it as a target, and a player's sweep doesn't catch it.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class GolemEvents {
    private record Build(ServerLevel level, BlockPos head, @Nullable UUID builder) {
    }

    /** Builds found while blocks were being placed, made at the end of the tick, once placing is done. */
    private static final List<Build> PENDING = new ArrayList<>();
    /** Who each player last struck directly, and when, to tell a sweep from a blow. */
    private static final Map<UUID, Strike> STRIKES = new HashMap<>();

    private record Strike(int target, long tick) {
    }

    private GolemEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level) || !event.getPlacedBlock().is(GolemStorage.HEADS)) {
            return;
        }
        if (isBuild(level, event.getPos())) {
            Entity entity = event.getEntity();
            PENDING.add(new Build(level, event.getPos().immutable(), entity == null ? null : entity.getUUID()));
        }
    }

    /** Whether there's a golem's head at {@code head}, on enchanted gravitite, on holystone. */
    public static boolean isBuild(ServerLevel level, BlockPos head) {
        return level.getBlockState(head).is(GolemStorage.HEADS)
                && level.getBlockState(head.below()).is(AetherBlocks.ENCHANTED_GRAVITITE.get())
                && level.getBlockState(head.below(2)).is(AetherBlocks.HOLYSTONE.get());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<Build> builds = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Build build : builds) {
            if (isBuild(build.level, build.head)) {
                Player builder = build.builder == null ? null : build.level.getPlayerByUUID(build.builder);
                build(build.level, build.head, builder);
            }
        }
    }

    /** Turns the three blocks into a golem, sitting where the holystone was, facing whoever built it. */
    public static GravititeGolem build(ServerLevel level, BlockPos head, @Nullable Player builder) {
        BlockPos base = head.below(2);
        for (BlockPos pos : new BlockPos[]{head, head.below(), base}) {
            BlockState state = level.getBlockState(pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.levelEvent(2001, pos, Block.getId(state));
        }
        GravititeGolem golem = AARegistries.GRAVITITE_GOLEM.get().create(level);
        if (golem == null) {
            return null;
        }
        // facing whoever built it, from where they stand
        float yaw = builder == null ? 0 : (float) (Math.atan2(builder.getZ() - (base.getZ() + 0.5),
                builder.getX() - (base.getX() + 0.5)) * (180 / Math.PI)) - 90;
        golem.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, yaw, 0);
        golem.setYHeadRot(yaw);
        golem.yBodyRot = yaw;
        golem.finalizeSpawn(level, level.getCurrentDifficultyAt(base), MobSpawnType.TRIGGERED, null);
        golem.setHome(base);
        level.addFreshEntity(golem);
        for (BlockPos pos : new BlockPos[]{head, head.below(), base}) {
            level.blockUpdated(pos, Blocks.AIR);
        }
        golem.event(GolemTiming.EVENT_LINK_PULSE);
        level.playSound(null, base, AARegistries.GOLEM_CHIME.get(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, golem.getBoundingBox().inflate(6))) {
            AAAdvancements.award(player, AAAdvancements.MIND_OVER_MATTER, "built");
        }
        return golem;
    }

    /** Nothing takes a golem as a target: it fights no one. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof GravititeGolem) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        STRIKES.put(player.getUUID(), new Strike(event.getTarget().getId(), player.level().getGameTime()));
    }

    /** A sweep catches what stands beside the target; a golem beside it is spared. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof GravititeGolem golem) || !event.getSource().is(DamageTypes.PLAYER_ATTACK)
                || !(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        Strike strike = STRIKES.get(player.getUUID());
        if (strike != null && strike.tick == player.level().getGameTime() && strike.target != golem.getId()) {
            event.setCanceled(true);
        }
    }
}
