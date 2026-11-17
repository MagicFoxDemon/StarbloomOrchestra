package dev.starbloom.orchestra.gametest;

import dev.starbloom.orchestra.OrchestraTabs;
import dev.starbloom.orchestra.StarbloomOrchestra;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 创造模式物品栏：本模组的每件物品都在「星辰花乐团」页里（写好的打孔纸带除外，要在乐器上写）。 */
public final class CreativeTabGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, StarbloomOrchestra.ID);
    private static final String NAME = "creative_tabs_cover_items";

    private CreativeTabGameTests() {}

    public static void register(IEventBus bus) {
        FUNCTIONS.register(NAME, () -> CreativeTabGameTests::cover);
        FUNCTIONS.register(bus);
        bus.addListener((RegisterGameTestsEvent e) -> {
            var env = e.registerEnvironment(StarbloomOrchestra.id("creative_tabs_default"), new TestEnvironmentDefinition.AllOf());
            e.registerTest(StarbloomOrchestra.id(NAME), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, StarbloomOrchestra.id(NAME)),
                    new TestData<>(env, Identifier.withDefaultNamespace("empty"), 100, 0, true, Rotation.NONE, false, 1, 1, false, 2)));
        });
    }

    private static void cover(GameTestHelper h) {
        var level = h.getLevel();
        var params = new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess());
        Set<Item> listed = new HashSet<>();
        CreativeModeTab tab = OrchestraTabs.INSTRUMENTS.get();
        tab.buildContents(params);
        for (ItemStack stack : tab.getDisplayItems()) listed.add(stack.getItem());
        List<String> missing = new ArrayList<>();
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            var id = entry.getKey().identifier();
            if (!id.getNamespace().equals(StarbloomOrchestra.ID) || id.getPath().equals("music_roll")) continue;
            if (!listed.contains(entry.getValue())) missing.add(id.getPath());
        }
        h.assertTrue(missing.isEmpty(), "Not in the creative tab: " + missing);
        h.succeed();
    }
}
