package dev.starbloom.orchestra;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * 乐器原先是 Logos 附属「炼金釜」的一部分，ID 是 {@code logos_dimension:<名字>}；拆成本模组后名字不变、命名空间换成
 * {@code starbloom_orchestra}。老存档、老结构里的方块、物品、方块实体、实体、物品上的数据组件和附加数据都按旧 ID 存着，
 * 这里给本模组注册的每一项加一个旧 ID 的别名，读档时自动换成新的（NeoForge 的注册表别名，只在找不到旧 ID 时起作用）。
 *
 * <p>附属从 0.12.0 起不再注册这些 ID（neoforge.mods.toml 里要求附属至少是这个版本），所以别名不会和附属自己的条目撞上。
 * 配方、进度按 ID 记的「已解锁」不搬：解锁过的配方在配方书里要重新解锁一次。
 */
final class LegacyIds {
    static final String OLD_NAMESPACE = "logos_dimension";

    private LegacyIds() {}

    static void register(IEventBus bus) {
        // 每个注册表填完之后给本模组的条目加别名；最低优先级，排在所有 DeferredRegister 之后
        bus.addListener(net.neoforged.bus.api.EventPriority.LOWEST, (RegisterEvent event) -> alias(event.getRegistry()));
    }

    private static <T> void alias(Registry<T> registry) {
        for (Identifier id : registry.keySet()) {
            if (!id.getNamespace().equals(StarbloomOrchestra.ID)) continue;
            registry.addAlias(Identifier.fromNamespaceAndPath(OLD_NAMESPACE, id.getPath()), id);
        }
    }
}
