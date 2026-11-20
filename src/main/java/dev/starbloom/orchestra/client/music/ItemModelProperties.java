package dev.starbloom.orchestra.client.music;

import dev.starbloom.orchestra.OrchestraRegistries;
import dev.starbloom.orchestra.StarbloomOrchestra;
import dev.starbloom.orchestra.music.HandheldInstrumentItem;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 物品模型用的属性（1.20.1 的 {@link ItemProperties}）。
 *
 * <p>手持乐器演奏时换成 `_playing` 模型（第一人称把乐器摆到下巴、嘴边）：`models/item/<乐器>.json` 的 `overrides`
 * 在 `starbloom_orchestra:playing` 为 1 时换成 `item/<乐器>_playing`。属性的判断和原版弓的 `pulling` 一样——
 * 拿着它的生物正在使用的就是这一叠物品——也就是 26.1 物品模型定义里 `minecraft:using_item` 条件的意思。
 *
 * <p>属性挂在每个 {@link HandheldInstrumentItem} 上（以后加的手持乐器自动带上），生成脚本给有 `_playing` 模型的物品写 overrides
 * （tools/instrument_kit.py 的 `Out.flush`）。
 */
@Mod.EventBusSubscriber(modid = StarbloomOrchestra.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ItemModelProperties {
    private ItemModelProperties() {}

    /** 正在演奏（使用中）：1，否则 0。 */
    public static final ResourceLocation PLAYING = StarbloomOrchestra.id("playing");

    /** 生物正在使用的正是这一叠物品时为 1。 */
    static final ClampedItemPropertyFunction USING = (stack, level, entity, seed) ->
        entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F;

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // ItemProperties 的表不是线程安全的，放回主线程登记
        event.enqueueWork(() -> {
            for (var holder : OrchestraRegistries.ITEMS.getEntries()) {
                Item item = holder.get();
                if (item instanceof HandheldInstrumentItem) ItemProperties.register(item, PLAYING, USING);
            }
        });
    }
}
