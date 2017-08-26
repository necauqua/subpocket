/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket;

import net.minecraftforge.common.config.Config.Comment;
import net.minecraftforge.common.config.Config.LangKey;
import net.minecraftforge.common.config.Config.Name;
import net.minecraftforge.common.config.Config.RangeInt;

@net.minecraftforge.common.config.Config(modid = Subpocket.MODID)
public class Config {

    @Comment("Block usage of ender chests when subpocket is active?")
    @Name("block_ender_chests")
    @LangKey("subpocket.config.block_ender_chests")
    public static boolean blockEnderChests = true;

    @Comment("How many real hours player should be alive to start the task for acquiring the subpocket")
    @Name("hours_to_live")
    @LangKey("subpocket.config.hours_to_live")
    @RangeInt(min = 0)
    public static int hoursToLive = 3;

    @Comment("If false then hardcore mechanic is used - only take half your health of damage from dragon")
    @Name("need_death_by_dragon")
    @LangKey("subpocket.config.need_death_by_dragon")
    public static boolean needDeathByDragon = true;
}
