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
import com.aetherteam.aether.item.AetherCreativeTabs;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
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
 * A scripted look at the gravitite rework in a running client, the parts a GameTest can't see: with the system
 * property {@code altered_aether.gravitite_tour} set (the client run's {@code -PaaGravititeTour=<label>}), it
 * quick-plays the world "tour", builds a small stage in open sky over the Aether's spawn and shoots
 * {@code screenshots/gravitite_<label>/NN_name.png}:
 *
 * <ul>
 *   <li>dropped gravitite hovering over the floor, and a hopper still collecting it;</li>
 *   <li>Jade on powered and unpowered enchanted gravitite (the lift line needs Sable) and on the raw block, which
 *   also shows the blocks' names;</li>
 *   <li>the Aether's ingredients and natural blocks creative tabs;</li>
 *   <li>JEI's pages for making the ingot and for using raw gravitite.</li>
 * </ul>
 *
 * <p>What it measures it logs under "Gravitite tour": each item's height over the floor, what the hopper holds, the
 * names the client's language gives, and the tabs' contents. Then it quits.
 */
public final class GravititeTour {
    private static final String LABEL = System.getProperty("altered_aether.gravitite_tour", "");
    private static final ResourceKey<Level> THE_AETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));
    /** High above the islands, and away from the golem tour's stage on the other side of spawn. */
    private static final int STAGE_Y = 285, STAGE_OFFSET = -600;

    /** The stage's origin, on its floor. Set on the server at login. */
    private static volatile BlockPos origin;

    /** One moment: what the server sets up, where the camera stands and looks (relative to the origin), how long after
     * the setup to shoot, whether the HUD shows, and what the client does just before. */
    record Step(String name, Consumer<ServerLevel> setup, Vec3 cam, Vec3 look, int delay, boolean gui, Runnable client) {
    }

    // where things stand on the stage, relative to the origin
    private static final BlockPos DROPS = new BlockPos(-3, 0, 0);
    private static final BlockPos HOPPER = new BlockPos(3, 0, 0);
    private static final BlockPos POWERED = new BlockPos(-3, 0, -4);
    private static final BlockPos UNPOWERED = new BlockPos(0, 0, -4);
    private static final BlockPos RAW_BLOCK = new BlockPos(3, 0, -4);

    private GravititeTour() {
    }

    private static boolean on() {
        return !LABEL.isBlank();
    }

    private static void log(String message, Object... args) {
        AlteredAether.LOGGER.info("Gravitite tour: " + message, args);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Server: the stage

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
                AlteredAether.LOGGER.error("Gravitite tour: no Aether");
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
            player.teleportTo(aether, o.getX() + 0.5, o.getY() + 4, o.getZ() + 8.5, 180, 20);
            stage(aether, o);
            origin = o;
            log("stage at {}", o.toShortString());
        }

        private Server() {
        }
    }

    private static void stage(ServerLevel level, BlockPos o) {
        AABB area = new AABB(o.offset(-8, -2, -8).getCenter(), o.offset(8, 6, 8).getCenter());
        for (Entity entity : level.getEntitiesOfClass(Entity.class, area, e -> !(e instanceof ServerPlayer))) {
            entity.discard();
        }
        for (BlockPos pos : BlockPos.betweenClosed(o.offset(-8, -2, -8), o.offset(8, 6, 8))) {
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.Clearable clearable) {
                clearable.clearContent();
            }
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
        var holystone = AetherBlocks.HOLYSTONE.get().defaultBlockState();
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                level.setBlockAndUpdate(o.offset(x, -1, z), holystone);
            }
        }
        // each gravitite block capped, or the Aether would float it away (the raw block and powered gravitite)
        gravitite(level, o.offset(POWERED), AetherBlocks.ENCHANTED_GRAVITITE.get().defaultBlockState(), true);
        gravitite(level, o.offset(UNPOWERED), AetherBlocks.ENCHANTED_GRAVITITE.get().defaultBlockState(), false);
        gravitite(level, o.offset(RAW_BLOCK), AARegistries.RAW_GRAVITITE_BLOCK.get().defaultBlockState(), null);
        level.setBlockAndUpdate(o.offset(HOPPER), Blocks.HOPPER.defaultBlockState());
    }

    /** A gravitite block under a holystone cap, with a lever on its east face (on or off), or none for null. */
    private static void gravitite(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
                                  Boolean lever) {
        level.setBlockAndUpdate(pos.above(), AetherBlocks.HOLYSTONE.get().defaultBlockState());
        level.setBlockAndUpdate(pos, state);
        if (lever != null) {
            level.setBlockAndUpdate(pos.east(), Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
                    .setValue(LeverBlock.FACING, Direction.EAST).setValue(LeverBlock.POWERED, lever));
        }
    }

    private static void drop(ServerLevel level, double dx, Item item) {
        BlockPos at = origin.offset(DROPS);
        ItemEntity entity = new ItemEntity(level, at.getX() + 0.5 + dx, at.getY() + 0.1, at.getZ() + 0.5, new ItemStack(item));
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setPickUpDelay(32767);
        level.addFreshEntity(entity);
    }

    private static void logHeights(ServerLevel level) {
        BlockPos o = origin;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(o).inflate(8))) {
            log("{} hovers {} above the floor (no gravity {})", item.getItem().getHoverName().getString(),
                    String.format("%.2f", item.getY() - o.getY()), item.isNoGravity());
        }
        if (level.getBlockEntity(o.offset(HOPPER)) instanceof HopperBlockEntity hopper) {
            List<String> held = new ArrayList<>();
            for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
                if (!hopper.getItem(slot).isEmpty()) {
                    held.add(hopper.getItem(slot).toString());
                }
            }
            log("the hopper holds {}", held);
        }
    }

    private static List<Step> script() {
        List<Step> steps = new ArrayList<>();
        Vec3 dropCam = new Vec3(-3.5, 1.4, 4.5), dropLook = new Vec3(-2.5, 1.2, 0.5);
        steps.add(new Step("dropped", level -> {
            drop(level, -1.2, AARegistries.RAW_GRAVITITE.get());
            drop(level, -0.4, AARegistries.ENCHANTED_GRAVITITE_INGOT.get());
            drop(level, 0.4, AARegistries.ENCHANTED_GRAVITITE_NUGGET.get());
            drop(level, 1.2, AARegistries.CRUSHED_RAW_GRAVITITE.get());
            BlockPos hopper = origin.offset(HOPPER);
            ItemEntity over = new ItemEntity(level, hopper.getX() + 0.5, hopper.getY() + 2, hopper.getZ() + 0.5,
                    new ItemStack(AARegistries.RAW_GRAVITITE.get()));
            over.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(over);
        }, dropCam, dropLook, 10, false, null));
        steps.add(new Step("hovering", GravititeTour::logHeights, dropCam, dropLook, 100, false, null));
        steps.add(new Step("hopper", GravititeTour::logHeights, new Vec3(3.5, 2.5, 3.5), new Vec3(3.5, 0.5, 0.5), 10,
                false, null));
        steps.add(new Step("jade_powered", level -> { }, jadeCam(POWERED), center(POWERED), 20, true, GravititeTour::logNames));
        steps.add(new Step("jade_unpowered", level -> { }, jadeCam(UNPOWERED), center(UNPOWERED), 20, true, null));
        steps.add(new Step("jade_raw_block", level -> { }, jadeCam(RAW_BLOCK), center(RAW_BLOCK), 20, true, null));
        steps.add(new Step("tab_ingredients", level -> { }, jadeCam(UNPOWERED), center(UNPOWERED), 20, true,
                () -> openTab(AetherCreativeTabs.AETHER_INGREDIENTS.get())));
        steps.add(new Step("tab_natural", level -> { }, jadeCam(UNPOWERED), center(UNPOWERED), 20, true,
                () -> openTab(AetherCreativeTabs.AETHER_NATURAL_BLOCKS.get())));
        steps.add(new Step("jei_ingot_recipes", level -> { }, jadeCam(UNPOWERED), center(UNPOWERED), 30, true,
                () -> jei(new ItemStack(AARegistries.ENCHANTED_GRAVITITE_INGOT.get()), true)));
        steps.add(new Step("jei_raw_uses", level -> { }, jadeCam(UNPOWERED), center(UNPOWERED), 30, true,
                () -> jei(new ItemStack(AARegistries.RAW_GRAVITITE.get()), false)));
        return steps;
    }

    /** Close enough in front of a block for Jade to show it under the crosshair. */
    private static Vec3 jadeCam(BlockPos block) {
        return new Vec3(block.getX() + 0.5, 0.6, block.getZ() + 2.8);
    }

    private static Vec3 center(BlockPos block) {
        return Vec3.atCenterOf(block);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Client

    /** The names the client's language gives the rework's blocks and items. */
    private static void logNames() {
        List<String> names = new ArrayList<>();
        names.add(Component.translatable(AetherBlocks.ENCHANTED_GRAVITITE.get().getDescriptionId()).getString());
        for (Item item : new Item[]{AARegistries.RAW_GRAVITITE_BLOCK_ITEM.get(), AARegistries.RAW_GRAVITITE.get(),
                AARegistries.ENCHANTED_GRAVITITE_INGOT.get(), AARegistries.ENCHANTED_GRAVITITE_NUGGET.get(),
                AARegistries.CRUSHED_RAW_GRAVITITE.get(), AARegistries.GRAVITITE_DUST.get(), AARegistries.SMALL_GRAVITITE_DUST.get()}) {
            names.add(new ItemStack(item).getHoverName().getString());
        }
        log("names: {}", names);
    }

    /** The creative inventory open on {@code tab}, its contents logged. */
    private static void openTab(CreativeModeTab tab) {
        Minecraft mc = Minecraft.getInstance();
        CreativeModeInventoryScreen screen = new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(),
                mc.options.operatorItemsTab().get());
        mc.setScreen(screen);
        try {
            var select = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
            select.setAccessible(true);
            select.invoke(screen, tab);
        } catch (ReflectiveOperationException e) {
            AlteredAether.LOGGER.warn("Gravitite tour: couldn't pick the tab", e);
        }
        List<String> gravitite = tab.getDisplayItems().stream()
                .filter(stack -> stack.getItem().builtInRegistryHolder().key().location().getPath().contains("gravitite"))
                .map(stack -> stack.getHoverName().getString()).toList();
        log("{} lists {}", tab.getDisplayName().getString(), gravitite);
    }

    private static void jei(ItemStack stack, boolean recipes) {
        Minecraft.getInstance().setScreen(null);
        if (ModList.get().isLoaded("jei")) {
            GolemTourJei.show(stack, recipes);
        } else {
            log("no JEI");
        }
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, value = Dist.CLIENT)
    public static final class Client {
        private static List<Step> steps;
        private static int index = -1, waited, settled;
        /** Frames drawn from where the current shot wants the camera: a screenshot takes the last frame drawn. */
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
                mc.player.moveTo(o.getX() + 0.5, o.getY() + 4, o.getZ() + 8.5, 180, 20);
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
            if (waited < step.delay() + (step.client() != null ? 5 : 0)) {
                return;
            }
            // wait for frames from here; a screen counts once it has drawn a few times
            if (mc.screen == null && framesHere < 3 && waited < step.delay() + 400) {
                return;
            }
            String dir = "gravitite_" + LABEL;
            new java.io.File(mc.gameDirectory, "screenshots/" + dir).mkdirs();
            String file = dir + "/" + String.format("%02d_%s.png", index, step.name());
            log("shooting {}", step.name());
            Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(),
                    message -> AlteredAether.LOGGER.info("Gravitite tour: {}", message.getString()));
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
                            AlteredAether.LOGGER.error("Gravitite tour: step {} failed", step.name(), e);
                        }
                    }
                });
            }
        }

        private Client() {
        }
    }
}
