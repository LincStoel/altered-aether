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
import com.linc.alteredaether.config.AAClientConfig.NetworkSort;
import com.linc.alteredaether.entity.golem.GolemNetworkMenu;
import com.linc.alteredaether.entity.golem.GolemNetworkView;
import com.linc.alteredaether.entity.golem.GolemSelections;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The Golem Chest Network screen: everything a golem network's storage holds, as tiles with counts, nine to a row,
 * filtered by a search box and sorted by count, name or chest (with a divider before each chest). Nothing can be taken
 * out or put in. Drawn like Integrated UI's shulker box band: a vanilla-style window tinted towards the network's colour.
 */
public final class GolemNetworkScreen extends AbstractContainerScreen<GolemNetworkMenu> {
    private static final String KEY = "gui." + AlteredAether.MOD_ID + ".golem_network.";
    private static final ResourceLocation WINDOW = sprite("window");
    private static final ResourceLocation SLOT = sprite("slot");
    private static final int COLUMNS = 9;
    private static final int TILE = 18;
    private static final int WIDTH = 182;
    private static final int GRID_X = 8;
    private static final int TOP_ROW_Y = 18;
    private static final int BUTTON = 12;
    private static final int GRID_GAP = 4;
    /** Picks shown in a row of the selection bar; the clear button takes the last column. */
    private static final int BAR_COLUMNS = 8;
    /** The engraved line between the selection bar and the grid. */
    private static final int SEPARATOR = 4;
    private static final int FLASH_TICKS = 6;
    private static final int BOTTOM = 7;
    private static final int SCROLLBAR_X = GRID_X + COLUMNS * TILE + 3;
    private static final int SCROLLBAR_WIDTH = 4;
    /** The space the screen leaves above and below it. */
    private static final int MARGIN = 20;

    /** One line of the grid: a row of tiles, or (sorted by chest) the divider naming the chest below it. */
    private record Line(@Nullable Component divider, List<Tile> tiles) {
    }

    /** A tile: an item kind, and how many to show (all of it, or what one chest holds). */
    record Tile(GolemNetworkView.Entry entry, long count, int chest) {
    }

    private EditBox search;
    private Button sortButton;
    /** Rows of the window below the search box, shared by the selection bar and the grid. */
    private int maxRows;
    /** Rows the grid shows, and where it starts; the selection bar takes rows above it while anything is picked. */
    private int rows;
    private int gridY;
    private int barRows;
    private Button clearButton;
    /** A tile flashed red: an item that couldn't be picked, every colour being taken. */
    @Nullable
    private ItemStack flashed;
    private int flashTicks;
    private int scroll;
    private boolean draggingScrollbar;
    private List<Line> lines = List.of();
    private int listedVersion = -1;
    private String listedFilter = "";
    @Nullable
    private NetworkSort listedSort;
    private Style style;

    public GolemNetworkScreen(GolemNetworkMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        style = Style.of(menu.color().getTextureDiffuseColor());
    }

    private static ResourceLocation sprite(String name) {
        return AlteredAether.id("golem_network/" + name);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Layout

    @Override
    protected void init() {
        int fit = (height - MARGIN * 2 - barY() - SEPARATOR - BOTTOM) / TILE;
        maxRows = Math.max(4, Math.min(AAClientConfig.GOLEM_NETWORK_ROWS.get(), fit));
        imageWidth = WIDTH;
        imageHeight = barY() + maxRows * TILE + SEPARATOR + BOTTOM;
        super.init();
        clearButton = addRenderableWidget(new SpriteButton(leftPos + GRID_X + BAR_COLUMNS * TILE + 3,
                topPos + barY() + (TILE - BUTTON) / 2, BUTTON, BUTTON, () -> sprite("clear"), button -> {
            menu.replacePicks(List.of());
            layoutBar();
        }));
        label(clearButton, Component.translatable(KEY + "clear"));
        layoutBar();
        String text = search != null ? search.getValue() : "";
        boolean focused = search != null && search.isFocused();
        int right = GRID_X + COLUMNS * TILE;
        sortButton = addRenderableWidget(new SpriteButton(leftPos + right - BUTTON, topPos + TOP_ROW_Y, BUTTON, BUTTON,
                () -> sprite(sortSprite(sort())), button -> cycleSort()));
        label(sortButton, sortLabel(sort()));
        int searchRight = right - BUTTON - 2;
        search = new EditBox(font, leftPos + GRID_X + 1, topPos + TOP_ROW_Y, searchRight - GRID_X - 3, BUTTON,
                Component.translatable(KEY + "search"));
        search.setMaxLength(100);
        search.setTextColor(0xFFFFFF);
        search.setHint(Component.translatable(KEY + "search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(text);
        search.setFocused(focused);
        addRenderableWidget(search);
    }

    private static int barY() {
        return TOP_ROW_Y + BUTTON + GRID_GAP;
    }

    /** Makes room for the selection bar above the grid while anything is picked: a row for every eight. */
    private void layoutBar() {
        int picks = menu.picks().size();
        barRows = picks == 0 ? 0 : (picks + BAR_COLUMNS - 1) / BAR_COLUMNS;
        gridY = barRows == 0 ? barY() : barY() + barRows * TILE + SEPARATOR;
        rows = barRows == 0 ? maxRows : maxRows - barRows;
        if (clearButton != null) {
            clearButton.visible = barRows > 0;
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private static void label(AbstractWidget widget, Component label) {
        widget.setTooltip(Tooltip.create(label));
        widget.setMessage(label);
    }

    private static NetworkSort sort() {
        return AAClientConfig.GOLEM_NETWORK_SORT.get();
    }

    private void cycleSort() {
        NetworkSort next = sort().next();
        AAClientConfig.GOLEM_NETWORK_SORT.set(next);
        AAClientConfig.GOLEM_NETWORK_SORT.save();
        label(sortButton, sortLabel(next));
        scroll = 0;
    }

    private static String sortSprite(NetworkSort sort) {
        return switch (sort) {
            case COUNT_DOWN -> "sort_count_down";
            case COUNT_UP -> "sort_count_up";
            case NAME -> "sort_name";
            case NAME_REVERSED -> "sort_name_reversed";
            case CHEST -> "sort_chest";
        };
    }

    private static Component sortLabel(NetworkSort sort) {
        return Component.translatable(KEY + "sort", Component.translatable(KEY + "sort." + sort.name().toLowerCase(Locale.ROOT)));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The list

    /** Lists the contents again if they, the search or the sort changed. */
    private void relist() {
        String filter = search.getValue();
        NetworkSort sort = sort();
        if (listedVersion == menu.received() && filter.equals(listedFilter) && sort == listedSort) {
            return;
        }
        listedVersion = menu.received();
        listedFilter = filter;
        listedSort = sort;
        style = Style.of(menu.color().getTextureDiffuseColor());
        lines = arrange(menu.contents(), filter(), sort);
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    /** What the search lets through. */
    private Predicate<ItemStack> filter() {
        return ItemSearch.parse(search.getValue());
    }

    private static List<Line> arrange(GolemNetworkView.Contents contents, Predicate<ItemStack> filter, NetworkSort sort) {
        List<GolemNetworkView.Entry> shown = new ArrayList<>();
        for (GolemNetworkView.Entry entry : contents.entries()) {
            if (filter.test(entry.item())) {
                shown.add(entry);
            }
        }
        Comparator<GolemNetworkView.Entry> byName = Comparator.comparing(entry -> name(entry.item()));
        Comparator<GolemNetworkView.Entry> byCount = Comparator.comparingLong(GolemNetworkView.Entry::total);
        List<Line> lines = new ArrayList<>();
        if (sort != NetworkSort.CHEST) {
            shown.sort(switch (sort) {
                case COUNT_DOWN -> byCount.reversed().thenComparing(byName);
                case COUNT_UP -> byCount.thenComparing(byName);
                case NAME -> byName.thenComparing(byCount.reversed());
                default -> byName.reversed().thenComparing(byCount.reversed());
            });
            List<Tile> tiles = new ArrayList<>(shown.size());
            for (GolemNetworkView.Entry entry : shown) {
                tiles.add(new Tile(entry, entry.total(), -1));
            }
            addRows(lines, tiles);
            return lines;
        }
        List<List<Tile>> perChest = new ArrayList<>();
        for (int i = 0; i < contents.chests().size(); i++) {
            perChest.add(new ArrayList<>());
        }
        for (GolemNetworkView.Entry entry : shown) {
            for (int i = 0; i < entry.chests().length; i++) {
                int chest = entry.chests()[i];
                if (chest >= 0 && chest < perChest.size()) {
                    perChest.get(chest).add(new Tile(entry, entry.counts()[i], chest));
                }
            }
        }
        Comparator<Tile> tileOrder = Comparator.comparingLong(Tile::count).reversed()
                .thenComparing(tile -> name(tile.entry().item()));
        for (int i = 0; i < perChest.size(); i++) {
            List<Tile> tiles = perChest.get(i);
            if (tiles.isEmpty()) {
                continue;
            }
            tiles.sort(tileOrder);
            lines.add(new Line(chestLabel(contents.chests().get(i)), List.of()));
            addRows(lines, tiles);
        }
        return lines;
    }

    private static void addRows(List<Line> lines, List<Tile> tiles) {
        for (int i = 0; i < tiles.size(); i += COLUMNS) {
            lines.add(new Line(null, tiles.subList(i, Math.min(tiles.size(), i + COLUMNS))));
        }
    }

    private static String name(ItemStack stack) {
        return stack.getHoverName().getString().toLowerCase(Locale.ROOT);
    }

    static Component chestLabel(GolemNetworkView.Chest chest) {
        return Component.translatable(KEY + "chest", chest.name(), chest.pos().getX(), chest.pos().getY(),
                chest.pos().getZ());
    }

    private int maxScroll() {
        return Math.max(0, lines.size() - rows);
    }

    /** The tile under the mouse, if any. */
    @Nullable
    Tile tileAt(double mouseX, double mouseY) {
        int x = (int) Math.floor(mouseX) - leftPos - GRID_X;
        int y = (int) Math.floor(mouseY) - topPos - gridY;
        if (x < 0 || y < 0 || x >= COLUMNS * TILE || y >= rows * TILE) {
            return null;
        }
        int line = scroll + y / TILE;
        if (line >= lines.size()) {
            return null;
        }
        List<Tile> tiles = lines.get(line).tiles();
        int column = x / TILE;
        return column < tiles.size() ? tiles.get(column) : null;
    }

    /** The item under the mouse, for JEI's lookups. */
    @Nullable
    public ItemStack itemAt(double mouseX, double mouseY) {
        Tile tile = tileAt(mouseX, mouseY);
        return tile == null ? null : tile.entry().item();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        relist();
        layoutBar();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTiles(graphics, mouseX, mouseY);
        renderTileTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        style.blit(graphics, WINDOW, style.tint, leftPos, topPos, imageWidth, imageHeight);
        int left = leftPos + GRID_X;
        int top = topPos + gridY;
        for (int row = 0; row < rows && scroll + row < lines.size(); row++) {
            Line line = lines.get(scroll + row);
            int y = top + row * TILE;
            if (line.divider() == null) {
                style.blit(graphics, SLOT, style.slotTint, left, y, line.tiles().size() * TILE, TILE);
            }
        }
        for (int i = 0; i < menu.picks().size(); i++) {
            style.blit(graphics, SLOT, style.slotTint, left + i % BAR_COLUMNS * TILE,
                    topPos + barY() + i / BAR_COLUMNS * TILE, TILE, TILE);
        }
        if (barRows > 0) {
            int lineY = topPos + gridY - SEPARATOR / 2 - 1;
            graphics.fill(left, lineY, left + COLUMNS * TILE, lineY + 1, style.engraveDark);
            graphics.fill(left, lineY + 1, left + COLUMNS * TILE, lineY + 2, style.engraveLight);
        }
        renderScrollbar(graphics);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int x = leftPos + SCROLLBAR_X;
        int y = topPos + gridY;
        int height = rows * TILE;
        graphics.fill(x, y, x + SCROLLBAR_WIDTH, y + height, style.track);
        int total = Math.max(1, lines.size());
        int thumb = maxScroll() == 0 ? height : Math.max(8, height * rows / total);
        int offset = maxScroll() == 0 ? 0 : (height - thumb) * scroll / maxScroll();
        graphics.fill(x, y + offset, x + SCROLLBAR_WIDTH, y + offset + thumb, style.shadow);
        graphics.fill(x, y + offset, x + SCROLLBAR_WIDTH - 1, y + offset + thumb - 1, style.thumb);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, trimmed(font, title, imageWidth - GRID_X * 2), GRID_X, 6, style.text, false);
        if (lines.isEmpty()) {
            Component empty = Component.translatable(KEY + (menu.contents().entries().isEmpty() ? "empty" : "no_match"));
            graphics.drawString(font, empty, (imageWidth - font.width(empty)) / 2, gridY + TILE / 2 - 4, style.text, false);
        }
    }

    private void renderTiles(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = leftPos + GRID_X;
        int top = topPos + gridY;
        Tile hovered = tileAt(mouseX, mouseY);
        for (int row = 0; row < rows && scroll + row < lines.size(); row++) {
            Line line = lines.get(scroll + row);
            int y = top + row * TILE;
            if (line.divider() != null) {
                renderDivider(graphics, line.divider(), left, y);
                continue;
            }
            for (int column = 0; column < line.tiles().size(); column++) {
                Tile tile = line.tiles().get(column);
                int x = left + column * TILE;
                renderTile(graphics, tile.entry().item(), tile.count(), x, y, tile == hovered);
            }
        }
        List<GolemSelections.Pick> picks = menu.picks();
        int hoveredPick = pickAt(mouseX, mouseY);
        for (int i = 0; i < picks.size(); i++) {
            GolemSelections.Pick pick = picks.get(i);
            int x = left + i % BAR_COLUMNS * TILE;
            int y = topPos + barY() + i / BAR_COLUMNS * TILE;
            GolemNetworkView.Entry entry = entry(pick.item());
            renderTile(graphics, pick.item(), entry == null ? 0 : entry.total(), x, y, hoveredPick == i);
            if (entry == null) {
                // no longer stored anywhere: dimmed until it is again, or the pick is dropped
                graphics.fill(RenderType.guiOverlay(), x + 1, y + 1, x + 17, y + 17, 0x80303030);
            }
        }
    }

    /** One tile: the item, its count, its pick's colour round it, and the hover or refusal wash over it. */
    private void renderTile(GuiGraphics graphics, ItemStack stack, long count, int x, int y, boolean hovered) {
        graphics.renderItem(stack, x + 1, y + 1);
        graphics.renderItemDecorations(font, stack, x + 1, y + 1, "");
        if (count != 1 && count != 0) {
            renderCount(graphics, count(count), x + 1, y + 1);
        }
        GolemSelections.Pick pick = pickOf(stack);
        if (pick != null) {
            int color = 0xFF000000 | GolemGlow.color(pick.color().getTextureDiffuseColor());
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 250);
            graphics.renderOutline(x, y, TILE, TILE, color);
            graphics.renderOutline(x + 1, y + 1, TILE - 2, TILE - 2, color);
            graphics.pose().popPose();
        }
        if (flashed != null && flashTicks > 0 && ItemStack.isSameItemSameComponents(flashed, stack)) {
            graphics.fill(RenderType.guiOverlay(), x + 1, y + 1, x + 17, y + 17, 0x90FF3030);
        }
        if (hovered) {
            renderSlotHighlight(graphics, x + 1, y + 1, 0);
        }
    }

    @Nullable
    private GolemSelections.Pick pickOf(ItemStack stack) {
        for (GolemSelections.Pick pick : menu.picks()) {
            if (ItemStack.isSameItemSameComponents(pick.item(), stack)) {
                return pick;
            }
        }
        return null;
    }

    /** What the network holds of {@code stack}'s kind, or null if none. */
    @Nullable
    private GolemNetworkView.Entry entry(ItemStack stack) {
        for (GolemNetworkView.Entry entry : menu.contents().entries()) {
            if (ItemStack.isSameItemSameComponents(entry.item(), stack)) {
                return entry;
            }
        }
        return null;
    }

    /** The index of the pick in the selection bar under the mouse, or -1. */
    private int pickAt(double mouseX, double mouseY) {
        int x = (int) Math.floor(mouseX) - leftPos - GRID_X;
        int y = (int) Math.floor(mouseY) - topPos - barY();
        if (x < 0 || y < 0 || x >= BAR_COLUMNS * TILE || y >= barRows * TILE) {
            return -1;
        }
        int index = y / TILE * BAR_COLUMNS + x / TILE;
        return index < menu.picks().size() ? index : -1;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (flashTicks > 0) {
            flashTicks--;
        }
    }

    /** Picks {@code item} or drops it; with every colour taken, the tile flashes red and a dull click sounds. */
    private void toggle(ItemStack item) {
        if (menu.togglePick(item)) {
            layoutBar();
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.4F));
        } else {
            flashed = item;
            flashTicks = FLASH_TICKS;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 0.6F));
        }
    }

    /** A count in the slot's bottom right corner, small enough that four characters fit. */
    private void renderCount(GuiGraphics graphics, String text, int x, int y) {
        float scale = 0.75F;
        graphics.pose().pushPose();
        graphics.pose().translate(x + 16 - font.width(text) * scale, y + 16 - 7 * scale, 200);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, 0xFFFFFF, true);
        graphics.pose().popPose();
    }

    /** A chest's name on an engraved line across the grid. */
    private void renderDivider(GuiGraphics graphics, Component label, int left, int y) {
        int width = COLUMNS * TILE;
        int lineY = y + TILE / 2 + 1;
        FormattedCharSequence text = trimmed(font, label, width - 12);
        int textWidth = font.width(text);
        graphics.fill(left, lineY, left + 4, lineY + 1, style.engraveDark);
        graphics.fill(left, lineY + 1, left + 4, lineY + 2, style.engraveLight);
        int after = left + 6 + textWidth + 2;
        graphics.fill(after, lineY, left + width, lineY + 1, style.engraveDark);
        graphics.fill(after, lineY + 1, left + width, lineY + 2, style.engraveLight);
        graphics.drawString(font, text, left + 6, lineY - 3, style.text, false);
    }

    private void renderTileTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Tile tile = tileAt(mouseX, mouseY);
        int pick = pickAt(mouseX, mouseY);
        if (tile == null && pick >= 0) {
            ItemStack item = menu.picks().get(pick).item();
            GolemNetworkView.Entry entry = entry(item);
            if (entry == null) {
                List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(item));
                lines.add(Component.translatable(KEY + "none_stored").withStyle(ChatFormatting.GRAY));
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY, item);
                return;
            }
            tile = new Tile(entry, entry.total(), -1);
        }
        if (tile == null) {
            return;
        }
        ItemStack stack = tile.entry().item();
        List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(stack));
        NumberFormat format = NumberFormat.getIntegerInstance();
        if (tile.chest() >= 0) {
            lines.add(Component.translatable(KEY + "in_this_chest", format.format(tile.count())).withStyle(ChatFormatting.GRAY));
        }
        int chests = tile.entry().chests().length;
        lines.add(Component.translatable(KEY + (chests == 1 ? "total_one" : "total"), format.format(tile.entry().total()),
                chests).withStyle(ChatFormatting.GRAY));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY, stack);
    }

    /** A count as short as it reads: 64, 1.2k, 35k, 3.4M. */
    static String count(long count) {
        if (count < 1000) {
            return Long.toString(count);
        }
        String[] units = {"k", "M", "G", "T"};
        double value = count;
        int unit = -1;
        while (value >= 1000 && unit < units.length - 1) {
            value /= 1000;
            unit++;
        }
        return (value < 10 ? String.format(Locale.ROOT, "%.1f", Math.floor(value * 10) / 10)
                : Long.toString((long) value)) + units[unit];
    }

    private static FormattedCharSequence trimmed(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text.getVisualOrderText();
        }
        FormattedText cut = font.substrByWidth(text, maxWidth - font.width("..."));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("...")));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (overScrollbar(mouseX, mouseY) && maxScroll() > 0) {
            draggingScrollbar = true;
            scrollTo(mouseY);
            return true;
        }
        if (search.isMouseOver(mouseX, mouseY) && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            search.setValue("");
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            Tile tile = tileAt(mouseX, mouseY);
            int pick = pickAt(mouseX, mouseY);
            if (tile != null || pick >= 0) {
                search.setFocused(false);
                toggle(tile != null ? tile.entry().item() : menu.picks().get(pick).item());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && maxScroll() > 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        double x = mouseX - leftPos - SCROLLBAR_X;
        double y = mouseY - topPos - gridY;
        return x >= -1 && x < SCROLLBAR_WIDTH + 1 && y >= 0 && y < rows * TILE;
    }

    private void scrollTo(double mouseY) {
        double y = (mouseY - topPos - gridY) / (rows * TILE);
        scroll = Mth.clamp((int) Math.round(y * maxScroll()), 0, maxScroll());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                search.setFocused(false);
                return true;
            }
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return search.isFocused() ? search.charTyped(codePoint, modifiers) : super.charTyped(codePoint, modifiers);
    }

    /** The search box's text. */
    public String searchText() {
        return search == null ? "" : search.getValue();
    }

    /** Puts {@code text} in the search box. */
    public void setSearchText(String text) {
        if (search != null && !search.getValue().equals(text)) {
            search.setValue(text);
            scroll = 0;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Look

    /**
     * The screen's colours from the network's (RGB), as Integrated UI tints a shulker box's band: the window and the
     * slots lightened towards white so dark dyes stay readable, text dark or light to suit, and a scrollbar to match.
     */
    private record Style(int tint, int slotTint, int text, int track, int thumb, int shadow, int engraveDark,
                         int engraveLight) {
        /** The grey of the window sprite, which the tint multiplies. */
        private static final int WINDOW_GREY = 0xC6;

        static Style of(int color) {
            int tint = lighten(color & 0xFFFFFF, 0.45F);
            int slotTint = lighten(color & 0xFFFFFF, 0.6F);
            float luminance = (0.299F * (tint >> 16 & 0xFF) + 0.587F * (tint >> 8 & 0xFF) + 0.114F * (tint & 0xFF)) / 255.0F;
            int text = luminance * WINDOW_GREY / 255.0F > 0.45F ? 0x404040 : 0xF0F0F0;
            return new Style(tint, slotTint, text, 0xFF000000 | multiply(0x373737, tint),
                    0xFF000000 | multiply(0xC6C6C6, tint), 0xFF000000 | multiply(0x555555, tint),
                    0xFF000000 | multiply(0x555555, tint), 0xFF000000 | multiply(0xFFFFFF, tint));
        }

        /** Mixes {@code color} with white by {@code amount}. */
        private static int lighten(int color, float amount) {
            int r = (int) Mth.lerp(amount, color >> 16 & 0xFF, 255);
            int g = (int) Mth.lerp(amount, color >> 8 & 0xFF, 255);
            int b = (int) Mth.lerp(amount, color & 0xFF, 255);
            return r << 16 | g << 8 | b;
        }

        private static int multiply(int a, int b) {
            int r = (a >> 16 & 0xFF) * (b >> 16 & 0xFF) / 255;
            int g = (a >> 8 & 0xFF) * (b >> 8 & 0xFF) / 255;
            int bl = (a & 0xFF) * (b & 0xFF) / 255;
            return r << 16 | g << 8 | bl;
        }

        void blit(GuiGraphics graphics, ResourceLocation sprite, int tint, int x, int y, int width, int height) {
            graphics.setColor((tint >> 16 & 0xFF) / 255.0F, (tint >> 8 & 0xFF) / 255.0F, (tint & 0xFF) / 255.0F, 1.0F);
            graphics.blitSprite(sprite, x, y, width, height);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /** A small button drawn from a sprite that can change, with a white wash while hovered (as Integrated UI's). */
    static final class SpriteButton extends Button {
        private final Supplier<ResourceLocation> sprite;

        SpriteButton(int x, int y, int width, int height, Supplier<ResourceLocation> sprite, OnPress onPress) {
            super(x, y, width, height, CommonComponents.EMPTY, onPress, DEFAULT_NARRATION);
            this.sprite = sprite;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.blitSprite(sprite.get(), getX(), getY(), width, height);
            if (isHovered() && active) {
                graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x80FFFFFF);
            }
        }
    }
}
