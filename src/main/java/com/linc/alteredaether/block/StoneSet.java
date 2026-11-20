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

import com.aetherteam.aether.block.natural.AetherDoubleDropBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A stone with holystone's set of blocks, plus a polished form: the natural stone, which double drops like holystone,
 * then polished stone and bricks, each with stairs, a slab and a wall. Polished is crafted from the stone and bricks
 * from polished, as deepslate's are. A set may also have chiseled forms of its polished stone and bricks (see
 * {@link #withChiseled}). Models, loot, recipes and tags are in data/AAStoneProvider.
 */
public final class StoneSet {
    /** Every stone set, in creative-tab order. */
    public static final List<StoneSet> ALL = new ArrayList<>();

    /** One of the three forms, with its shapes: {@code base} is its full block's name, {@code shape} its shapes' stem. */
    public record Form(String base, String shape, DeferredBlock<? extends Block> block, DeferredBlock<StairBlock> stairs,
                       DeferredBlock<SlabBlock> slab, DeferredBlock<WallBlock> wall) {
        public List<DeferredBlock<? extends Block>> blocks() {
            return List.of(block, stairs, slab, wall);
        }
    }

    public final String name;
    public final Form natural;
    public final Form polished;
    public final Form bricks;
    /** Chiseled blocks, each made from {@code from}'s slabs as vanilla's chiseled stone bricks are; empty for most sets. */
    public final List<Chiseled> chiseled = new ArrayList<>();
    private final Supplier<BlockBehaviour.Properties> polishedProperties;
    private final Supplier<BlockBehaviour.Properties> bricksProperties;

    public record Chiseled(String base, Form from, DeferredBlock<Block> block) {
    }

    /**
     * Registers the set. {@code natural} is the stone as it generates; {@code cut} is for polished stone and bricks,
     * which, like holystone bricks, are harder than the stone they're made from.
     */
    public StoneSet(DeferredRegister.Blocks blocks, DeferredRegister.Items items, String name,
                    Supplier<BlockBehaviour.Properties> natural, Supplier<BlockBehaviour.Properties> polished,
                    Supplier<BlockBehaviour.Properties> bricks) {
        this.name = name;
        this.natural = form(blocks, items, name, name, AetherDoubleDropBlock::new, natural);
        this.polished = form(blocks, items, "polished_" + name, "polished_" + name, Block::new, polished);
        this.bricks = form(blocks, items, name + "_bricks", name + "_brick", Block::new, bricks);
        this.polishedProperties = polished;
        this.bricksProperties = bricks;
        ALL.add(this);
    }

    /** Adds chiseled stone (from polished slabs) and chiseled bricks (from brick slabs), as hard as what they're cut from. */
    public StoneSet withChiseled(DeferredRegister.Blocks blocks, DeferredRegister.Items items) {
        chiseled(blocks, items, "chiseled_" + name, polished, polishedProperties);
        chiseled(blocks, items, "chiseled_" + name + "_bricks", bricks, bricksProperties);
        return this;
    }

    private void chiseled(DeferredRegister.Blocks blocks, DeferredRegister.Items items, String base, Form from,
                          Supplier<BlockBehaviour.Properties> properties) {
        DeferredBlock<Block> block = blocks.register(base, () -> new Block(properties.get()));
        items.registerSimpleBlockItem(block);
        chiseled.add(new Chiseled(base, from, block));
    }

    public List<Form> forms() {
        return List.of(natural, polished, bricks);
    }

    /** Every block in creative-tab order: each form's block and shapes, then any chiseled block made from it. */
    public List<DeferredBlock<? extends Block>> blocks() {
        List<DeferredBlock<? extends Block>> all = new ArrayList<>();
        for (Form form : forms()) {
            all.addAll(form.blocks());
            for (Chiseled each : chiseled) {
                if (each.from() == form) {
                    all.add(each.block());
                }
            }
        }
        return all;
    }

    private static Form form(DeferredRegister.Blocks blocks, DeferredRegister.Items items, String base, String shape,
                             Function<BlockBehaviour.Properties, ? extends Block> factory,
                             Supplier<BlockBehaviour.Properties> properties) {
        DeferredBlock<? extends Block> block = blocks.register(base, () -> factory.apply(properties.get()));
        DeferredBlock<StairBlock> stairs = blocks.register(shape + "_stairs",
                () -> new StairBlock(block.get().defaultBlockState(), properties.get()));
        DeferredBlock<SlabBlock> slab = blocks.register(shape + "_slab", () -> new SlabBlock(properties.get()));
        DeferredBlock<WallBlock> wall = blocks.register(shape + "_wall",
                () -> new WallBlock(properties.get().forceSolidOn()));
        Form form = new Form(base, shape, block, stairs, slab, wall);
        for (DeferredBlock<? extends Block> each : form.blocks()) {
            items.registerSimpleBlockItem(each);
        }
        return form;
    }
}
