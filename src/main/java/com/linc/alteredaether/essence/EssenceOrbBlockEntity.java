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
package com.linc.alteredaether.essence;

import com.linc.alteredaether.AATags;
import com.linc.alteredaether.advancement.AAAdvancements;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.electric.AetherJarBlock;
import com.linc.alteredaether.electric.AetherJarBlockEntity;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * An essence source at work. Every second it looks for its circle: a capstone at each of the eight compass points,
 * level with the orb and in plain sight of it (3 to 6 blocks out on the cardinals, 2 to 4 each way on the diagonals);
 * a gemmed capstone catches an eighth of the flow only while the one opposite it holds the same gem and stands just as far out ({@link #capture}). Below it, up to six blocks down, it looks for
 * an essence plinth, and around it, within six blocks and up to six down, for Aether Jars with a lightning rod on top.
 * Every half second it pours its flow, times the capture, into the plinth and into every one of those jars alike, and
 * blesses the circle: it heals players, mends what they hold, and quickens crops and saplings.
 *
 * <p>The flow is the source's own ({@link EssenceKind#flow}, by a grade rolled once) swelling and ebbing slowly over a
 * long breath. Players near it are sent the circle (which capstones, the plinth, the jars charging), from which their
 * game draws the rings and the motes of light.
 */
public class EssenceOrbBlockEntity extends BlockEntity {
    /** The eight compass points, N, NE, E, SE, S, SW, W, NW: opposite points are four apart. */
    public static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1};
    public static final int[] DZ = {-1, -1, 0, 1, 1, 1, 0, -1};
    /** How far out a capstone may stand: on a cardinal, and each way on a diagonal. */
    public static final int CARDINAL_MIN = 3, CARDINAL_MAX = 6, DIAGONAL_MIN = 2, DIAGONAL_MAX = 4;
    /** How far below the orb the plinth and jars may stand, and how far out the jars and the blessings reach. */
    public static final int DEPTH = 6;
    public static final double RADIUS = 6.5;
    /** How far above the orb the blessings reach. */
    public static final int HEADROOM = 3;
    /** The most charging jars told to players, for the motes. */
    private static final int SHOWN_JARS = 16;
    private static final int CIRCLE_SCAN = 20, JAR_SCAN = 40, POUR = 10, QUICKEN_SCAN = 100;

    /** In [0, 1), or below 0 until rolled. */
    private double grade = -1;
    /** Bit {@code i} set when the capstone at compass point {@code i} holds a gem. */
    private int gems;
    /** Of {@link #gems}, the capstones whose opposite holds the same gem the same distance out: the ones that work. */
    private int paired;
    private final BlockPos[] capstones = new BlockPos[8];
    @Nullable
    private BlockPos plinth;
    private boolean plinthActive;
    private final List<BlockPos> jars = new ArrayList<>();
    private final List<BlockPos> charging = new ArrayList<>();
    private final LongArrayList quickened = new LongArrayList();
    private double owed, regenOwed, repairOwed, growthOwed;
    @Nullable
    private CompoundTag lastSent;

    public EssenceOrbBlockEntity(BlockPos pos, BlockState state) {
        super(EssenceRegistries.ORB_BLOCK_ENTITY.get(), pos, state);
    }

    public EssenceKind kind() {
        return getBlockState().getBlock() instanceof EssenceOrbBlock orb ? orb.kind() : EssenceKind.CHANNEL;
    }

    public double grade() {
        return Math.max(grade, 0);
    }

    /** Sets the grade, for tests and commands. */
    public void setGrade(double grade) {
        this.grade = Mth.clamp(grade, 0.0, 0.999_999);
        setChanged();
    }

    public int gems() {
        return gems;
    }

    /**
     * The capstones that work: bit {@code i} set when the capstone at compass point {@code i} and the one opposite it
     * hold the same gem and stand the same distance out. Each pair may stand at its own distance, with its own gem.
     */
    public int pairedGems() {
        return paired;
    }

    /** The share of the flow the circle catches: an eighth for each capstone of a working pair. */
    public double capture() {
        return Integer.bitCount(pairedGems()) / 8.0;
    }

    @Nullable
    public BlockPos capstone(int point) {
        return capstones[point];
    }

    @Nullable
    public BlockPos plinth() {
        return plinth;
    }

    public boolean plinthActive() {
        return plinthActive;
    }

    /** The jars charging now (on the client, those the server told about). */
    public List<BlockPos> charging() {
        return charging;
    }

    /** Where the breath stands at {@code time}, in [-1, 1]. */
    public double breath(long time) {
        double phase = (Mth.getSeed(worldPosition) >>> 8 & 0xFFFF) / 65536.0;
        return Math.sin(2 * Math.PI * (time / (double) AACommonConfig.ESSENCE_BREATH_TICKS.get() + phase));
    }

    /** FE per tick a full circle draws at {@code time}. */
    public double flow(long time) {
        return kind().flow(grade()) * (1 + AACommonConfig.ESSENCE_BREATH.get() * breath(time));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EssenceOrbBlockEntity orb) {
        if (level instanceof ServerLevel server) {
            orb.tick(server, pos);
        }
    }

    private void tick(ServerLevel level, BlockPos pos) {
        if (grade < 0 && settle(level, pos)) {
            return;
        }
        if (grade < 0) {
            RandomSource roll = RandomSource.create(level.getSeed() ^ Mth.getSeed(pos) ^ 0x5EEDE55E11CEL);
            setGrade(roll.nextDouble());
        }
        long time = level.getGameTime() + (Mth.getSeed(pos) & 0x3FF);
        if (time % CIRCLE_SCAN == 0) {
            scanCircle(level, pos);
        }
        if (time % JAR_SCAN == 0) {
            scanJars(level, pos);
        }
        if (time % QUICKEN_SCAN == 0) {
            scanQuickened(level, pos);
        }
        if (time % POUR == 0) {
            pour(level, pos, level.getGameTime());
        }
        sync(level);
    }

    /**
     * On a new orb's first tick: a tree from a neighbouring chunk may have grown its leaves up round where the orb was
     * hung, so if there are leaves just below it, it rises clear of them. Returns whether it moved (this block entity
     * is then gone).
     */
    private boolean settle(ServerLevel level, BlockPos pos) {
        for (int lift = 0; lift <= 8; lift++) {
            BlockPos at = pos.above(lift);
            if (lift > 0 && !level.getBlockState(at).isAir()) {
                return false;
            }
            if (!level.getBlockState(at.below()).is(BlockTags.LEAVES)
                    && !level.getBlockState(at.below(2)).is(BlockTags.LEAVES)) {
                if (lift == 0) {
                    return false;
                }
                BlockState state = getBlockState();
                level.removeBlock(pos, false);
                level.setBlock(at, state, Block.UPDATE_ALL);
                return true;
            }
        }
        return false;
    }

    /** Finds the capstones and the plinth. */
    private void scanCircle(ServerLevel level, BlockPos pos) {
        int before = paired;
        gems = 0;
        Item[] set = new Item[8];
        int[] reach = new int[8];
        Vec3 centre = Vec3.atCenterOf(pos);
        for (int point = 0; point < 8; point++) {
            capstones[point] = null;
            boolean diagonal = (point & 1) == 1;
            int min = diagonal ? DIAGONAL_MIN : CARDINAL_MIN, max = diagonal ? DIAGONAL_MAX : CARDINAL_MAX;
            for (int r = min; r <= max; r++) {
                BlockPos at = pos.offset(DX[point] * r, 0, DZ[point] * r);
                BlockState state = level.getBlockState(at);
                if (state.getBlock() instanceof EssenceCapstoneBlock) {
                    if (inSight(level, centre, at)) {
                        capstones[point] = at;
                        reach[point] = r;
                        if (EssenceCapstoneBlock.gemmed(state)
                                && level.getBlockEntity(at) instanceof EssenceCapstoneBlockEntity capstone
                                && !capstone.gem().isEmpty()) {
                            gems |= 1 << point;
                            set[point] = capstone.gem().getItem();
                        }
                    }
                    break;
                }
            }
        }
        paired = 0;
        for (int point = 0; point < 4; point++) {
            int opposite = point + 4;
            if (set[point] != null && set[point] == set[opposite] && reach[point] == reach[opposite]) {
                paired |= 1 << point | 1 << opposite;
            }
        }
        if (paired == 0xFF && before != 0xFF) {
            for (Player player : level.getEntitiesOfClass(Player.class, new AABB(pos).inflate(16))) {
                AAAdvancements.award(player, AAAdvancements.FULL_CIRCLE, "circle");
            }
        }
        plinth = null;
        for (int down = 1; down <= DEPTH; down++) {
            BlockPos at = pos.below(down);
            BlockState state = level.getBlockState(at);
            if (state.getBlock() instanceof EssencePlinthBlock) {
                plinth = at;
                break;
            }
            if (!state.getCollisionShape(level, at).isEmpty()) {
                break;
            }
        }
    }

    /** Whether nothing solid stands between the orb's middle and the capstone at {@code at}. */
    private static boolean inSight(Level level, Vec3 from, BlockPos at) {
        BlockHitResult hit = level.clip(new ClipContext(from, Vec3.atCenterOf(at), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(at);
    }

    /** Finds the Aether Jars with a lightning rod on top in reach, nearest first. */
    private void scanJars(ServerLevel level, BlockPos pos) {
        jars.clear();
        int reach = Mth.ceil(RADIUS);
        int minCx = (pos.getX() - reach) >> 4, maxCx = (pos.getX() + reach) >> 4;
        int minCz = (pos.getZ() - reach) >> 4, maxCz = (pos.getZ() + reach) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof AetherJarBlockEntity jar && !jar.creative() && inReach(pos, jar.getBlockPos())
                            && AetherJarBlock.rodOn(jar.getBlockState(),
                            level.getBlockState(jar.getBlockPos().relative(jar.facing())))) {
                        jars.add(jar.getBlockPos());
                    }
                }
            }
        }
        jars.sort(Comparator.comparingDouble(at -> at.distSqr(pos)));
        int cap = AACommonConfig.MAX_ROD_JARS.get();
        if (cap > 0 && jars.size() > cap) {
            jars.subList(cap, jars.size()).clear();
        }
    }

    /** Whether {@code at} is within the jars' and blessings' reach: six out, and from six down to level. */
    public static boolean inReach(BlockPos orb, BlockPos at) {
        int dy = at.getY() - orb.getY();
        double dx = at.getX() - orb.getX(), dz = at.getZ() - orb.getZ();
        return dy <= 0 && dy >= -DEPTH && dx * dx + dz * dz <= RADIUS * RADIUS;
    }

    /** Lists the crops and saplings in the circle, for the quickening. */
    private void scanQuickened(ServerLevel level, BlockPos pos) {
        quickened.clear();
        if (gems == 0) {
            return;
        }
        int reach = Mth.floor(RADIUS);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) {
                    continue;
                }
                for (int dy = -DEPTH; dy <= HEADROOM; dy++) {
                    at.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (level.getBlockState(at).is(AATags.ESSENCE_QUICKENED)) {
                        quickened.add(at.asLong());
                    }
                }
            }
        }
    }

    /** Pours half a second's flow into the plinth and the jars, and blesses the circle. */
    private void pour(ServerLevel level, BlockPos pos, long time) {
        charging.clear();
        boolean active = false;
        double capture = capture();
        if (capture > 0) {
            owed += flow(time) * capture * POUR;
            int give = (int) owed;
            owed -= give;
            if (give > 0) {
                for (BlockPos at : jars) {
                    if (level.getBlockEntity(at) instanceof AetherJarBlockEntity jar && jar.charge(give) > 0) {
                        charging.add(at);
                    }
                }
                if (plinth != null && level.getBlockEntity(plinth) instanceof EssencePlinthBlockEntity entity) {
                    active = entity.feed(give, kind());
                }
            }
            bless(level, pos, capture);
        } else {
            owed = 0;
        }
        plinthActive = active;
    }

    private void bless(ServerLevel level, BlockPos pos, double capture) {
        EssenceKind kind = kind();
        regenOwed += POUR * capture / kind.regenTicks();
        repairOwed += POUR * capture / AACommonConfig.ESSENCE_REPAIR_TICKS.get();
        growthOwed += capture * kind.growthTicks() * POUR / 20.0;
        int heals = (int) regenOwed, mends = (int) repairOwed, quickens = (int) growthOwed;
        regenOwed -= heals;
        repairOwed -= mends;
        growthOwed -= quickens;
        if (heals > 0 || mends > 0) {
            AABB box = new AABB(pos.getX() - RADIUS + 0.5, pos.getY() - DEPTH, pos.getZ() - RADIUS + 0.5,
                    pos.getX() + RADIUS + 0.5, pos.getY() + HEADROOM + 1, pos.getZ() + RADIUS + 0.5);
            for (Player player : level.getEntitiesOfClass(Player.class, box, Player::isAlive)) {
                double dx = player.getX() - pos.getX() - 0.5, dz = player.getZ() - pos.getZ() - 0.5;
                if (dx * dx + dz * dz > RADIUS * RADIUS) {
                    continue;
                }
                if (heals > 0 && player.getHealth() < player.getMaxHealth()) {
                    player.heal(heals);
                }
                if (mends > 0) {
                    mend(player, mends);
                }
            }
        }
        if (quickens > 0 && !quickened.isEmpty()) {
            for (int i = 0; i < quickens; i++) {
                BlockPos at = BlockPos.of(quickened.getLong(level.random.nextInt(quickened.size())));
                BlockState state = level.getBlockState(at);
                if (state.is(AATags.ESSENCE_QUICKENED) && state.isRandomlyTicking()) {
                    state.randomTick(level, at, level.random);
                }
            }
        }
    }

    /** Mends {@code points} of durability on what {@code player} holds. */
    public static void mend(Player player, int points) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isDamaged()) {
                stack.setDamageValue(Math.max(0, stack.getDamageValue() - points));
            }
        }
    }

    // -- what players near the orb are told --

    private void sync(ServerLevel level) {
        CompoundTag now = circleTag();
        if (!now.equals(lastSent)) {
            lastSent = now;
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private CompoundTag circleTag() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("grade", grade());
        tag.putInt("gems", gems);
        tag.putInt("paired", paired);
        long[] stones = new long[8];
        for (int i = 0; i < 8; i++) {
            stones[i] = capstones[i] == null ? Long.MIN_VALUE : capstones[i].asLong();
        }
        tag.putLongArray("capstones", stones);
        if (plinth != null) {
            tag.putLong("plinth", plinth.asLong());
        }
        tag.putBoolean("active", plinthActive);
        long[] shown = new long[Math.min(SHOWN_JARS, charging.size())];
        for (int i = 0; i < shown.length; i++) {
            shown[i] = charging.get(i).asLong();
        }
        tag.putLongArray("charging", shown);
        return tag;
    }

    private void readCircle(CompoundTag tag) {
        grade = tag.getDouble("grade");
        gems = tag.getInt("gems");
        paired = tag.getInt("paired");
        long[] stones = tag.getLongArray("capstones");
        for (int i = 0; i < 8; i++) {
            capstones[i] = i < stones.length && stones[i] != Long.MIN_VALUE ? BlockPos.of(stones[i]) : null;
        }
        plinth = tag.contains("plinth") ? BlockPos.of(tag.getLong("plinth")) : null;
        plinthActive = tag.getBoolean("active");
        charging.clear();
        for (long at : tag.getLongArray("charging")) {
            charging.add(BlockPos.of(at));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return circleTag();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        readCircle(tag);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("gems")) {
            // an update from the server
            readCircle(tag);
        } else {
            grade = tag.contains("grade") ? tag.getDouble("grade") : -1;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (grade >= 0) {
            tag.putDouble("grade", grade);
        }
    }

    // -- the client: notes of light about the orb, motes drifting out to what it feeds --

    public static void clientTick(Level level, BlockPos pos, BlockState state, EssenceOrbBlockEntity orb) {
        orb.particles(level, pos, level.random);
    }

    private void particles(Level level, BlockPos pos, RandomSource random) {
        Vec3 centre = Vec3.atCenterOf(pos);
        if (random.nextInt(7) == 0) {
            level.addParticle(EssenceRegistries.ESSENCE_NOTE.get(),
                    centre.x + (random.nextDouble() - 0.5) * 0.5, centre.y + (random.nextDouble() - 0.5) * 0.5,
                    centre.z + (random.nextDouble() - 0.5) * 0.5,
                    (random.nextDouble() - 0.5) * 0.02, random.nextDouble() * 0.01, (random.nextDouble() - 0.5) * 0.02);
        }
        int working = pairedGems();
        if (working == 0) {
            return;
        }
        List<Vec3> targets = new ArrayList<>(8 + charging.size());
        List<Vec3> streams = new ArrayList<>(2);
        for (int point = 0; point < 8; point++) {
            if ((working >> point & 1) == 1 && capstones[point] != null) {
                targets.add(Vec3.atCenterOf(capstones[point]));
            }
        }
        if (plinth != null && plinthActive) {
            streams.add(new Vec3(plinth.getX() + 0.5, plinth.getY() + EssencePlinthBlock.TOP + 0.1, plinth.getZ() + 0.5));
        }
        for (BlockPos jar : charging) {
            Vec3 tip = rodTip(level, jar);
            if (jar.getX() == pos.getX() && jar.getZ() == pos.getZ()) {
                streams.add(tip);
            } else {
                targets.add(tip);
            }
        }
        // a mote to each about every three seconds, but no more than a couple a second however many there are
        float chance = Math.min(1 / 60.0F, 1.5F / 20.0F / Math.max(1, targets.size()));
        for (Vec3 target : targets) {
            if (random.nextFloat() < chance) {
                double distance = target.distanceTo(centre);
                int life = 40 + random.nextInt(21) + (int) (distance * 3);
                level.addParticle(new EssenceMoteOptions(target, Math.min(life, 80), 0.6F + (float) distance * 0.15F),
                        centre.x, centre.y, centre.z, 0, 0, 0);
            }
        }
        for (Vec3 target : streams) {
            if (random.nextInt(10) == 0) {
                double distance = target.distanceTo(centre);
                int life = 40 + random.nextInt(16) + (int) (distance * 5);
                level.addParticle(new EssenceMoteOptions(target, Math.min(life, 90), 0.25F),
                        centre.x + (random.nextDouble() - 0.5) * 0.15, centre.y - 0.1,
                        centre.z + (random.nextDouble() - 0.5) * 0.15, 0, 0, 0);
            }
        }
    }

    /** The tip of the lightning rod on a jar's top, where a mote ends. */
    private static Vec3 rodTip(Level level, BlockPos jar) {
        BlockState state = level.getBlockState(jar);
        if (state.getBlock() instanceof AetherJarBlock) {
            var facing = state.getValue(AetherJarBlock.FACING);
            return Vec3.atCenterOf(jar.relative(facing)).add(facing.getStepX() * 0.4, facing.getStepY() * 0.4,
                    facing.getStepZ() * 0.4);
        }
        return Vec3.atCenterOf(jar);
    }
}
