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
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Where gleamberries sit in the vanilla creative tabs, in a running client: with the system property
 * {@code altered_aether.tab_check} set (the client run's {@code -PaaTabCheck=<label>}), it quick-plays the world
 * "tour", opens Natural Blocks and Food & Drinks scrolled to the berries, logs each tab's berries and their
 * neighbours under "Tab check", shoots {@code screenshots/tabs_<label>/NN_tab.png} and quits.
 */
public final class TabCheck {
    private static final String LABEL = System.getProperty("altered_aether.tab_check", "");

    private TabCheck() {
    }

    private static void log(String message, Object... args) {
        AlteredAether.LOGGER.info("Tab check: " + message, args);
    }

    /** Opens {@code tab} in the creative inventory scrolled to its berries, and logs them. */
    private static void open(Minecraft mc, CreativeModeTab tab) {
        CreativeModeInventoryScreen screen = new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(),
                mc.options.operatorItemsTab().get());
        mc.setScreen(screen);
        try {
            var select = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
            select.setAccessible(true);
            select.invoke(screen, tab);
        } catch (ReflectiveOperationException e) {
            AlteredAether.LOGGER.warn("Tab check: couldn't pick the tab", e);
        }
        List<ItemStack> items = new ArrayList<>(tab.getDisplayItems());
        String name = tab.getDisplayName().getString();
        log("{} lists {} items", name, items.size());
        int gleam = -1;
        for (Item item : new Item[]{Items.GLOW_BERRIES, Items.SWEET_BERRIES, AARegistries.GLEAMBERRIES.get()}) {
            List<Integer> at = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).is(item)) {
                    at.add(i);
                }
            }
            log("{}: {} at {}", name, BuiltInRegistries.ITEM.getKey(item), at);
            if (item == AARegistries.GLEAMBERRIES.get() && !at.isEmpty()) {
                gleam = at.getFirst();
            }
        }
        int focus = gleam;
        if (focus < 0) {
            for (int i = 0; i < items.size() && focus < 0; i++) {
                if (items.get(i).is(Items.GLOW_BERRIES) || items.get(i).is(Items.SWEET_BERRIES)) {
                    focus = i;
                }
            }
        }
        if (focus < 0) {
            return;
        }
        List<String> around = new ArrayList<>();
        for (int i = Math.max(0, focus - 3); i <= Math.min(items.size() - 1, focus + 3); i++) {
            around.add(i + "=" + BuiltInRegistries.ITEM.getKey(items.get(i).getItem()));
        }
        log("{} around the berries: {}", name, around);
        // scroll so the berries' row is in view (the grid shows 5 rows)
        int rows = (items.size() + 8) / 9;
        float scroll = rows <= 5 ? 0 : Math.min(1, Math.max(0, (focus / 9 - 2) / (float) (rows - 5)));
        try {
            var field = CreativeModeInventoryScreen.class.getDeclaredField("scrollOffs");
            field.setAccessible(true);
            field.setFloat(screen, scroll);
        } catch (ReflectiveOperationException e) {
            AlteredAether.LOGGER.warn("Tab check: couldn't move the scroll bar", e);
        }
        screen.getMenu().scrollTo(scroll);
        // the mouse to the window's corner, so no tooltip covers the grid
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 0, 0);
        try {
            for (String axis : new String[]{"xpos", "ypos"}) {
                var field = net.minecraft.client.MouseHandler.class.getDeclaredField(axis);
                field.setAccessible(true);
                field.setDouble(mc.mouseHandler, 0);
            }
        } catch (ReflectiveOperationException e) {
            AlteredAether.LOGGER.warn("Tab check: couldn't move the mouse", e);
        }
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, value = Dist.CLIENT)
    public static final class Client {
        private static final CreativeModeTab[] TABS = new CreativeModeTab[2];
        private static int ticks, index = -1, waited;

        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (LABEL.isBlank() || mc.player == null || mc.level == null || index >= TABS.length) {
                return;
            }
            if (index < 0) {
                if (++ticks < 100) {
                    return;
                }
                TABS[0] = BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(CreativeModeTabs.NATURAL_BLOCKS);
                TABS[1] = BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(CreativeModeTabs.FOOD_AND_DRINKS);
                index = 0;
                waited = 0;
                open(mc, TABS[0]);
                return;
            }
            if (++waited < 20) {
                return;
            }
            String dir = "tabs_" + LABEL;
            new java.io.File(mc.gameDirectory, "screenshots/" + dir).mkdirs();
            String file = dir + "/" + String.format("%02d_%s.png", index, index == 0 ? "natural_blocks" : "food_and_drinks");
            Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(),
                    message -> AlteredAether.LOGGER.info("Tab check: {}", message.getString()));
            index++;
            waited = 0;
            if (index < TABS.length) {
                open(mc, TABS[index]);
            } else {
                log("done");
                mc.stop();
            }
        }

        private Client() {
        }
    }
}
