/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket.handlers;

import dev.necauqua.mods.subpocket.CapabilitySubpocket;
import dev.necauqua.mods.subpocket.Config;
import dev.necauqua.mods.subpocket.api.ISubpocket;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.Name;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.SortingIndex;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;

import java.util.Map;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.Subpocket.SUBSPATIAL_KEY;
import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.Opcodes.*;

@EventBusSubscriber(modid = MODID)
public final class KeyBlockBreakingHandler {

    @SubscribeEvent
    public static void on(PlayerEvent.BreakSpeed e) {
        EntityPlayer player = e.getEntityPlayer();
        if (e.getState().getBlock() == Blocks.ENDER_CHEST
                && player.getHeldItemMainhand().getItem() == SUBSPATIAL_KEY
                && player.world.getTotalWorldTime() % 20 == 0
                && player.dimension == DimensionType.THE_END.getId()
                && !CapabilitySubpocket.get(player).isUnlocked()) {
            player.attackEntityFrom(DamageSource.OUT_OF_WORLD, 1.0F);
        }
    }

    @SubscribeEvent
    public static void on(BlockEvent.BreakEvent e) {
        EntityPlayer player = e.getPlayer();
        if (player.dimension != DimensionType.THE_END.getId()
                || e.getState().getBlock() != Blocks.ENDER_CHEST
                || player.getHeldItemMainhand().getItem() != SUBSPATIAL_KEY) {
            return;
        }
        ISubpocket storage = CapabilitySubpocket.get(player);
        if (storage.isUnlocked()) {
            return;
        }
        storage.unlock();
        World world = player.world;
        if (!world.isRemote) {
            BlockPos p = e.getPos();
            EntityLightningBolt lightning = new EntityLightningBolt(world, p.getX(), p.getY(), p.getZ(), false);
            world.spawnEntity(lightning);
        }
    }

    @SubscribeEvent
    public static void on(BlockEvent.HarvestDropsEvent e) {
        EntityPlayer player = e.getHarvester();
        if (player != null && player.getHeldItemMainhand().getItem() == SUBSPATIAL_KEY) {
            e.getDrops().clear();
        }
    }

    @SuppressWarnings("unused") // called from ASM
    public static boolean forceDefaultSpeed(IBlockState state, EntityPlayer player, World world, BlockPos pos) {
        float hardness = state.getBlockHardness(world, pos);
        return (hardness >= 0.0F || Config.allowBreakingUnbreakableBlocks)
                && (state.getBlock() != Blocks.ENDER_CHEST
                                || player.dimension != DimensionType.THE_END.getId()
                                || CapabilitySubpocket.get(player).isUnlocked())
                && player.getHeldItemMainhand().getItem() == SUBSPATIAL_KEY;
    }

    @SortingIndex(1001)
    @Name("SubpocketCore")
    public static class ForgeHooksTransformer implements IFMLLoadingPlugin, IClassTransformer {

        @Override
        public byte[] transform(String name, String transformedName, byte[] basicClass) {
            if (!"net.minecraftforge.common.ForgeHooks".equals(transformedName)) {
                return basicClass;
            }
            ClassNode classNode = new ClassNode();
            new ClassReader(basicClass).accept(classNode, EXPAND_FRAMES);

            classNode.methods.stream()
                    .filter(m -> m.name.equals("blockStrength"))
                    .findFirst()
                    .ifPresent(m -> {
                        InsnList injection = new InsnList();
                        injection.add(new VarInsnNode(ALOAD, 0));
                        injection.add(new VarInsnNode(ALOAD, 1));
                        injection.add(new VarInsnNode(ALOAD, 2));
                        injection.add(new VarInsnNode(ALOAD, 3));
                        injection.add(new MethodInsnNode(INVOKESTATIC,
                                "dev/necauqua/mods/subpocket/handlers/KeyBlockBreakingHandler",
                                "forceDefaultSpeed",
                                "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)Z",
                                false));
                        LabelNode skip = new LabelNode();
                        injection.add(new JumpInsnNode(IFEQ, skip));
                        injection.add(new LdcInsnNode(1.0F / 30.0F));
                        injection.add(new InsnNode(FRETURN));
                        injection.add(skip);
                        m.instructions.insert(injection);
                    });

            ClassWriter writer = new ClassWriter(COMPUTE_FRAMES);
            classNode.accept(writer);
            return writer.toByteArray();
        }

        @Override
        public String[] getASMTransformerClass() {
            return new String[]{getClass().getName()};
        }

        @Override
        public String getModContainerClass() {
            return null;
        }

        @Override
        public String getSetupClass() {
            return null;
        }

        @Override
        public void injectData(Map<String, Object> data) {}

        @Override
        public String getAccessTransformerClass() {
            return null;
        }
    }
}
