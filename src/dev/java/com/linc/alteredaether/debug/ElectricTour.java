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

import com.aetherteam.aether.block.AetherBlocks;
import com.aetherteam.aether.item.AetherItems;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.electric.AetherJarBlockEntity;
import com.linc.alteredaether.electric.ElectricRegistries;
import com.linc.alteredaether.electric.SquallGeneratorBlock;
import com.linc.alteredaether.electric.SquallGeneratorHost;
import com.linc.alteredaether.electric.SquallInsulatorBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A scripted look at the Tempest Peaks electricity in a running client, the parts a GameTest can't see: with the
 * system property {@code altered_aether.electric_tour} set (the client run's {@code -PaaElectricTour=<label>}), it
 * quick-plays the world "tour", builds a stage in open sky over the Aether's spawn and shoots
 * {@code screenshots/electric_<label>/NN_name.png}: the jars at each charge, the insulator and lit generators facing
 * several ways, the storm aercloud and the items, Jade on a jar and a generator, JEI's flash blasting page, and with
 * Create the wheel turning in its frame, a generator over a depot, and the wheel's Ponder scene. Then it quits.
 */
public final class ElectricTour {
    private static final String LABEL = System.getProperty("altered_aether.electric_tour", "");
    private static final ResourceKey<Level> THE_AETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));
    private static final int STAGE_Y = 285, STAGE_OFFSET = 900;
    private static final boolean CREATE = ModList.get().isLoaded("create");

    private static volatile BlockPos origin;

    static BlockPos origin() {
        return origin;
    }

    record Step(String name, Consumer<ServerLevel> setup, Vec3 cam, Vec3 look, int delay, boolean gui, Runnable client) {
    }

    private static final BlockPos JARS = new BlockPos(-6, 0, -4);
    private static final BlockPos INSULATOR = new BlockPos(0, 0, -4);
    private static final BlockPos ROD_JAR = new BlockPos(-4, 0, -7);
    private static final BlockPos GENERATOR_UP = new BlockPos(2, 0, -4);
    private static final BlockPos GENERATOR_NORTH = new BlockPos(4, 0, -3);
    private static final BlockPos CLOUD = new BlockPos(-4, 0, 2);
    static final BlockPos WHEEL = new BlockPos(4, 1, 3);
    static final BlockPos DEPOT = new BlockPos(-1, 0, 4);

    private ElectricTour() {
    }

    private static boolean on() {
        return !LABEL.isBlank();
    }

    static void log(String message, Object... args) {
        AlteredAether.LOGGER.info("Electric tour: " + message, args);
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID)
    public static final class Server {
        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!on() || !(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            MinecraftServer server = player.getServer();
            ServerLevel aether = server.getLevel(THE_AETHER);
            if (aether == null) {
                AlteredAether.LOGGER.error("Electric tour: no Aether");
                return;
            }
            BlockPos spawn = aether.getSharedSpawnPos();
            BlockPos o = new BlockPos(spawn.getX() + STAGE_OFFSET, STAGE_Y, spawn.getZ());
            aether.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            aether.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            aether.setDayTime(6000);
            player.getInventory().clearContent();
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(aether, o.getX() + 0.5, o.getY() + 4, o.getZ() + 10.5, 180, 20);
            stage(aether, o);
            origin = o;
            log("stage at {}", o.toShortString());
        }

        private Server() {
        }
    }

    private static void stage(ServerLevel level, BlockPos o) {
        AABB area = new AABB(o.offset(-10, -2, -10).getCenter(), o.offset(10, 8, 10).getCenter());
        for (Entity entity : level.getEntitiesOfClass(Entity.class, area, e -> !(e instanceof ServerPlayer))) {
            entity.discard();
        }
        for (BlockPos pos : BlockPos.betweenClosed(o.offset(-10, -2, -10), o.offset(10, 8, 10))) {
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.Clearable clearable) {
                clearable.clearContent();
            }
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
        var bricks = AetherBlocks.HOLYSTONE_BRICKS.get().defaultBlockState();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                level.setBlockAndUpdate(o.offset(x, -1, z), bricks);
            }
        }
        for (int i = 0; i <= 4; i++) {
            BlockPos at = o.offset(JARS).east(i);
            level.setBlockAndUpdate(at, ElectricRegistries.AETHER_JAR.get().defaultBlockState());
            if (level.getBlockEntity(at) instanceof AetherJarBlockEntity jar) {
                jar.charge(AetherJarBlockEntity.capacity() * i / 4);
            }
        }
        // a jar with a lightning rod set on its tip
        level.setBlockAndUpdate(o.offset(ROD_JAR), ElectricRegistries.AETHER_JAR.get().defaultBlockState());
        level.setBlockAndUpdate(o.offset(ROD_JAR).above(), Blocks.LIGHTNING_ROD.defaultBlockState());
        level.setBlockAndUpdate(o.offset(INSULATOR), ElectricRegistries.SQUALL_INSULATOR.get().defaultBlockState()
                .setValue(SquallInsulatorBlock.FACING, Direction.UP));
        generator(level, o.offset(GENERATOR_UP), Direction.UP);
        // facing north, a jar behind it charging, oxidised copper in front
        generator(level, o.offset(GENERATOR_NORTH), Direction.NORTH);
        level.setBlockAndUpdate(o.offset(GENERATOR_NORTH).south(), ElectricRegistries.AETHER_JAR.get().defaultBlockState());
        level.setBlockAndUpdate(o.offset(GENERATOR_NORTH).north(), Blocks.OXIDIZED_COPPER.defaultBlockState());
        level.setBlockAndUpdate(o.offset(CLOUD), ElectricRegistries.STORM_AERCLOUD.get().defaultBlockState());
        if (CREATE) {
            ElectricTourCreate.stage(level, o);
        }
    }

    static void generator(ServerLevel level, BlockPos pos, Direction facing) {
        level.setBlockAndUpdate(pos, ElectricRegistries.SQUALL_GENERATOR.get().defaultBlockState()
                .setValue(SquallGeneratorBlock.FACING, facing));
        if (level.getBlockEntity(pos) instanceof SquallGeneratorHost host) {
            host.core().insert(new ItemStack(AetherItems.AMBROSIUM_SHARD.get(), 16));
        }
    }

    private static void drop(ServerLevel level, double dx, Item item) {
        BlockPos at = origin.offset(CLOUD).east(2);
        ItemEntity entity = new ItemEntity(level, at.getX() + 0.5 + dx, at.getY() + 0.1, at.getZ() + 0.5, new ItemStack(item));
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setPickUpDelay(32767);
        level.addFreshEntity(entity);
    }

    private static List<Step> script() {
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("jars", level -> { }, new Vec3(-3.5, 1.3, -1.3), new Vec3(-3.5, 0.5, -3.5), 20, false, null));
        steps.add(new Step("bowls", level -> { }, new Vec3(2.5, 2.2, -0.2), new Vec3(2.5, 0.4, -3.5), 30, false, null));
        steps.add(new Step("jar_tip", level -> { }, new Vec3(-1.5, 1.35, -2.75), new Vec3(-1.5, 0.95, -3.5), 20,
                false, null));
        steps.add(new Step("jar_rod", level -> { }, new Vec3(-3.5, 1.6, -5.4), new Vec3(-3.5, 1.0, -6.5), 20, false,
                null));
        // the squall turns to face the camera (the player), so it should look a different way in each
        steps.add(new Step("squall_west", level -> { }, new Vec3(1.0, 1.5, -2.6), new Vec3(2.5, 0.5, -3.5), 40, false,
                null));
        steps.add(new Step("squall_northeast", level -> { }, new Vec3(3.7, 1.5, -4.9), new Vec3(2.5, 0.5, -3.5), 40,
                false, null));
        steps.add(new Step("storm_cloud", level -> {
            drop(level, -0.6, ElectricRegistries.STATIC_BOOTS.get());
            drop(level, 0.0, ElectricRegistries.AETHER_JAR_ITEM.get());
            drop(level, 0.6, ElectricRegistries.SQUALL_GENERATOR_ITEM.get());
        }, new Vec3(-2.5, 1.6, 5.5), new Vec3(-2.5, 0.4, 2.5), 30, false, null));
        steps.add(new Step("jade_jar", level -> { }, jadeCam(JARS.east(4)), Vec3.atCenterOf(JARS.east(4)), 50, true, null));
        steps.add(new Step("jade_generator", level -> { }, jadeCam(GENERATOR_UP), Vec3.atCenterOf(GENERATOR_UP), 50, true,
                null));
        if (CREATE) {
            steps.add(new Step("wheel", level -> { }, new Vec3(4.5, 3.2, 8.5), new Vec3(4.5, 1.2, 3.5), 40, false, null));
            steps.add(new Step("depot", ElectricTourCreate::logDepot, new Vec3(-0.5, 2.5, 7.5), new Vec3(-0.5, 0.8, 4.5),
                    60, false, null));
        }
        steps.add(new Step("jei_flash_blasting", level -> { }, jadeCam(GENERATOR_UP), Vec3.atCenterOf(GENERATOR_UP), 30,
                true, () -> {
                    Minecraft.getInstance().setScreen(null);
                    if (ModList.get().isLoaded("jei")) {
                        ElectricTourJei.showType(AlteredAether.id("flash_blasting"));
                    }
                }));
        steps.add(new Step("jei_generator_uses", level -> { }, jadeCam(GENERATOR_UP), Vec3.atCenterOf(GENERATOR_UP), 30,
                true, () -> {
                    Minecraft.getInstance().setScreen(null);
                    if (ModList.get().isLoaded("jei")) {
                        GolemTourJei.show(new ItemStack(ElectricRegistries.SQUALL_GENERATOR_ITEM.get()), false);
                    }
                }));
        if (CREATE) {
            steps.add(new Step("ponder_wheel", level -> { }, jadeCam(GENERATOR_UP), Vec3.atCenterOf(GENERATOR_UP), 200,
                    true, ElectricTourCreate::ponder));
        }
        return steps;
    }

    private static Vec3 jadeCam(BlockPos block) {
        return new Vec3(block.getX() + 0.5, 0.9, block.getZ() + 2.6);
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, value = Dist.CLIENT)
    public static final class Client {
        private static List<Step> steps;
        private static int index = -1, waited, settled;
        private static int framesHere;
        private static Vec3 wanted;

        @SubscribeEvent
        public static void onRender(RenderLevelStageEvent event) {
            Vec3 want = wanted;
            if (want != null && event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL
                    && event.getCamera().getPosition().distanceToSqr(want) < 1.0E-4) {
                framesHere++;
            }
        }

        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            BlockPos o = origin;
            if (!on() || o == null || mc.player == null || mc.level == null || !mc.level.dimension().equals(THE_AETHER)) {
                return;
            }
            MinecraftServer server = mc.getSingleplayerServer();
            if (steps == null) {
                steps = script();
            }
            if (index < 0) {
                boolean ready = mc.levelRenderer.hasRenderedAllSections() && mc.level.getBlockState(o.below()).isSolid();
                settled = ready ? settled + 1 : 0;
                mc.player.moveTo(o.getX() + 0.5, o.getY() + 4, o.getZ() + 10.5, 180, 20);
                if (settled < 80) {
                    return;
                }
                next(mc, server);
                return;
            }
            Step step = steps.get(index);
            Vec3 cam = Vec3.atLowerCornerOf(o).add(step.cam());
            Vec3 look = Vec3.atLowerCornerOf(o).add(step.look());
            double dx = look.x - cam.x, dy = look.y - cam.y, dz = look.z - cam.z;
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
            float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
            mc.player.moveTo(cam.x, cam.y - mc.player.getEyeHeight(), cam.z, yaw, pitch);
            mc.player.setYHeadRot(yaw);
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.options.hideGui = !step.gui();
            if (wanted == null || wanted.distanceToSqr(cam) > 1.0E-6) {
                wanted = cam;
                framesHere = 0;
            }
            if (++waited < step.delay()) {
                return;
            }
            if (step.client() != null && waited == step.delay()) {
                step.client().run();
            }
            if (waited < step.delay() + (step.client() != null ? 40 : 0)) {
                return;
            }
            if (mc.screen == null && framesHere < 3 && waited < step.delay() + 400) {
                return;
            }
            String dir = "electric_" + LABEL;
            new java.io.File(mc.gameDirectory, "screenshots/" + dir).mkdirs();
            String file = dir + "/" + String.format("%02d_%s.png", index, step.name());
            log("shooting {}", step.name());
            Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(),
                    message -> AlteredAether.LOGGER.info("Electric tour: {}", message.getString()));
            next(mc, server);
        }

        private static void next(Minecraft mc, MinecraftServer server) {
            index++;
            waited = 0;
            if (index >= steps.size()) {
                log("done");
                origin = null;
                mc.stop();
                return;
            }
            Step step = steps.get(index);
            log("step {} {}", index, step.name());
            if (server != null) {
                server.execute(() -> {
                    ServerLevel aether = server.getLevel(THE_AETHER);
                    if (aether != null) {
                        try {
                            step.setup().accept(aether);
                        } catch (RuntimeException e) {
                            AlteredAether.LOGGER.error("Electric tour: step {} failed", step.name(), e);
                        }
                    }
                });
            }
        }

        private Client() {
        }
    }
}
