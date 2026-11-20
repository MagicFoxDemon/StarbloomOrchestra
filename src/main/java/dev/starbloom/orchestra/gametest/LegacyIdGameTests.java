package dev.starbloom.orchestra.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.starbloom.orchestra.music.Instruments;
import dev.starbloom.orchestra.music.orchestra.Orchestra;
import java.util.Collection;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;

/**
 * 乐器原先在 Logos 附属里（{@code logos_dimension:*}）：老存档按旧 ID 存的方块状态、物品（连同物品上的数据组件）、
 * 方块实体和实体，读出来都是本模组的（{@code LegacyIds} 注册的别名）。
 */
public final class LegacyIdGameTests {
    private static final String NAME = "legacy_addon_ids_resolve";

    private LegacyIdGameTests() {}

    public static void register(IEventBus bus) {
        GameTestFunctions.register(bus, LegacyIdGameTests.class);
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return GameTestFunctions.batch("legacy_ids_default", 2, 100, 0, Map.of(NAME, LegacyIdGameTests::resolve));
    }

    private static ResourceLocation old(String path) {
        return ResourceLocation.fromNamespaceAndPath("logos_dimension", path);
    }

    private static void resolve(GameTestHelper h) {
        h.assertTrue(BuiltInRegistries.BLOCK.get(old("hexen_triangle")) == Instruments.TRIANGLE.get(), "old block id → triangle");
        h.assertTrue(BuiltInRegistries.ITEM.get(old("hexen_violin")) == Instruments.VIOLIN.get(), "old item id → violin");
        h.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.get(old("instrument")) == Instruments.INSTRUMENT_ENTITY.get(), "old block entity id");
        h.assertTrue(BuiltInRegistries.ENTITY_TYPE.get(old("standing_post")) == Instruments.STANDING_POST.get(), "old entity id");

        // 区块里存的方块状态
        CompoundTag state = new CompoundTag();
        state.putString("Name", "logos_dimension:hexen_triangle");
        CompoundTag props = new CompoundTag();
        props.putString("facing", "east");
        state.put("Properties", props);
        BlockState decoded = BlockState.CODEC.parse(NbtOps.INSTANCE, state).getOrThrow();
        h.assertTrue(decoded.is(Instruments.TRIANGLE.get()), "old block state → triangle: " + decoded);

        // 物品栏里存的物品和它的数据组件（指挥棒绑定的指挥台）
        var ops = RegistryOps.create(JsonOps.INSTANCE, h.getLevel().registryAccess());
        var json = JsonParser.parseString("""
            {"id": "logos_dimension:hexen_baton", "count": 1,
             "components": {"logos_dimension:baton_podium": {"dimension": "minecraft:overworld", "pos": [1, 2, 3]}}}""");
        ItemStack stack = ItemStack.CODEC.parse(ops, json).getOrThrow();
        h.assertTrue(stack.is(Orchestra.BATON.get()), "old baton item: " + stack);
        h.assertTrue(stack.get(Orchestra.BATON_PODIUM.get()) != null, "old baton keeps its podium component");
        h.succeed();
    }
}
