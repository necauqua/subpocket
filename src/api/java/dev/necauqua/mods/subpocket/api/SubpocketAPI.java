/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket.api;

/**
 * A holder class for the {@link ISubpocketAPI} instance set by the mod when it's instance is created.
 **/
public final class SubpocketAPI {

    /**
     * Set at subpocket instance creation time.
     **/
    public static ISubpocketAPI instance = null;
}
