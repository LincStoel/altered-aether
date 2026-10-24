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
package com.linc.alteredaether.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** Density function JSON builders. Arguments are density functions as JSON: an inline object, a reference or a constant. */
final class Df {
    static JsonElement ref(String id) {
        return new JsonPrimitive(id);
    }

    static JsonElement constant(double value) {
        return new JsonPrimitive(value);
    }

    static JsonObject add(Object a, Object b) {
        return binary("minecraft:add", a, b);
    }

    static JsonObject mul(Object a, Object b) {
        return binary("minecraft:mul", a, b);
    }

    static JsonObject min(Object a, Object b) {
        return binary("minecraft:min", a, b);
    }

    static JsonObject max(Object a, Object b) {
        return binary("minecraft:max", a, b);
    }

    /** {@code (1 - mask) * low + mask * high}. There is no lerp or subtract function, so the complement is spelled out. */
    static JsonObject lerp(Object mask, Object low, Object high) {
        return add(mul(add(1.0, mul(-1.0, mask)), low), mul(mask, high));
    }

    static JsonObject abs(Object argument) {
        return wrap("minecraft:abs", argument);
    }

    static JsonObject square(Object argument) {
        return wrap("minecraft:square", argument);
    }

    /** The block's own Y coordinate, across the whole build height. */
    static JsonObject y() {
        return yGradient(-2048, -2048.0, 2048, 2048.0);
    }

    /** {@code (1 - |noise|)^2}: sharp crests along the noise's zero contour, the shape of a mountain ridge. */
    static JsonObject ridged(Object noise) {
        return square(add(1.0, mul(-1.0, abs(noise))));
    }

    /** A value from the common config (see ConfigValue). */
    static JsonObject config(String key) {
        JsonObject json = type("altered_aether:config");
        json.addProperty("key", key);
        return json;
    }

    /**
     * {@link #lerp}, but evaluating only the side that matters where {@code mask} is exactly 0 or 1. A plain lerp
     * computes both sides everywhere, so an expensive landform would cost its full price across the whole world
     * even where its weight is 0. The mask must stay within [0, 1].
     */
    static JsonObject choose(Object mask, Object low, Object high) {
        JsonObject mix = rangeChoice(mask, 0.9999, 2.0, high, lerp(mask, low, high));
        return rangeChoice(mask, -1.0, 0.0001, low, mix);
    }

    static JsonObject rangeChoice(Object input, double minInclusive, double maxExclusive, Object inRange,
                                  Object outOfRange) {
        JsonObject json = type("minecraft:range_choice");
        json.add("input", of(input));
        json.addProperty("min_inclusive", minInclusive);
        json.addProperty("max_exclusive", maxExclusive);
        json.add("when_in_range", of(inRange));
        json.add("when_out_of_range", of(outOfRange));
        return json;
    }

    static JsonObject clamp(Object input, double min, double max) {
        JsonObject json = type("minecraft:clamp");
        json.addProperty("min", min);
        json.addProperty("max", max);
        json.add("input", of(input));
        return json;
    }

    /** {@code clamp(0, 1, offset + scale * input)}: a linear ramp from 0 to 1. */
    static JsonObject ramp(Object input, double offset, double scale) {
        return clamp(add(offset, mul(scale, input)), 0, 1);
    }

    static JsonObject flatCache(Object argument) {
        return wrap("minecraft:flat_cache", argument);
    }

    static JsonObject wrap(String type, Object argument) {
        JsonObject json = type(type);
        json.add("argument", of(argument));
        return json;
    }

    static JsonObject shiftedNoise(String noise, double xzScale, double yScale) {
        return shiftedNoise(noise, ref("minecraft:shift_x"), xzScale, yScale);
    }

    static JsonObject shiftedNoise(String noise, Object shiftX, double xzScale, double yScale) {
        JsonObject json = type("minecraft:shifted_noise");
        json.addProperty("noise", noise);
        json.add("shift_x", of(shiftX));
        json.add("shift_z", ref("minecraft:shift_z"));
        json.addProperty("shift_y", 0.0);
        json.addProperty("xz_scale", xzScale);
        json.addProperty("y_scale", yScale);
        return json;
    }

    static JsonObject yGradient(int fromY, double fromValue, int toY, double toValue) {
        JsonObject json = type("minecraft:y_clamped_gradient");
        json.addProperty("from_y", fromY);
        json.addProperty("to_y", toY);
        json.addProperty("from_value", fromValue);
        json.addProperty("to_value", toValue);
        return json;
    }

    static JsonObject oldBlendedNoise(double xzScale, double yScale, double xzFactor, double yFactor, double smear) {
        JsonObject json = type("minecraft:old_blended_noise");
        json.addProperty("xz_scale", xzScale);
        json.addProperty("y_scale", yScale);
        json.addProperty("xz_factor", xzFactor);
        json.addProperty("y_factor", yFactor);
        json.addProperty("smear_scale_multiplier", smear);
        return json;
    }

    static JsonObject type(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        return json;
    }

    private static JsonObject binary(String type, Object a, Object b) {
        JsonObject json = type(type);
        json.add("argument1", of(a));
        json.add("argument2", of(b));
        return json;
    }

    /** Accepts JSON, a number, or a string reference. */
    static JsonElement of(Object value) {
        if (value instanceof JsonElement element) {
            return element.deepCopy();
        }
        if (value instanceof Number number) {
            return new JsonPrimitive(number.doubleValue());
        }
        if (value instanceof String id) {
            return new JsonPrimitive(id);
        }
        throw new IllegalArgumentException("Not a density function: " + value);
    }

    private Df() {
    }
}
