package dev.starbloom.orchestra.gametest;

import dev.starbloom.orchestra.OrchestraTabs;
import dev.starbloom.orchestra.StarbloomOrchestra;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;

/** 创造模式物品栏：本模组的每件物品都在「星辰花乐团」页里（写好的打孔纸带除外，要在乐器上写）。 */
public final class CreativeTabGameTests {
    private static final String NAME = "creative_tabs_cover_items";

    private CreativeTabGameTests() {}

    public static void register(IEventBus bus) {
        GameTestFunctions.register(bus, CreativeTabGameTests.class);
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return GameTestFunctions.batch("creative_tabs_default", 2, 100, 0, Map.of(NAME, CreativeTabGameTests::cover));
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
            var id = entry.getKey().location();
            if (!id.getNamespace().equals(StarbloomOrchestra.ID) || id.getPath().equals("music_roll")) continue;
            if (!listed.contains(entry.getValue())) missing.add(id.getPath());
        }
        h.assertTrue(missing.isEmpty(), "Not in the creative tab: " + missing);
        h.succeed();
    }
}
