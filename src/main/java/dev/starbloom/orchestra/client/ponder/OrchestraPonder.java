package dev.starbloom.orchestra.client.ponder;

import net.createmod.ponder.api.client.PonderIndex;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 「思索」（按住 W，Ponder 库，机械动力的作者拆出来的，本模组的 jar 里带着）：用图文场景讲琴凳、工位、乐谱架的归属，
 * 管风琴怎么搭，指挥台怎么用（docs/PONDER.md）。只在客户端、装着 Ponder 时注册（主类按名字判断，这个类才会被加载）。
 */
public final class OrchestraPonder {
    private OrchestraPonder() {}

    public static void init() {
        PonderIndex.addPlugin(new OrchestraPonderPlugin());
        // Ponder 在模组加载完时注册标签，那时物品的数据组件还没绑定，标签没有图标；进了世界（组件绑好了）再注册一次
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> {
            if (!OrchestraPonderPlugin.iconsRegistered) PonderIndex.reload();
        });
    }
}
