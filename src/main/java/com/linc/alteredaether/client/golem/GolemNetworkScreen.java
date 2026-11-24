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
import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The Golem Chest Network screen: everything a golem network's storage holds, as tiles with counts, nine to a row,
 * filtered by a search box and sorted by count, name or container (with a divider before each container). Nothing can
 * be taken out or put in. Picked items sit in a bar above the grid, framed in their colour. Drawn like Integrated UI's
 * shulker box band: a vanilla-style window tinted towards the network's colour. It is as tall as what it lists, up to
 * three quarters of the window.
 */
public final class GolemNetworkScreen extends AbstractContainerScreen<GolemNetworkMenu> {
    private static final String KEY = "gui." + AlteredAether.MOD_ID + ".golem_network.";
    private static final ResourceLocation WINDOW = sprite("window");
    private static final ResourceLocation SLOT = sprite("slot");
    private static final ResourceLocation BUTTON_BASE = sprite("button");
    private static final ResourceLocation PICK_FRAME = sprite("pick_frame");
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
    /** The most of the game window's height the screen takes. */
    private static final float MAX_SHARE = 0.75F;

    /** One line of the grid: a row of tiles, or (sorted by container) the divider naming the container below it. */
    private record Line(@Nullable GolemNetworkView.Chest divider, List<Tile> tiles) {
    }

    /** A tile: an item kind, and how many to show (all of it, or what one container holds). */
    record Tile(GolemNetworkView.Entry entry, long count, int chest) {
    }

    /** A widget kept at a place in the window as the window moves. */
    private record Placed(AbstractWidget widget, IntSupplier x, IntSupplier y) {
    }

    private final List<Placed> placed = new ArrayList<>();
    private EditBox search;
    private Button sortButton;
    private Button clearButton;
    /** Rows the grid shows, and where it starts; the selection bar takes rows above it while anything is picked. */
    private int rows = 1;
    private int gridY;
    private int barRows;
    /** The tallest the window has been since it opened, which keeps its top where it was as it shrinks. */
    private int tallest;
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
    private boolean listedSynced;
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
        imageWidth = WIDTH;
        tallest = 0;
        resize();
        super.init();
        placed.clear();
        String text = search != null ? search.getValue() : "";
        boolean focused = search != null && search.isFocused();
        int right = GRID_X + COLUMNS * TILE;
        clearButton = place(new SpriteButton(BUTTON, BUTTON, () -> sprite("clear"), () -> style, button -> {
            menu.replacePicks(List.of());
            resize();
        }), () -> GRID_X + BAR_COLUMNS * TILE + 3, () -> barY() + (TILE - BUTTON) / 2);
        label(clearButton, Component.translatable(KEY + "clear"));
        sortButton = place(new SpriteButton(BUTTON, BUTTON, () -> sprite(sortSprite(sort())), () -> style,
                button -> cycleSort()), () -> right - BUTTON, () -> TOP_ROW_Y);
        label(sortButton, sortLabel(sort()));
        int searchRight = right - BUTTON - 2;
        if (jei != null) {
            searchRight -= BUTTON + 2;
            int x = searchRight + 2;
            syncButton = place(new SpriteButton(BUTTON, BUTTON, () -> sprite(synced() ? "jei_sync" : "jei_sync_off"),
                    () -> style, button -> toggleSync()), () -> x, () -> TOP_ROW_Y);
            label(syncButton, syncLabel());
        }
        searchWidth = searchRight - GRID_X;
        search = place(new EditBox(font, 0, 0, searchWidth - 5, BUTTON - 2, Component.translatable(KEY + "search")),
                () -> GRID_X + 3, () -> TOP_ROW_Y + 2);
        search.setBordered(false);
        search.setMaxLength(100);
        search.setTextColor(0xFFFFFF);
        search.setHint(Component.translatable(KEY + "search").withStyle(ChatFormatting.GRAY));
        search.setValue(text);
        search.setFocused(focused);
        resize();
    }

    /** The search field's width, inset frame included. */
    private int searchWidth;

    private <T extends AbstractWidget> T place(T widget, IntSupplier x, IntSupplier y) {
        placed.add(new Placed(widget, x, y));
        widget.setPosition(leftPos + x.getAsInt(), topPos + y.getAsInt());
        return addRenderableWidget(widget);
    }

    private static int barY() {
        return TOP_ROW_Y + BUTTON + GRID_GAP;
    }

    /**
     * Sizes the window to what it shows: the selection bar's rows while anything is picked, then the grid's lines, up to
     * three quarters of the game window. Its top stays put as it shrinks (so the search box doesn't move under the
     * typing), and it is centred again when it grows past its tallest.
     */
    private void resize() {
        int picks = menu.picks().size();
        barRows = picks == 0 ? 0 : (picks + BAR_COLUMNS - 1) / BAR_COLUMNS;
        gridY = barRows == 0 ? barY() : barY() + barRows * TILE + SEPARATOR;
        int room = (int) (height * MAX_SHARE) - gridY - BOTTOM;
        rows = Mth.clamp(lines.size(), 1, Math.max(1, room / TILE));
        imageHeight = gridY + rows * TILE + BOTTOM;
        leftPos = (width - imageWidth) / 2;
        if (imageHeight > tallest) {
            tallest = imageHeight;
            topPos = (height - imageHeight) / 2;
        }
        for (Placed widget : placed) {
            widget.widget().setPosition(leftPos + widget.x().getAsInt(), topPos + widget.y().getAsInt());
        }
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
        return Component.translatable(KEY + "sort." + sort.name().toLowerCase(Locale.ROOT));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // JEI

    /**
     * JEI's search box, as the JEI plugin hands it over while JEI runs (null without JEI): its text, and the items it
     * lets through.
     */
    public interface JeiSearch {
        String text();

        void setText(String text);

        /** The items JEI's list shows for its current text. */
        Set<Item> shown();
    }

    @Nullable
    public static JeiSearch jei;
    @Nullable
    private Button syncButton;
    /** The texts last seen in each box while they follow each other, to tell which one changed. */
    private String syncedOurs = "", syncedJei = "";

    /** Whether the search box follows JEI's, and JEI's follows it. */
    private static boolean synced() {
        return jei != null && AAClientConfig.GOLEM_NETWORK_JEI_SYNC.get();
    }

    private static Component syncLabel() {
        return Component.translatable(KEY + (synced() ? "jei_sync" : "jei_sync_off"));
    }

    private void toggleSync() {
        AAClientConfig.GOLEM_NETWORK_JEI_SYNC.set(!AAClientConfig.GOLEM_NETWORK_JEI_SYNC.get());
        AAClientConfig.GOLEM_NETWORK_JEI_SYNC.save();
        if (syncButton != null) {
            label(syncButton, syncLabel());
        }
        if (synced() && jei != null) {
            // our text goes to JEI; with none, JEI's comes here
            if (search.getValue().isBlank()) {
                search.setValue(jei.text());
            } else {
                jei.setText(search.getValue());
            }
            syncedOurs = syncedJei = search.getValue();
        }
    }

    /** Keeps the two search boxes the same: whichever changed since last tick is copied to the other. */
    private void followJei() {
        if (!synced() || jei == null) {
            return;
        }
        String ours = search.getValue();
        String theirs = jei.text();
        if (!ours.equals(syncedOurs)) {
            jei.setText(ours);
            syncedOurs = syncedJei = ours;
        } else if (!theirs.equals(syncedJei)) {
            search.setValue(theirs);
            scroll = 0;
            syncedOurs = syncedJei = theirs;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The list

    /** Lists the contents again if they, the search or the sort changed. */
    private void relist() {
        String filter = search.getValue();
        NetworkSort sort = sort();
        boolean synced = synced();
        if (listedVersion == menu.received() && filter.equals(listedFilter) && sort == listedSort
                && synced == listedSynced) {
            return;
        }
        listedVersion = menu.received();
        listedFilter = filter;
        listedSort = sort;
        listedSynced = synced;
        Style restyled = Style.of(menu.color().getTextureDiffuseColor());
        if (!restyled.equals(style)) {
            style = restyled;
            search.setHint(Component.translatable(KEY + "search").withStyle(ChatFormatting.GRAY));
        }
        lines = arrange(menu.contents(), filter(), sort);
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    /**
     * What the search lets through: following JEI, what JEI's list shows for the same text (all its syntax, as JEI
     * reads it); otherwise our own reading of that syntax.
     */
    private Predicate<ItemStack> filter() {
        String text = search.getValue();
        if (synced() && jei != null && !text.isBlank() && text.equals(jei.text())) {
            Set<Item> shown = jei.shown();
            return stack -> shown.contains(stack.getItem());
        }
        return ItemSearch.parse(text);
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
            lines.add(new Line(contents.chests().get(i), List.of()));
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

    /** What a container is kept for, as its divider says it: its family's last word, the item pinned to it, or its name. */
    private static Component keptFor(GolemNetworkView.Chest chest) {
        if (!chest.family().isEmpty()) {
            List<String> words = familyWords(chest.family());
            return words.isEmpty() ? Component.translatable(KEY + "odds_and_ends") : Component.literal(words.getLast());
        }
        return chest.pinned().isEmpty() ? chest.name() : Component.literal(chest.pinned());
    }

    /** A family's saved path as words, as Jade shows it: {@code overworld/blocks/wood/spruce} reads Overworld, Blocks... */
    private static List<String> familyWords(String path) {
        List<String> words = new ArrayList<>();
        if (path.equals("misc")) {
            return words;
        }
        for (String part : path.split("/")) {
            String spaced = part.replace('_', ' ').replace('.', ' ');
            words.add(spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1));
        }
        return words;
    }

    /** A divider's tooltip: the container's name, what it is kept for in full, and where it is. */
    private static List<Component> dividerTooltip(GolemNetworkView.Chest chest) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(chest.name());
        if (!chest.family().isEmpty()) {
            List<String> words = familyWords(chest.family());
            tooltip.add((words.isEmpty() ? Component.translatable(KEY + "odds_and_ends")
                    : Component.literal(String.join(" › ", words))).withStyle(ChatFormatting.GRAY));
        } else if (!chest.pinned().isEmpty()) {
            tooltip.add(Component.translatable(KEY + "pinned", chest.pinned()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(KEY + "at", chest.pos().getX(), chest.pos().getY(), chest.pos().getZ())
                .withStyle(ChatFormatting.DARK_GRAY));
        return tooltip;
    }

    private int maxScroll() {
        return Math.max(0, lines.size() - rows);
    }

    /** The grid line under the mouse, or -1. */
    private int lineAt(double mouseX, double mouseY) {
        int x = (int) Math.floor(mouseX) - leftPos - GRID_X;
        int y = (int) Math.floor(mouseY) - topPos - gridY;
        if (x < 0 || y < 0 || x >= COLUMNS * TILE || y >= rows * TILE) {
            return -1;
        }
        int line = scroll + y / TILE;
        return line < lines.size() ? line : -1;
    }

    /** The tile under the mouse, if any. */
    @Nullable
    Tile tileAt(double mouseX, double mouseY) {
        int line = lineAt(mouseX, mouseY);
        if (line < 0) {
            return null;
        }
        List<Tile> tiles = lines.get(line).tiles();
        int column = ((int) Math.floor(mouseX) - leftPos - GRID_X) / TILE;
        return column < tiles.size() ? tiles.get(column) : null;
    }

    /** The item under the mouse, for JEI's lookups. */
    @Nullable
    public ItemStack itemAt(double mouseX, double mouseY) {
        Tile tile = tileAt(mouseX, mouseY);
        if (tile != null) {
            return tile.entry().item();
        }
        int pick = pickAt(mouseX, mouseY);
        return pick >= 0 ? menu.picks().get(pick).item() : null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String asked = menu.takeAskedSearch();
        if (asked != null) {
            setSearchText(asked);
        }
        relist();
        resize();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTiles(graphics, mouseX, mouseY);
        renderHoverTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        style.blit(graphics, WINDOW, style.tint, leftPos, topPos, imageWidth, imageHeight);
        renderSearchField(graphics);
        int left = leftPos + GRID_X;
        int top = topPos + gridY;
        for (int row = 0; row < rows && scroll + row < lines.size(); row++) {
            Line line = lines.get(scroll + row);
            if (line.divider() == null) {
                style.blit(graphics, SLOT, style.slotTint, left, top + row * TILE, line.tiles().size() * TILE, TILE);
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

    /** The search box sunk into the window, as the creative tab's and Quark's filter are: shaded, in the window's tint. */
    private void renderSearchField(GuiGraphics graphics) {
        int x = leftPos + GRID_X, y = topPos + TOP_ROW_Y;
        int x1 = x + searchWidth, y1 = y + BUTTON;
        graphics.fill(x, y, x1, y1, style.field);
        graphics.fill(x, y, x1 - 1, y + 1, style.engraveDark);
        graphics.fill(x, y, x + 1, y1 - 1, style.engraveDark);
        graphics.fill(x + 1, y1 - 1, x1, y1, style.engraveLight);
        graphics.fill(x1 - 1, y + 1, x1, y1, style.engraveLight);
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
                renderTile(graphics, tile.entry().item(), tile.count(), left + column * TILE, y, tile == hovered);
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

    /**
     * One tile: its pick's colour behind it (a soft frame, bright at the edge and faint in the middle, beating with the
     * picked items' glow), then the item, its count, and the hover or refusal wash over it.
     */
    private void renderTile(GuiGraphics graphics, ItemStack stack, long count, int x, int y, boolean hovered) {
        GolemSelections.Pick pick = pickOf(stack);
        if (pick != null) {
            int color = GolemGlow.color(pick.color().getTextureDiffuseColor());
            float alpha = 0.65F + 0.35F * GolemGlow.pulse();
            RenderSystem.enableBlend();
            graphics.setColor((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, alpha);
            graphics.blitSprite(PICK_FRAME, x, y, TILE, TILE);
            graphics.setColor(1, 1, 1, 1);
            RenderSystem.disableBlend();
        }
        graphics.renderItem(stack, x + 1, y + 1);
        graphics.renderItemDecorations(font, stack, x + 1, y + 1, "");
        if (count != 1 && count != 0) {
            renderCount(graphics, count(count), x + 1, y + 1);
        }
        if (flashed != null && flashTicks > 0 && ItemStack.isSameItemSameComponents(flashed, stack)) {
            graphics.fill(RenderType.guiOverlay(), x + 1, y + 1, x + 17, y + 17, 0x90FF3030);
        }
        if (hovered) {
            renderSlotHighlight(graphics, x + 1, y + 1, 0);
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

    /** A container's divider: its block, what it is kept for, and an engraved line across the rest of the grid. */
    private void renderDivider(GuiGraphics graphics, GolemNetworkView.Chest chest, int left, int y) {
        int width = COLUMNS * TILE;
        graphics.renderItem(chest.icon(), left + 1, y + 1);
        int textX = left + TILE + 3;
        FormattedCharSequence text = trimmed(font, keptFor(chest), width - TILE - 12);
        int lineY = y + TILE / 2;
        graphics.drawString(font, text, textX, lineY - 4, style.text, false);
        int after = textX + font.width(text) + 4;
        if (after < left + width) {
            graphics.fill(after, lineY, left + width, lineY + 1, style.engraveDark);
            graphics.fill(after, lineY + 1, left + width, lineY + 2, style.engraveLight);
        }
    }

    private void renderHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int line = lineAt(mouseX, mouseY);
        if (line >= 0 && lines.get(line).divider() != null) {
            graphics.renderComponentTooltip(font, dividerTooltip(lines.get(line).divider()), mouseX, mouseY);
            return;
        }
        Tile tile = tileAt(mouseX, mouseY);
        int pick = pickAt(mouseX, mouseY);
        if (tile == null && pick >= 0) {
            ItemStack item = menu.picks().get(pick).item();
            GolemNetworkView.Entry entry = entry(item);
            if (entry == null) {
                List<Component> tooltip = new ArrayList<>(getTooltipFromContainerItem(item));
                tooltip.add(Component.translatable(KEY + "none_stored").withStyle(ChatFormatting.GRAY));
                graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY, item);
                return;
            }
            tile = new Tile(entry, entry.total(), -1);
        }
        if (tile == null) {
            return;
        }
        ItemStack stack = tile.entry().item();
        List<Component> tooltip = new ArrayList<>(getTooltipFromContainerItem(stack));
        NumberFormat format = NumberFormat.getIntegerInstance();
        if (tile.chest() >= 0) {
            tooltip.add(Component.translatable(KEY + "in_this_chest", format.format(tile.count())).withStyle(ChatFormatting.GRAY));
        }
        int chests = tile.entry().chests().length;
        tooltip.add(Component.translatable(KEY + (chests == 1 ? "total_one" : "total"), format.format(tile.entry().total()),
                chests).withStyle(ChatFormatting.GRAY));
        graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY, stack);
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
    protected void containerTick() {
        super.containerTick();
        followJei();
        if (flashTicks > 0) {
            flashTicks--;
        }
    }

    /** Picks {@code item} or drops it; with every colour taken, the tile flashes red and a dull click sounds. */
    private void toggle(ItemStack item) {
        if (menu.togglePick(item)) {
            resize();
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.4F));
        } else {
            flashed = item;
            flashTicks = FLASH_TICKS;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 0.6F));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (overScrollbar(mouseX, mouseY) && maxScroll() > 0) {
            draggingScrollbar = true;
            scrollTo(mouseY);
            return true;
        }
        boolean overSearch = mouseX >= leftPos + GRID_X && mouseX < leftPos + GRID_X + searchWidth
                && mouseY >= topPos + TOP_ROW_Y && mouseY < topPos + TOP_ROW_Y + BUTTON;
        if (overSearch) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                search.setValue("");
            }
            setFocused(search);
            search.setFocused(true);
            return true;
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
     * The screen's colours from the network's (RGB), as Integrated UI tints a shulker box's band but quieter: the window
     * and slots take the colour mixed well into a mid grey, so it reads as a hint rather than a wash; text is dark or
     * light to suit, and the scrollbar, lines, search field and buttons match.
     */
    private record Style(int tint, int slotTint, int text, int track, int thumb, int shadow, int engraveDark,
                         int engraveLight, int field) {
        /** The grey of the window sprite, which the tint multiplies. */
        private static final int WINDOW_GREY = 0xC6;

        static Style of(int color) {
            int tint = mix(color & 0xFFFFFF, 0xB4B4B4, 0.62F);
            int slotTint = mix(color & 0xFFFFFF, 0xC8C8C8, 0.55F);
            float luminance = (0.299F * (tint >> 16 & 0xFF) + 0.587F * (tint >> 8 & 0xFF) + 0.114F * (tint & 0xFF)) / 255.0F;
            int text = luminance * WINDOW_GREY / 255.0F > 0.4F ? 0x404040 : 0xF0F0F0;
            return new Style(tint, slotTint, text, opaque(multiply(0x373737, tint)), opaque(multiply(0xC6C6C6, tint)),
                    opaque(multiply(0x555555, tint)), opaque(multiply(0x555555, tint)), opaque(multiply(0xFFFFFF, tint)),
                    opaque(multiply(0x6E6E6E, tint)));
        }

        private static int opaque(int rgb) {
            return 0xFF000000 | rgb;
        }

        /** {@code color} mixed into {@code grey} by {@code amount} of the grey. */
        private static int mix(int color, int grey, float amount) {
            int r = (int) Mth.lerp(amount, color >> 16 & 0xFF, grey >> 16 & 0xFF);
            int g = (int) Mth.lerp(amount, color >> 8 & 0xFF, grey >> 8 & 0xFF);
            int b = (int) Mth.lerp(amount, color & 0xFF, grey & 0xFF);
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

    /**
     * A small button: a base in the window's tint with an icon on it that can change, and a white wash while hovered
     * (as Integrated UI's).
     */
    static final class SpriteButton extends Button {
        private final Supplier<ResourceLocation> icon;
        private final Supplier<Style> style;

        SpriteButton(int width, int height, Supplier<ResourceLocation> icon, Supplier<Style> style, OnPress onPress) {
            super(0, 0, width, height, CommonComponents.EMPTY, onPress, DEFAULT_NARRATION);
            this.icon = icon;
            this.style = style;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            style.get().blit(graphics, BUTTON_BASE, style.get().slotTint, getX(), getY(), width, height);
            RenderSystem.enableBlend();
            graphics.blitSprite(icon.get(), getX(), getY(), width, height);
            RenderSystem.disableBlend();
            if (isHovered() && active) {
                graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x80FFFFFF);
            }
        }
    }
}
