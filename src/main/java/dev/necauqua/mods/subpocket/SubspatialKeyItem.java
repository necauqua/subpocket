/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.common.IRarity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;

public final class SubspatialKeyItem extends Item {

    public SubspatialKeyItem() {
        setCreativeTab(CreativeTabs.MISC);
        setRegistryName(MODID, "key");
        setUnlocalizedName(MODID + ":key");
        setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand handIn) {
        if (world.isRemote || !CapabilitySubpocket.get(player).isUnlocked()) {
            return new ActionResult<>(EnumActionResult.PASS, player.getHeldItem(handIn));
        }
        player.openGui(Subpocket.instance, 0, player.world, 0, 0, 0);
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(handIn));
    }

    // so it triggers createEntity, which returns null, keeping the original entity
    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return true;
    }

    @Nullable
    @Override
    public Entity createEntity(World world, Entity location, ItemStack itemstack) {
        location.setEntityInvulnerable(true);
        ((EntityItem) location).age = -32768;
        return null;
    }

    @Override
    public boolean onEntityItemUpdate(EntityItem entityItem) {
        entityItem.extinguish();
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        return player != null && CapabilitySubpocket.get(player).isUnlocked();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        if (!hasEffect(stack)) {
            tooltip.addAll(Arrays.asList(I18n.format("item.subpocket:key.desc").split("\\\\n")));
        }
    }

    @Override
    public IRarity getForgeRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }
}
