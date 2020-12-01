/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import net.minecraftforge.common.config.Config.Comment;
import net.minecraftforge.common.config.Config.LangKey;
import net.minecraftforge.common.config.Config.Name;

@net.minecraftforge.common.config.Config(modid = Subpocket.MODID)
public class Config {

    @Comment("Block usage of ender chests when subpocket is active?")
    @Name("block_ender_chests")
    @LangKey("config.subpocket:block_ender_chests")
    public static boolean blockEnderChests = true;

    @Comment("Allow the subspatial key to break unbreakable blocks?")
    @Name("allow_breaking_unbreakable")
    @LangKey("config.subpocket:allow_breaking_unbreakable")
    public static boolean allowBreakingUnbreakableBlocks = true;

    @Comment("Disable the pixel picking of items? (alt-mode always on)")
    @Name("disable_pixel_picking")
    @LangKey("config.subpocket:disable_pixel_picking")
    public static boolean disablePixelPicking = false;
}
