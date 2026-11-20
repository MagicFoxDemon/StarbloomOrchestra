package dev.starbloom.orchestra;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 星辰花乐团的创造模式物品栏：方块乐器（键盘、竖琴、管风琴、敲的、拉的、打击乐），然后谱架、指挥台、琴凳，
 * 最后空白纸带、手持乐器和指挥棒。写好的打孔纸带（{@code music_roll}）要在乐器上写，不放空壳。
 * 世界测试 {@code creative_tabs_cover_items} 会找出没进这一页的物品。
 */
public final class OrchestraTabs {
    private static final Logger LOG = LogUtils.getLogger();
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, StarbloomOrchestra.ID);

    /** 物品栏里的顺序。 */
    static final List<String> ORDER = List.of(
            "starbloom_grand_piano", "hexen_concert_harp",
            "hexen_organ_console", "organ_wind_chest", "organ_tin_pipe", "organ_wood_pipe", "organ_trumpet_pipe",
            "hexen_harpsichord", "hexen_celesta", "hexen_glockenspiel", "hexen_tubular_bells",
            "hexen_cello", "hexen_double_bass", "hexen_timpani",
            "hexen_bass_drum", "hexen_snare_drum", "hexen_suspended_cymbal", "hexen_triangle",
            "hexen_music_stand", "hexen_podium", "starbloom_bench", "starbloom_duet_bench",
            "blank_music_roll", "hexen_violin", "hexen_viola", "hexen_flute", "hexen_oboe", "hexen_bassoon", "hexen_trumpet",
            "shinobue", "shakuhachi", "kagura_suzu", "hexen_baton");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> INSTRUMENTS = TABS.register("instruments", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + StarbloomOrchestra.ID))
            .icon(() -> new ItemStack(item("starbloom_grand_piano")))
            .displayItems((params, out) -> {
                for (String id : ORDER) {
                    ItemLike item = item(id);
                    if (item != null) out.accept(item);
                }
            })
            .build());

    private OrchestraTabs() {}

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }

    /** 按 ID 取本模组的物品；写错的 ID 只记日志不崩，世界测试会因为那件物品不在这一页而失败。 */
    private static @Nullable ItemLike item(String id) {
        var key = StarbloomOrchestra.id(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) {
            LOG.error("Creative tab lists an unknown item {}", key);
            return null;
        }
        return BuiltInRegistries.ITEM.get(key);
    }
}
