package dev.starbloom.orchestra;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** 本模组的方块与物品；方块实体、实体、配方等各自在用到它们的类里注册。 */
public final class OrchestraRegistries {
    private OrchestraRegistries() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, StarbloomOrchestra.ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, StarbloomOrchestra.ID);
}
