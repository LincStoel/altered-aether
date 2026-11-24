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
package com.linc.alteredaether.compat.jei;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.client.golem.GolemNetworkScreen;
import com.linc.alteredaether.entity.golem.GolemNetworkMenu;
import com.linc.alteredaether.entity.golem.GolemNetworkView;
import com.linc.alteredaether.entity.golem.GolemSelections;
import com.linc.alteredaether.registry.AARegistries;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JEI and the Golem Chest Network screen: the screen's search box can follow JEI's (and read its text as JEI does), its
 * items answer JEI's recipe and usage keys, and a recipe's "+" picks out the recipe's ingredients in the network rather
 * than filling a crafting grid, with the recipe's result searched for if the network holds any.
 */
final class GolemNetworkJei {
    private static final String KEY = "gui." + AlteredAether.MOD_ID + ".golem_network.";

    private GolemNetworkJei() {
    }

    static void runtimeAvailable(IJeiRuntime runtime) {
        GolemNetworkScreen.jei = new GolemNetworkScreen.JeiSearch() {
            @Override
            public String text() {
                return runtime.getIngredientFilter().getFilterText();
            }

            @Override
            public void setText(String text) {
                runtime.getIngredientFilter().setFilterText(text);
            }

            @Override
            public Set<Item> shown() {
                Set<Item> items = new HashSet<>();
                for (ItemStack stack : runtime.getIngredientFilter().getFilteredItemStacks()) {
                    items.add(stack.getItem());
                }
                return items;
            }
        };
    }

    static void runtimeUnavailable() {
        GolemNetworkScreen.jei = null;
    }

    static void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(GolemNetworkScreen.class, new IGuiContainerHandler<>() {
            @Override
            public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
                    IClickableIngredientFactory factory, GolemNetworkScreen screen, double mouseX, double mouseY) {
                ItemStack item = screen.itemAt(mouseX, mouseY);
                if (item == null) {
                    return Optional.empty();
                }
                int x = (int) Math.floor(mouseX), y = (int) Math.floor(mouseY);
                return factory.createBuilder(item).buildWithArea(x - 8, y - 8, 16, 16);
            }
        });
    }

    static void registerTransfer(IRecipeTransferRegistration registration) {
        registration.addUniversalRecipeTransferHandler(new Transfer(registration.getTransferHelper()));
    }

    /** "+" on any recipe: picks the ingredients the network holds, in place of moving anything. */
    private record Transfer(IRecipeTransferHandlerHelper helper) implements IUniversalRecipeTransferHandler<GolemNetworkMenu> {
        @Override
        public Class<? extends GolemNetworkMenu> getContainerClass() {
            return GolemNetworkMenu.class;
        }

        @Override
        public Optional<MenuType<GolemNetworkMenu>> getMenuType() {
            return Optional.of(AARegistries.GOLEM_NETWORK_MENU.get());
        }

        @Override
        @Nullable
        public IRecipeTransferError transferRecipe(GolemNetworkMenu menu, Object recipe, IRecipeSlotsView slots,
                                                   Player player, boolean maxTransfer, boolean doTransfer) {
            List<ItemStack> picks = new ArrayList<>();
            List<IRecipeSlotView> missing = new ArrayList<>();
            for (IRecipeSlotView slot : slots.getSlotViews(RecipeIngredientRole.INPUT)) {
                List<ItemStack> options = slot.getItemStacks().filter(stack -> !stack.isEmpty()).toList();
                if (options.isEmpty()) {
                    continue;
                }
                ItemStack best = mostStored(menu, options);
                if (best == null) {
                    missing.add(slot);
                } else if (picks.stream().noneMatch(p -> ItemStack.isSameItemSameComponents(p, best))) {
                    picks.add(best);
                }
            }
            if (picks.isEmpty()) {
                return helper.createUserErrorForMissingSlots(Component.translatable(KEY + "transfer.none"), missing);
            }
            int over = Math.max(0, picks.size() - GolemSelections.MAX);
            if (doTransfer) {
                menu.replacePicks(picks.subList(0, Math.min(picks.size(), GolemSelections.MAX)));
                slots.getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                        .flatMap(slot -> slot.getDisplayedItemStack().stream())
                        .filter(stack -> !stack.isEmpty())
                        .findFirst()
                        .filter(result -> stored(menu, result) != null)
                        .ifPresent(result -> menu.askSearch(result.getHoverName().getString()));
            }
            if (missing.isEmpty() && over == 0) {
                return null;
            }
            return new Warning(missing, over);
        }

        /** Of a slot's choices, the one the network holds the most of; null if it holds none of them. */
        @Nullable
        private static ItemStack mostStored(GolemNetworkMenu menu, List<ItemStack> options) {
            ItemStack best = null;
            long most = 0;
            for (ItemStack option : options) {
                GolemNetworkView.Entry entry = stored(menu, option);
                if (entry != null && entry.total() > most) {
                    most = entry.total();
                    best = entry.item();
                }
            }
            return best;
        }

        /**
         * What the network holds of {@code stack}: the same item with the same data, or else the most of the same item
         * with any (a recipe asks for a plain sword; the network's may be worn).
         */
        @Nullable
        private static GolemNetworkView.Entry stored(GolemNetworkMenu menu, ItemStack stack) {
            GolemNetworkView.Entry loose = null;
            for (GolemNetworkView.Entry entry : menu.contents().entries()) {
                if (ItemStack.isSameItemSameComponents(entry.item(), stack)) {
                    return entry;
                }
                if (entry.item().is(stack.getItem()) && (loose == null || entry.total() > loose.total())) {
                    loose = entry;
                }
            }
            return loose;
        }
    }

    /**
     * Some ingredients aren't stored, or there are more than there are colours: the "+" still picks what it can, and
     * the missing slots are marked red.
     */
    private record Warning(List<IRecipeSlotView> missing, int over) implements IRecipeTransferError {
        @Override
        public Type getType() {
            return Type.COSMETIC;
        }

        @Override
        public int getButtonHighlightColor() {
            return 0x80FFA500;
        }

        @Override
        public void showError(GuiGraphics graphics, int mouseX, int mouseY, IRecipeSlotsView slots, int recipeX,
                              int recipeY) {
            graphics.pose().pushPose();
            graphics.pose().translate(recipeX, recipeY, 0);
            for (IRecipeSlotView slot : missing) {
                slot.drawHighlight(graphics, 0x66FF0000);
            }
            graphics.pose().popPose();
        }

        @Override
        public List<Component> getTooltip() {
            List<Component> lines = new ArrayList<>();
            if (!missing.isEmpty()) {
                lines.add(Component.translatable(KEY + "transfer.missing").withStyle(ChatFormatting.RED));
            }
            if (over > 0) {
                lines.add(Component.translatable(KEY + "transfer.too_many", GolemSelections.MAX, GolemSelections.MAX)
                        .withStyle(ChatFormatting.GOLD));
            }
            return lines;
        }
    }
}
