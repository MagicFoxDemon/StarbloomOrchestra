package dev.starbloom.orchestra.client.ponder;

import dev.starbloom.orchestra.StarbloomOrchestra;
import dev.starbloom.orchestra.music.Benches;
import dev.starbloom.orchestra.music.Instruments;
import dev.starbloom.orchestra.music.orchestra.Orchestra;
import java.util.List;
import java.util.function.Supplier;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.registration.TagBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

/**
 * 星辰花乐团的思索场景和标签（场景脚本在 {@link OrchestraScenes}，蓝图由 {@code ponder.PonderLayouts} 生成）。
 * 一个物品可以有好几个场景，按住 W 时按这里注册的顺序排。
 */
public final class OrchestraPonderPlugin implements PonderPlugin {
    public static final ResourceLocation MUSICIANS = StarbloomOrchestra.id("musicians");
    public static final ResourceLocation PIPE_ORGAN = StarbloomOrchestra.id("pipe_organ");
    public static final ResourceLocation ORCHESTRA = StarbloomOrchestra.id("orchestra");

    @Override
    public String getModId() {
        return StarbloomOrchestra.ID;
    }

    @SafeVarargs
    private static Item[] items(Supplier<? extends ItemLike>... holders) {
        return java.util.Arrays.stream(holders).map(h -> h.get().asItem()).toArray(Item[]::new);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> registration) {
        PonderSceneRegistrationHelper<Item> helper = registration.withKeyFunction(BuiltInRegistries.ITEM::getKey);
        helper.forComponents(items(Benches.BENCH_ITEM, Benches.DUET_ITEM, Instruments.GRAND_PIANO_ITEM, Instruments.HARPSICHORD_ITEM,
                Instruments.CELESTA_ITEM, Instruments.CONCERT_HARP_ITEM))
            .addStoryBoard("benches", OrchestraScenes::benches, MUSICIANS);
        helper.forComponents(items(Instruments.MUSIC_STAND_ITEM, Benches.BENCH_ITEM, Instruments.VIOLIN, Instruments.VIOLA,
                Instruments.FLUTE, Instruments.OBOE, Instruments.BASSOON, Instruments.TRUMPET, Instruments.SHINOBUE, Instruments.KAGURA_SUZU, Instruments.SHAKUHACHI))
            .addStoryBoard("music_stand", OrchestraScenes::musicStand, MUSICIANS);
        helper.forComponents(items(Instruments.CELLO_ITEM, Instruments.DOUBLE_BASS_ITEM))
            .addStoryBoard("posts_strings", OrchestraScenes::postsStrings, MUSICIANS);
        helper.forComponents(items(Instruments.GLOCKENSPIEL_ITEM, Instruments.TUBULAR_BELLS_ITEM, Instruments.SNARE_DRUM_ITEM,
                Instruments.SUSPENDED_CYMBAL_ITEM, Instruments.TRIANGLE_ITEM, Instruments.BASS_DRUM_ITEM))
            .addStoryBoard("posts_mallets", OrchestraScenes::postsMallets, MUSICIANS);
        helper.forComponents(items(Instruments.TIMPANI_ITEM, Benches.DUET_ITEM, Benches.BENCH_ITEM))
            .addStoryBoard("timpani", OrchestraScenes::timpani, MUSICIANS);
        helper.forComponents(items(Instruments.ORGAN_CONSOLE_ITEM, Instruments.ORGAN_WIND_CHEST_ITEM, Instruments.ORGAN_TIN_PIPE_ITEM,
                Instruments.ORGAN_WOOD_PIPE_ITEM, Instruments.ORGAN_TRUMPET_PIPE_ITEM))
            .addStoryBoard("pipe_organ", OrchestraScenes::pipeOrgan, PIPE_ORGAN);
        helper.forComponents(items(Orchestra.PODIUM_ITEM, Orchestra.BATON))
            .addStoryBoard("podium", OrchestraScenes::podium, ORCHESTRA);
    }

    /** 标签的图标：1.21.1 的物品默认数据组件在物品构造时就有了，Ponder 在模组加载完时注册标签，这时就能放图标。 */
    private static TagBuilder icon(TagBuilder tag, Supplier<? extends ItemLike> item) {
        return tag.item(item.get(), true, false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> registration) {
        PonderTagRegistrationHelper<Item> helper = registration.withKeyFunction(BuiltInRegistries.ITEM::getKey);
        icon(registration.registerTag(MUSICIANS).addToIndex(), Benches.BENCH_ITEM)
            .title("Where musicians sit and stand")
            .description("Which bench, post or music stand belongs to which instrument")
            .register();
        icon(registration.registerTag(PIPE_ORGAN).addToIndex(), Instruments.ORGAN_CONSOLE_ITEM)
            .title("Pipe organ")
            .description("Console, wind chests and pipes")
            .register();
        icon(registration.registerTag(ORCHESTRA).addToIndex(), Orchestra.PODIUM_ITEM)
            .title("Orchestra and conducting")
            .description("Podium, baton and the instruments that join the orchestra")
            .register();
        for (Item item : items(Benches.BENCH_ITEM, Benches.DUET_ITEM, Instruments.MUSIC_STAND_ITEM, Instruments.GRAND_PIANO_ITEM,
                Instruments.HARPSICHORD_ITEM, Instruments.CELESTA_ITEM, Instruments.CONCERT_HARP_ITEM, Instruments.CELLO_ITEM,
                Instruments.DOUBLE_BASS_ITEM, Instruments.GLOCKENSPIEL_ITEM, Instruments.TUBULAR_BELLS_ITEM, Instruments.TIMPANI_ITEM,
                Instruments.BASS_DRUM_ITEM, Instruments.SNARE_DRUM_ITEM, Instruments.SUSPENDED_CYMBAL_ITEM, Instruments.TRIANGLE_ITEM)) {
            helper.addTagToComponent(item, MUSICIANS);
        }
        for (Item item : items(Instruments.ORGAN_CONSOLE_ITEM, Instruments.ORGAN_WIND_CHEST_ITEM, Instruments.ORGAN_TIN_PIPE_ITEM,
                Instruments.ORGAN_WOOD_PIPE_ITEM, Instruments.ORGAN_TRUMPET_PIPE_ITEM)) {
            helper.addTagToComponent(item, PIPE_ORGAN);
        }
        for (Item item : List.of(Orchestra.PODIUM_ITEM.get().asItem(), Orchestra.BATON.get())) {
            helper.addTagToComponent(item, ORCHESTRA);
        }
    }
}
