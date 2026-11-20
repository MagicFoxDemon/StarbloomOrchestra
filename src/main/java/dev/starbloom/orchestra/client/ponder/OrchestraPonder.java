package dev.starbloom.orchestra.client.ponder;

import net.createmod.ponder.foundation.PonderIndex;

/**
 * 「思索」（按住 W，Ponder 库，机械动力的作者拆出来的，本模组的 jar 里带着）：用图文场景讲琴凳、工位、乐谱架的归属，
 * 管风琴怎么搭，指挥台怎么用（docs/PONDER.md）。只在客户端、装着 Ponder 时注册（主类按名字判断，这个类才会被加载）。
 *
 * <p>1.21.1 的物品默认数据组件在物品构造时就建好了，Ponder 在模组加载完时注册标签就能放图标，不用等进世界再注册一次。
 */
public final class OrchestraPonder {
    private OrchestraPonder() {}

    public static void init() {
        PonderIndex.addPlugin(new OrchestraPonderPlugin());
    }
}
