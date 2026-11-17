package dev.starbloom.orchestra;

import net.neoforged.neoforge.registries.DeferredRegister;

/** 本模组的方块与物品；方块实体、实体、数据组件等各自在用到它们的类里注册。 */
public final class OrchestraRegistries {
    private OrchestraRegistries() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StarbloomOrchestra.ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StarbloomOrchestra.ID);
}
