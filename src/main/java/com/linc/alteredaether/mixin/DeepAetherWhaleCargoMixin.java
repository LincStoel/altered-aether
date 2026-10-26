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
package com.linc.alteredaether.mixin;

import com.aetherteam.aether.entity.passive.Aerwhale;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * With Deep Aether loaded but its aerwhales switched off for ours (see {@link AAMixinPlugin}), an aerwhale saved while
 * Deep Aether's were in play would silently lose its saddle and the 27 slots of its chest. This reads them back from
 * Deep Aether's save keys and drops them where the whale is on its first tick, when the world can take new entities.
 */
@Mixin(Aerwhale.class)
public abstract class DeepAetherWhaleCargoMixin extends FlyingMob {
    @Unique
    private static final ResourceLocation altered_aether$SADDLE =
            ResourceLocation.fromNamespaceAndPath("deep_aether", "aerwhale_saddle");

    @Unique
    private List<ItemStack> altered_aether$cargo = List.of();

    private DeepAetherWhaleCargoMixin(EntityType<? extends FlyingMob> type, Level level) {
        super(type, level);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        List<ItemStack> cargo = new ArrayList<>();
        if (tag.contains("Items")) {
            NonNullList<ItemStack> chest = NonNullList.withSize(27, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag, chest, registryAccess());
            chest.stream().filter(stack -> !stack.isEmpty()).forEach(cargo::add);
        }
        if (tag.getBoolean("isSaddled")) {
            cargo.add(new ItemStack(BuiltInRegistries.ITEM.get(altered_aether$SADDLE)));
        }
        altered_aether$cargo = cargo;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void altered_aether$dropCargo(CallbackInfo ci) {
        if (!altered_aether$cargo.isEmpty() && !level().isClientSide) {
            altered_aether$cargo.forEach(this::spawnAtLocation);
            altered_aether$cargo = List.of();
        }
    }
}
