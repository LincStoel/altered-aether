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
package com.linc.alteredaether.block;

import com.aetherteam.aether.block.AetherBlockStateProperties;
import com.linc.alteredaether.compat.cloud.ShipRiders;
import com.linc.alteredaether.config.AACommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An aercloud a Sable body has just passed through (see compat/cloud/AercloudPhasing): it shows mostly see-through and
 * firms up a step at a time, then turns back into the cloud it was, {@code [phased_aerclouds] fade_seconds} on (15 by
 * default). Sable bodies pass through
 * it as they do every cloud. For anything else it is that cloud: the same shape, bounce, slowed fall and drops. Only
 * someone riding a Sable body passes through it, as they do every cloud (see ShipRiders).
 *
 * <p>Its steps line up with the game clock, so every cloud a ship phases in a chunk section steps on the same tick and
 * the section is rebuilt once per step rather than once per cloud.
 */
public class PhasedAercloudBlock extends HalfTransparentBlock {
    /** The clouds that can phase, with the texture each fades from. */
    public enum Variant implements StringRepresentable {
        COLD("aether:cold_aercloud", "aether:block/natural/cold_aercloud"),
        BLUE("aether:blue_aercloud", "aether:block/natural/blue_aercloud"),
        GOLDEN("aether:golden_aercloud", "aether:block/natural/golden_aercloud"),
        RAIN("deep_aether:rain_aercloud", "deep_aether:block/rain_aercloud"),
        STERLING("deep_aether:sterling_aercloud", "deep_aether:block/sterling_aercloud"),
        CHROMATIC("deep_aether:chromatic_aercloud", "deep_aether:block/chromatic_aercloud");

        private final ResourceLocation id;
        private final ResourceLocation texture;
        private volatile Block block;

        Variant(String id, String texture) {
            this.id = ResourceLocation.parse(id);
            this.texture = ResourceLocation.parse(texture);
        }

        public ResourceLocation texture() {
            return texture;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** The cloud block itself, or null where its mod isn't installed. */
        public Block block() {
            Block found = block;
            if (found == null) {
                found = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
                block = found;
            }
            return found;
        }

        private static volatile Map<Block, Variant> byBlock;

        /** Which variant a cloud block is, or null if it isn't one that phases. */
        public static Variant of(Block block) {
            Map<Block, Variant> map = byBlock;
            if (map == null) {
                map = Stream.of(values()).filter(v -> v.block() != null)
                        .collect(Collectors.toUnmodifiableMap(Variant::block, Function.identity()));
                byBlock = map;
            }
            return map.get(block);
        }
    }

    public static final EnumProperty<Variant> VARIANT = EnumProperty.create("variant", Variant.class);
    /** How far it has firmed up: 0 is the faintest, and the step after the last turns it back. */
    public static final IntegerProperty FADE = IntegerProperty.create("fade", 0, 7);
    public static final int MAX_FADE = 7;
    /** Aether's own property, so its double-drops loot function reads the same value off either block. */
    public static final BooleanProperty DOUBLE_DROPS = AetherBlockStateProperties.DOUBLE_DROPS;

    /** Ticks per step: the configured fade time split over the eight steps, at least one tick. */
    public static int step() {
        return Math.max(1, (int) Math.round(AACommonConfig.PHASED_AERCLOUD_SECONDS.get() * 20 / (MAX_FADE + 1)));
    }

    public PhasedAercloudBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(VARIANT, Variant.COLD).setValue(FADE, 0)
                .setValue(DOUBLE_DROPS, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT, FADE, DOUBLE_DROPS);
    }

    /** The phased form of a cloud, or null if it isn't one that phases. */
    public BlockState phase(BlockState cloud) {
        Variant variant = Variant.of(cloud.getBlock());
        if (variant == null) {
            return null;
        }
        boolean doubleDrops = cloud.hasProperty(DOUBLE_DROPS) && cloud.getValue(DOUBLE_DROPS);
        return defaultBlockState().setValue(VARIANT, variant).setValue(DOUBLE_DROPS, doubleDrops);
    }

    /** The cloud this was, and will be again; a cold aercloud if its mod has gone. */
    public static BlockState original(BlockState state) {
        Block block = state.getValue(VARIANT).block();
        if (block == null) {
            block = Variant.COLD.block();
        }
        BlockState cloud = block.defaultBlockState();
        return cloud.hasProperty(DOUBLE_DROPS) ? cloud.setValue(DOUBLE_DROPS, state.getValue(DOUBLE_DROPS)) : cloud;
    }

    // --------------------------------------------------------------------------------------------------- firming up

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        // also covers a phased cloud moved by a piston or set by a command
        schedule(level, pos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int fade = state.getValue(FADE);
        if (fade < MAX_FADE) {
            level.setBlock(pos, state.setValue(FADE, fade + 1), Block.UPDATE_CLIENTS);
            schedule(level, pos);
        } else {
            level.setBlock(pos, original(state), Block.UPDATE_CLIENTS);
        }
    }

    /** Schedules the next step on the next {@link #step()} boundary of the game clock, at least half a step away. */
    private void schedule(Level level, BlockPos pos) {
        if (level.isClientSide || level.getBlockTicks().hasScheduledTick(pos, this)) {
            return;
        }
        int step = step();
        int delay = (int) (step - level.getGameTime() % step);
        if (delay < step / 2) {
            delay += step;
        }
        level.scheduleTick(pos.immutable(), this, delay);
    }

    // ---------------------------------------------------------------------------------------- as the cloud it was

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext && ShipRiders.onShip(entityContext.getEntity())) {
            return Shapes.empty();
        }
        return original(state).getCollisionShape(level, pos, context);
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return original(state).getVisualShape(level, pos, context);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!ShipRiders.onShip(entity)) {
            original(state).entityInside(level, pos, entity);
        }
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float distance) {
        BlockState cloud = original(state);
        cloud.getBlock().fallOn(level, cloud, pos, entity, distance);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return original(state).propagatesSkylightDown(level, pos);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return original(state).getShadeBrightness(level, pos);
    }

    /** No face between it and the same cloud, phased or not, so a tunnel shows only its walls. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        Variant variant = state.getValue(VARIANT);
        if (adjacent.is(this)) {
            return adjacent.getValue(VARIANT) == variant;
        }
        return adjacent.getBlock() == variant.block() || super.skipRendering(state, adjacent, direction);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockState cloud = original(state);
        return cloud.getDrops(params.withParameter(LootContextParams.BLOCK_STATE, cloud));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(original(state).getBlock());
    }
}
