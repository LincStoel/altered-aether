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
package com.linc.alteredaether.compat.boulder;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.advancement.AAAdvancements;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.worldgen.boulder.BoulderShape;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import com.linc.alteredaether.data.AABoulderProvider;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Boulders that fall out of the sky: a lump of stone and ore built as real blocks, turned into a Sable sub-level, let
 * fall under Sable's physics, and put back into the world as ordinary blocks once it has come to rest.
 *
 * <ul>
 *   <li>Each is recorded by its sub-level's id in {@link BoulderRecords}, so one Sable puts away with its chunks is
 *   still ours when it comes back, and {@link #isBoulder} (what lets it through the end sea) is a set lookup.</li>
 *   <li>A boulder somebody has changed (a block taken or added, or merged into a ship) stops being one: it is let go
 *   and Sable treats it like any other body from then on.</li>
 *   <li>Its speed is capped from Sable's pre-physics event, so a fall from the top of the world can't tunnel through
 *   thin ground.</li>
 *   <li>Settling happens in the level tick, after the physics step, never inside it: tearing down a sub-level while
 *   Sable steps it is not safe (magneticism learned this). The body's rotation is snapped to the nearest right-angle
 *   orientation and its position to the block grid, so its blocks land without gaps.</li>
 * </ul>
 *
 * <p>Loaded only when Sable is: nothing outside this package and the mixin that asks {@link #isBoulder} names a Sable
 * type.
 */
public final class FallingBoulders {
    public static final String MOD = "sable";
    /** The name given to a boulder's sub-level, which Sable shows in its own commands and saves with it. */
    public static final String NAME = AlteredAether.MOD_ID + ":boulder";
    /** Sub-levels that are ours, in every level; read from Sable's physics thread, so concurrent. */
    private static final Set<UUID> ACTIVE = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Containers an observer is already on. */
    private static final Set<SubLevelContainer> OBSERVED = Collections.newSetFromMap(new WeakHashMap<>());
    /** Entities a boulder has already hurt, so a body resting on one doesn't hurt it every tick. */
    private static final Map<UUID, Set<UUID>> HURT = new HashMap<>();

    /** The fastest a boulder may fall, in blocks per second. */
    private static final double MAX_SPEED = 45;
    /** Below this speed, and {@link #STILL_SPIN}, for {@link #STILL_TICKS} in a row, a boulder that has moved is at rest. */
    private static final double STILL_SPEED = 0.08;
    private static final double STILL_SPIN = 0.2;
    private static final int STILL_TICKS = 10;
    /** The shortest a boulder may live before it can settle: a body just made reports a stale zero speed. */
    private static final int MIN_AGE = 20;
    /** After this long, a boulder still creeping or wobbling is settled where it is. */
    private static final int WOBBLE_AGE = 20 * 150;
    /** After this long, whatever it is doing, it is removed. */
    private static final int MAX_AGE = 20 * 600;
    /** Speed below which a boulder hurts nobody. */
    private static final double HARMLESS_SPEED = 4;
    /** How far below the world a boulder may go before it is removed. */
    private static final int VOID_MARGIN = 96;

    /** The fastest a boulder's body has been seen going in a physics step, for the checks to read. */
    private static volatile double peakSpeed;

    /** The 24 rotations of a cube, as whole-number matrices. */
    private static final int[][][] ROTATIONS = rotations();

    public static boolean active() {
        return ModList.get().isLoaded(MOD) && AACommonConfig.compat(MOD);
    }

    /** Called from the mod's constructor, only when Sable is loaded. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(FallingBoulders::onLevelTick);
        NeoForge.EVENT_BUS.addListener(FallingBoulders::onPrePhysics);
        BoulderSpawner.register();
        if (ModList.get().isLoaded(FtbChunksProtection.MOD)) {
            FtbChunksProtection.register();
        }
    }

    /** Whether this sub-level is one of ours; what the end sea mixin asks. Safe from any thread. */
    public static boolean isBoulder(SubLevel subLevel) {
        return subLevel != null && ACTIVE.contains(subLevel.getUniqueId());
    }

    /** The fastest a boulder's body has been seen going, read from its physics handle after the cap; and clears it. */
    public static double takePeakSpeed() {
        double peak = peakSpeed;
        peakSpeed = 0;
        return peak;
    }

    /** For the checks: announces a boulder near the player as their timer would, if a spot will do. */
    public static boolean announceNear(ServerLevel level, ServerPlayer player) {
        return BoulderSpawner.attempt(level, player, level.getRandom());
    }

    /** For the checks: lets boulders be announced and dropped outside the Aether. */
    public static void anywhere(boolean on) {
        BoulderSpawner.anywhere = on;
    }

    /** For the checks: forgets every boulder a level knew of, such as ones a world kept from an earlier run. */
    public static void forgetAll(ServerLevel level) {
        BoulderRecords records = BoulderRecords.get(level);
        for (UUID id : new ArrayList<>(records.entries().keySet())) {
            release(records, id);
        }
    }

    /** How many boulders this level is keeping track of. */
    public static int tracked(ServerLevel level) {
        return BoulderRecords.get(level).entries().size();
    }

    /** How many solid blocks a boulder's body holds. */
    public static int blocks(ServerLevel level, ServerSubLevel subLevel) {
        return plotBlocks(level, subLevel).size();
    }

    // ----------------------------------------------------------------------------------------------------- spawning

    /**
     * Builds a boulder in open air with its lowest middle near {@code origin} and lets it go. The blocks form only
     * where there is air, so it can't carve into anything.
     *
     * @param ore       the ore buried through it, or null for plain stone
     * @param oreChance how much of it is ore; gravitite only ever goes where it is buried on all six sides, as the
     *                  Aether floats it away once it is exposed
     * @return its sub-level, or null if there was no room
     */
    public static ServerSubLevel spawn(ServerLevel level, BlockPos origin, BlockState stone, BlockState ore,
                                       float oreChance, RandomSource random) {
        int radius = 2 + random.nextInt(2);
        double squash = 0.8;
        double height = BoulderShape.height(radius, squash);
        BlockPos center = origin.above((int) Math.ceil(height));
        double reach = radius * 2 + 2;
        Map<BlockPos, BlockState> shape = BoulderShape.carve(random, BoulderShape.lobes(random, radius, height, squash),
                center, origin, reach, radius, at -> level.getBlockState(at).isAir(), at -> stone);
        if (shape.size() < 6) {
            return null;
        }
        boolean buriedOnly = ore.is(com.aetherteam.aether.block.AetherBlocks.GRAVITITE_ORE.get());
        List<BlockPos> positions = new ArrayList<>(shape.keySet());
        for (BlockPos pos : positions) {
            boolean placeOre = random.nextFloat() < oreChance && (!buriedOnly || BoulderShape.enclosed(shape, pos));
            level.setBlock(pos, placeOre ? ore : stone, Block.UPDATE_CLIENTS);
        }

        BlockPos anchor = shape.containsKey(center) ? center : positions.get(0);
        ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, anchor, positions, padded(positions));
        if (subLevel == null) {
            for (BlockPos pos : positions) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            return null;
        }
        subLevel.setName(NAME);
        UUID id = subLevel.getUniqueId();
        ACTIVE.add(id);
        BoulderRecords.get(level).add(id, level.getGameTime(), positions.size());
        observe(SubLevelContainer.getContainer(level));
        return subLevel;
    }

    /**
     * Sable's own single-block shatter command pads the bounds by one in every direction, and a body made without
     * the margin never reaches its clients properly (see magneticism's note on it).
     */
    private static BoundingBox3i padded(List<BlockPos> positions) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX() + 1);
            maxY = Math.max(maxY, pos.getY() + 1);
            maxZ = Math.max(maxZ, pos.getZ() + 1);
        }
        return new BoundingBox3i(minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1);
    }

    // ------------------------------------------------------------------------------------------------------ ticking

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        BoulderRecords records = BoulderRecords.get(level);
        if (records.entries().isEmpty()) {
            return;
        }
        observe(container);
        long now = level.getGameTime();
        List<ServerSubLevel> settling = new ArrayList<>();
        List<ServerSubLevel> vanishing = new ArrayList<>();
        Map<ServerSubLevel, Integer> embeds = new HashMap<>();
        for (UUID id : new ArrayList<>(records.entries().keySet())) {
            BoulderRecords.Entry entry = records.entries().get(id);
            ACTIVE.add(id);
            if (!(container.getSubLevel(id) instanceof ServerSubLevel sub) || sub.isRemoved()) {
                continue;
            }
            int age = (int) (now - entry.started);
            double speed = sub.latestLinearVelocity.length();
            double spin = sub.latestAngularVelocity.length();
            if (speed > 0.5) {
                records.markMoved(entry);
            }
            Vector3d velocity = new Vector3d(sub.latestLinearVelocity);
            Vector3d before = entry.previous;
            entry.previous = velocity;
            if (before != null && before.length() > EMBED_SPEED && embedded(level, sub, before, velocity)) {
                embeds.put(sub, before.length() > 25 ? 2 : 1);
                continue;
            }
            if (speed > BREAK_SPEED && age % 2 == 0) {
                breakThrough(level, sub, velocity);
            }

            if (sub.logicalPose().position().y() < level.getMinBuildHeight() - VOID_MARGIN || age > MAX_AGE) {
                vanishing.add(sub);
                continue;
            }
            if (age % 20 == 0 && plotBlocks(level, sub).size() != entry.blocks) {
                // somebody has taken it apart or built onto it: it is no longer ours
                release(records, id);
                continue;
            }
            if (speed > HARMLESS_SPEED) {
                hurt(level, sub, speed);
            }
            if (speed > 12) {
                Vector3d at = sub.logicalPose().position();
                level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 1, at.z, 2, 0.8, 0.8, 0.8, 0.02);
            }

            boolean still = speed < STILL_SPEED && spin < STILL_SPIN;
            entry.restTicks = still ? entry.restTicks + 1 : 0;
            boolean rested = entry.moved && age >= MIN_AGE && entry.restTicks >= STILL_TICKS;
            if (rested || age > WOBBLE_AGE && speed < 0.5) {
                settling.add(sub);
            }
        }
        for (ServerSubLevel sub : vanishing) {
            discard(level, sub);
        }
        for (Map.Entry<ServerSubLevel, Integer> embed : embeds.entrySet()) {
            settle(level, embed.getKey(), embed.getValue());
        }
        for (ServerSubLevel sub : settling) {
            settle(level, sub, 0);
        }
    }

    // ------------------------------------------------------------------------------------- breaking and embedding

    /** Below this speed a boulder is not going fast enough to plough through anything. */
    private static final double BREAK_SPEED = 5;
    /** A boulder moving faster than this and stopped dead against solid ground buries itself in it. */
    private static final double EMBED_SPEED = 10;
    private static final TagKey<Block> AETHER_DIRT = blockTag("aether", "aether_dirt");
    private static final TagKey<Block> HOLYSTONE = blockTag("aether", "holystone");
    private static final TagKey<Block> HOLYSTONE_REPLACEABLES = blockTag("aether", "holystone_ore_replaceables");

    private static final TagKey<Block> AERCLOUDS = blockTag("aether", "aerclouds");

    private static TagKey<Block> blockTag(String namespace, String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    /** Ground a boulder sinks into: Aether grass, dirt and stone, and the overworld's like. */
    private static boolean embeddable(BlockState state) {
        return state.is(AETHER_DIRT) || state.is(HOLYSTONE) || state.is(HOLYSTONE_REPLACEABLES)
                || state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD);
    }

    /** What a falling boulder goes straight through: trees, plants, torches and anything else a block can replace. */
    private static boolean breakable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() || embeddable(state)) {
            return false;
        }
        if (state.is(AERCLOUDS) || BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("aercloud")) {
            return true;
        }
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(BlockTags.REPLACEABLE)
                || state.canBeReplaced()) {
            return true;
        }
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0 && hardness <= 0.2f && state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Clears what the boulder is about to hit: every breakable block within reach of any of its blocks, now and a
     * tenth of a second ahead, is broken (with its drops) so Sable's collision never sees it.
     */
    private static void breakThrough(ServerLevel level, ServerSubLevel sub, Vector3d velocity) {
        List<Vec3> centres = worldCentres(level, sub);
        if (centres.isEmpty()) {
            return;
        }
        double[] ahead = {0, 0.05, 0.1};
        AABB box = bounds(centres).minmax(bounds(centres).move(velocity.x * 0.1, velocity.y * 0.1, velocity.z * 0.1))
                .inflate(1.5);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
            for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || !breakable(level, cursor, state) || !touches(centres, velocity, ahead, x, y, z)) {
                        continue;
                    }
                    if (BoulderProtection.allowed(level, cursor)) {
                        level.destroyBlock(cursor, true);
                    }
                }
            }
        }
    }

    private static boolean touches(List<Vec3> centres, Vector3d velocity, double[] ahead, int x, int y, int z) {
        for (double t : ahead) {
            double cx = x + 0.5 - velocity.x * t, cy = y + 0.5 - velocity.y * t, cz = z + 0.5 - velocity.z * t;
            for (Vec3 centre : centres) {
                double dx = centre.x - cx, dy = centre.y - cy, dz = centre.z - cz;
                if (dx * dx + dy * dy + dz * dz < 1.25 * 1.25) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the boulder has just struck ground it can sink into: it lost most of its speed in one tick, and the
     * face it hit has at least four of its blocks pressed into embeddable ground. A glancing blow at the edge of an
     * island, or a hit on anything else, fails the test and ricochets as before.
     */
    private static boolean embedded(ServerLevel level, ServerSubLevel sub, Vector3d before, Vector3d now) {
        Vector3d change = new Vector3d(now).sub(before);
        if (change.length() < before.length() * 0.6) {
            return false;
        }
        Vector3d normal = change.normalize();
        Set<Long> cells = new HashSet<>();
        for (Vec3 centre : worldCentres(level, sub)) {
            BlockPos probe = BlockPos.containing(centre.x - normal.x * 0.9, centre.y - normal.y * 0.9,
                    centre.z - normal.z * 0.9);
            if (embeddable(level.getBlockState(probe))) {
                cells.add(probe.asLong());
            }
        }
        return cells.size() >= 4;
    }

    /** Whether a settling block may go where {@code there} is. */
    private static boolean fits(ServerLevel level, BlockPos pos, BlockState there, boolean sunk) {
        return there.isAir() || there.canBeReplaced() && there.getFluidState().isEmpty()
                || sunk && (embeddable(there) || breakable(level, pos, there));
    }

    private static void release(BoulderRecords records, UUID id) {
        records.remove(id);
        ACTIVE.remove(id);
        HURT.remove(id);
    }

    /** Keeps {@link #ACTIVE} and the records right when Sable takes a boulder away for good. */
    private static void observe(ServerSubLevelContainer container) {
        if (container == null || !OBSERVED.add(container)) {
            return;
        }
        container.addObserver(new SubLevelObserver() {
            @Override
            public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
                if (reason == SubLevelRemovalReason.REMOVED && isBoulder(subLevel)
                        && subLevel.getLevel() instanceof ServerLevel level) {
                    release(BoulderRecords.get(level), subLevel.getUniqueId());
                }
            }
        });
    }

    /** Keeps a falling boulder from reaching a speed at which it could pass through thin ground between two steps. */
    private static void onPrePhysics(ForgeSablePrePhysicsTickEvent event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        SubLevelPhysicsSystem system = event.getPhysicsSystem();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(system.getLevel());
        if (container == null) {
            return;
        }
        for (ServerSubLevel sub : container.getAllSubLevels()) {
            if (!ACTIVE.contains(sub.getUniqueId())) {
                continue;
            }
            RigidBodyHandle handle = system.getPhysicsHandle(sub);
            if (handle == null || !handle.isValid()) {
                continue;
            }
            Vector3d velocity = handle.getLinearVelocity(new Vector3d());
            double speed = velocity.length();
            peakSpeed = Math.max(peakSpeed, speed);
            if (speed > MAX_SPEED) {
                handle.addLinearAndAngularVelocity(velocity.mul(MAX_SPEED / speed - 1), new Vector3d());
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------- impact

    /** Hurts what the moving boulder is touching: each entity once, as a falling block would. */
    private static void hurt(ServerLevel level, ServerSubLevel sub, double speed) {
        double scale = AACommonConfig.BOULDER_DAMAGE.get();
        if (scale <= 0) {
            return;
        }
        List<Vec3> centres = worldCentres(level, sub);
        if (centres.isEmpty()) {
            return;
        }
        AABB around = bounds(centres).inflate(1.5);
        Set<UUID> hurt = HURT.computeIfAbsent(sub.getUniqueId(), k -> new HashSet<>());
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, around)) {
            if (hurt.contains(entity.getUUID()) || !BoulderProtection.allowed(level, entity.blockPosition())) {
                continue;
            }
            Vec3 at = entity.position().add(0, entity.getBbHeight() / 2, 0);
            for (Vec3 centre : centres) {
                if (centre.distanceToSqr(at) < 1.6 * 1.6) {
                    hurt.add(entity.getUUID());
                    entity.hurt(damage(level), (float) Math.min(18, speed * 1.2) * (float) scale);
                    break;
                }
            }
        }
    }

    private static final ResourceKey<DamageType> DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, AlteredAether.id(AABoulderProvider.DAMAGE));

    private static DamageSource damage(ServerLevel level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE));
    }

    // ---------------------------------------------------------------------------------------------------- settling

    /** Where the body's blocks are in the world now, block by block, tilted as the body is. */
    private static List<Vec3> worldCentres(ServerLevel level, ServerSubLevel sub) {
        Pose3d pose = sub.logicalPose();
        List<Vec3> centres = new ArrayList<>();
        for (BlockPos local : plotBlocks(level, sub)) {
            Vector3d at = pose.transformPosition(new Vector3d(local.getX() + 0.5, local.getY() + 0.5, local.getZ() + 0.5),
                    new Vector3d());
            centres.add(new Vec3(at.x, at.y, at.z));
        }
        return centres;
    }

    private static AABB bounds(List<Vec3> points) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Vec3 p : points) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
            maxZ = Math.max(maxZ, p.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Every solid block of the body, as a position in its plot. */
    private static List<BlockPos> plotBlocks(ServerLevel level, ServerSubLevel sub) {
        BoundingBox3ic box = sub.getPlot().getBoundingBox();
        List<BlockPos> blocks = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).isAir()) {
                        blocks.add(cursor.immutable());
                    }
                }
            }
        }
        return blocks;
    }

    /**
     * Turns a boulder that has come to rest back into blocks. Its rotation is snapped to the nearest right-angle
     * orientation (the one whose axes lie closest to where the body's actually point) and its position to the grid,
     * all relative to its first block, so the blocks keep their spacing. Blocks go only into air and replaceable
     * blocks; the whole shape is lifted a little if it would sit in the ground, and a block that still doesn't fit is
     * dropped as an item.
     */
    private static void settle(ServerLevel level, ServerSubLevel sub, int sink) {
        List<BlockPos> locals = plotBlocks(level, sub);
        if (locals.isEmpty()) {
            discard(level, sub);
            return;
        }
        Pose3d pose = sub.logicalPose();
        BlockPos first = locals.get(0);
        Vector3d firstCentre = new Vector3d(first.getX() + 0.5, first.getY() + 0.5, first.getZ() + 0.5);
        Vector3d world = pose.transformPosition(new Vector3d(firstCentre), new Vector3d());
        int[][] turn = nearestRotation(pose, firstCentre, world);

        Map<BlockPos, BlockState> states = new HashMap<>();
        for (BlockPos local : locals) {
            states.put(local, level.getBlockState(local));
        }
        BlockPos base = BlockPos.containing(world.x, world.y, world.z);
        Map<BlockPos, BlockState> placed = new HashMap<>();
        for (int lift = -sink; lift <= 3 && placed.isEmpty(); lift++) {
            int blocked = 0;
            Map<BlockPos, BlockState> attempt = new HashMap<>();
            for (BlockPos local : locals) {
                BlockPos at = base.offset(rotate(turn, local.getX() - first.getX(), local.getY() - first.getY(),
                        local.getZ() - first.getZ())).above(lift);
                attempt.put(at, states.get(local));
                if (!fits(level, at, level.getBlockState(at), sink > 0)) {
                    blocked++;
                }
            }
            if (blocked * 10 <= locals.size() || lift == 3) {
                placed = attempt;
            }
        }

        BlockPos middle = BlockPos.containing(bounds(worldCentres(level, sub)).getCenter());
        boolean allowed = BoulderProtection.allowed(level, middle);

        for (BlockPos local : locals) {
            level.setBlock(local, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        release(BoulderRecords.get(level), sub.getUniqueId());
        SubLevelContainer.getContainer(level).removeSubLevel(sub, SubLevelRemovalReason.REMOVED);

        if (!allowed) {
            AlteredAether.LOGGER.info("Falling boulders: one came to rest at {} where boulders aren't allowed (structure or "
                    + "claim) and was removed", middle);
            dust(level, middle, Blocks.STONE.defaultBlockState(), 40);
            return;
        }
        pushOut(level, placed.keySet());
        for (Map.Entry<BlockPos, BlockState> entry : placed.entrySet()) {
            BlockState there = level.getBlockState(entry.getKey());
            if (fits(level, entry.getKey(), there, sink > 0)) {
                level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_ALL);
            } else {
                Block.popResource(level, entry.getKey(), new ItemStack(entry.getValue().getBlock()));
            }
        }
        dust(level, middle, locals.isEmpty() ? Blocks.STONE.defaultBlockState() : states.get(locals.get(0)), 60);
        if (sink > 0) {
            // the impact: grit thrown from the ground it buried itself in
            dust(level, middle.below(), level.getBlockState(middle.below(2)), 80);
            level.playSound(null, middle, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.5f, 0.7f);
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(middle.getX(), middle.getY(), middle.getZ()) < 48 * 48) {
                AAAdvancements.award(player, AAAdvancements.ROCKFALL, "landed");
            }
        }
        level.playSound(null, middle, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 2.0f, 0.5f);
    }

    /** Removes a boulder without placing it: it fell out of the world, or went where it is not allowed to land. */
    private static void discard(ServerLevel level, ServerSubLevel sub) {
        for (BlockPos local : plotBlocks(level, sub)) {
            level.setBlock(local, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        release(BoulderRecords.get(level), sub.getUniqueId());
        SubLevelContainer.getContainer(level).removeSubLevel(sub, SubLevelRemovalReason.REMOVED);
    }

    /** Lifts anything caught where the blocks are about to go to the top of them, so nobody is built into. */
    private static void pushOut(ServerLevel level, Set<BlockPos> cells) {
        AABB area = null;
        Map<Long, Integer> tops = new HashMap<>();
        for (BlockPos cell : cells) {
            AABB box = new AABB(cell);
            area = area == null ? box : area.minmax(box);
            tops.merge(column(cell.getX(), cell.getZ()), cell.getY(), Math::max);
        }
        if (area == null) {
            return;
        }
        for (Entity entity : level.getEntities((Entity) null, area, e -> !e.isSpectator())) {
            Integer top = tops.get(column(Mth.floor(entity.getX()), Mth.floor(entity.getZ())));
            if (top != null && entity.getY() < top + 1) {
                entity.teleportTo(entity.getX(), top + 1, entity.getZ());
            }
        }
    }

    private static void dust(ServerLevel level, BlockPos at, BlockState state, int count) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.getX() + 0.5, at.getY() + 0.5,
                at.getZ() + 0.5, count, 1.5, 1.0, 1.5, 0.1);
        level.sendParticles(ParticleTypes.CLOUD, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, count / 4, 1.5, 0.5,
                1.5, 0.05);
    }

    // ---------------------------------------------------------------------------------------------------- rotation

    /**
     * Of the 24 right-angle rotations, the one that best matches how the body is turned: read off the pose by seeing
     * where it sends each axis, so it doesn't depend on which way round Sable stores its rotations.
     */
    private static int[][] nearestRotation(Pose3d pose, Vector3d firstCentre, Vector3d world) {
        Vector3d[] axis = new Vector3d[3];
        for (int j = 0; j < 3; j++) {
            Vector3d step = new Vector3d(firstCentre).add(j == 0 ? 1 : 0, j == 1 ? 1 : 0, j == 2 ? 1 : 0);
            axis[j] = pose.transformPosition(step, new Vector3d()).sub(world);
        }
        int[][] best = ROTATIONS[0];
        double bestScore = -Double.MAX_VALUE;
        for (int[][] candidate : ROTATIONS) {
            double score = 0;
            for (int j = 0; j < 3; j++) {
                score += candidate[0][j] * axis[j].x + candidate[1][j] * axis[j].y + candidate[2][j] * axis[j].z;
            }
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static BlockPos rotate(int[][] m, int x, int y, int z) {
        return new BlockPos(m[0][0] * x + m[0][1] * y + m[0][2] * z, m[1][0] * x + m[1][1] * y + m[1][2] * z,
                m[2][0] * x + m[2][1] * y + m[2][2] * z);
    }

    /** Every signed permutation matrix with determinant +1: the 24 ways to turn a cube onto itself. */
    private static int[][][] rotations() {
        int[][] perms = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        int[] parity = {1, -1, -1, 1, 1, -1};
        List<int[][]> found = new ArrayList<>();
        for (int p = 0; p < 6; p++) {
            for (int signs = 0; signs < 8; signs++) {
                int[] s = {(signs & 1) == 0 ? 1 : -1, (signs & 2) == 0 ? 1 : -1, (signs & 4) == 0 ? 1 : -1};
                if (parity[p] * s[0] * s[1] * s[2] != 1) {
                    continue;
                }
                int[][] m = new int[3][3];
                for (int row = 0; row < 3; row++) {
                    m[row][perms[p][row]] = s[row];
                }
                found.add(m);
            }
        }
        return found.toArray(new int[0][][]);
    }

    /** Packs a block column into a map key. */
    private static long column(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private FallingBoulders() {
    }
}
