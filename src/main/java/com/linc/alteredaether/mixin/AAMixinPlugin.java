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

import com.bawnorton.mixinsquared.canceller.MixinCancellerRegistrar;
import com.linc.alteredaether.entity.AerwhaleMode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Chooses between our aerwhales and Deep Aether's (see {@link AerwhaleMode}). With ours in play, MixinSquared cancels
 * every Deep Aether mixin that patches the Aether's aerwhale, its model or its renderer, so neither its saddle, chest,
 * seats, flight nor saddle layers exist; {@link DeepAetherWhaleCargoMixin} then salvages what its whales had saved.
 */
public final class AAMixinPlugin implements IMixinConfigPlugin {
    private static final String DEEP_AETHER_MIXINS = "io.github.razordevs.deep_aether.mixin.entity.";
    private static final Set<String> DEEP_AETHER_WHALE_MIXINS = Set.of(
            DEEP_AETHER_MIXINS + "AerwhaleMixin",
            DEEP_AETHER_MIXINS + "AerwhaleModelMixin",
            DEEP_AETHER_MIXINS + "ClassicAerwhaleModelMixin",
            DEEP_AETHER_MIXINS + "AerwhaleRendererMixin");

    @Override
    public void onLoad(String mixinPackage) {
        if (AerwhaleMode.deepAetherCancelled()) {
            MixinCancellerRegistrar.register((targets, mixin) -> DEEP_AETHER_WHALE_MIXINS.contains(mixin));
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".DeepAetherWhaleCargoMixin")) {
            return AerwhaleMode.deepAetherCancelled();
        }
        return true;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
