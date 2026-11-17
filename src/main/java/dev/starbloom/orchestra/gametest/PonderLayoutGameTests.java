package dev.starbloom.orchestra.gametest;

import static dev.starbloom.orchestra.ponder.PonderLayouts.*;

import dev.starbloom.orchestra.StarbloomOrchestra;
import dev.starbloom.orchestra.music.Benches;
import dev.starbloom.orchestra.music.InstrumentKind;
import dev.starbloom.orchestra.music.InstrumentPosts;
import dev.starbloom.orchestra.music.MusicStandBlock;
import dev.starbloom.orchestra.music.StarbloomBenchBlock;
import dev.starbloom.orchestra.music.StarbloomDuetBenchBlock;
import dev.starbloom.orchestra.music.organ.OrganPipeBlock;
import dev.starbloom.orchestra.music.organ.OrganStructure;
import dev.starbloom.orchestra.ponder.PonderLayouts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 思索场景的布置（{@link PonderLayouts}）在世界里像玩家那样放一遍，核对场景里讲的事是真的；
 * 带系统属性 {@code starbloom_orchestra.ponder_export}（{@code -PponderExport}）时把每一份存成思索用的蓝图。
 */
public final class PonderLayoutGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, StarbloomOrchestra.ID);
    private static final String NAME = "ponder_layouts";

    private PonderLayoutGameTests() {}

    public static void register(IEventBus bus) {
        FUNCTIONS.register(NAME, () -> PonderLayoutGameTests::layouts);
        FUNCTIONS.register(bus);
        bus.addListener((RegisterGameTestsEvent e) -> {
            var env = e.registerEnvironment(StarbloomOrchestra.id("ponder_layouts_default"), new TestEnvironmentDefinition.AllOf());
            e.registerTest(StarbloomOrchestra.id(NAME), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, StarbloomOrchestra.id(NAME)),
                new TestData<>(env, Identifier.withDefaultNamespace("empty"), 200, 0, true, Rotation.NONE, false, 1, 1, false, 14)));
        });
    }

    /** 往世界里放，坐标是蓝图里的、加上 origin。 */
    private record WorldPlacer(ServerLevel level, BlockPos origin) implements PonderLayouts.Placer {
        BlockPos abs(BlockPos rel) {
            return origin.offset(rel);
        }

        @Override
        public void block(BlockPos pos, BlockState state) {
            level.setBlockAndUpdate(abs(pos), state);
        }

        @Override
        public void place(BlockPos pos, BlockState state) {
            level.setBlockAndUpdate(abs(pos), state);
            state.getBlock().setPlacedBy(level, abs(pos), state, null, ItemStack.EMPTY);
        }

        @Override
        public void pipe(BlockPos bottom, Block pipe, Direction facing, int height) {
            for (int i = 0; i < height; i++) {
                BlockPos p = abs(bottom.above(i));
                level.setBlockAndUpdate(p, pipe.defaultBlockState().setValue(OrganPipeBlock.FACING, facing));
                if (pipe instanceof OrganPipeBlock b) level.setBlockAndUpdate(p, b.withShape(level.getBlockState(p), level, p));
            }
            // 叠上去之后下面几格的形状要跟着变（接成长管），从下往上再算一遍
            for (int i = 0; i < height; i++) {
                BlockPos p = abs(bottom.above(i));
                if (pipe instanceof OrganPipeBlock b) level.setBlockAndUpdate(p, b.withShape(level.getBlockState(p), level, p));
            }
        }
    }

    private static void clear(ServerLevel level, BlockPos origin) {
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-2, 0, -2), origin.offset(12, 9, 12))) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void layouts(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(1, 1, 1));
        String export = System.getProperty("starbloom_orchestra.ponder_export", "");
        for (Layout layout : ALL) {
            clear(level, origin);
            WorldPlacer p = new WorldPlacer(level, origin);
            layout.build().accept(p);
            if (!export.isEmpty()) save(level, origin, layout, Path.of(export));
            check(h, layout, p);
        }
        clear(level, origin);
        h.succeed();
    }

    private static void save(ServerLevel level, BlockPos origin, Layout layout, Path dir) {
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, origin, layout.size(), false, List.of());
        CompoundTag saved = template.save(new CompoundTag());
        // 指挥台存着自己的世界坐标（origin，被搬到别处时按它平移成员）：换成蓝图里的坐标，每次导出的结果才一样
        for (Tag t : saved.getListOrEmpty("blocks")) {
            if (!(t instanceof CompoundTag block)) continue;
            ListTag pos = block.getListOrEmpty("pos");
            block.getCompound("nbt").filter(nbt -> nbt.contains("origin"))
                .ifPresent(nbt -> nbt.putLong("origin", new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0)).asLong()));
        }
        try {
            Files.createDirectories(dir);
            NbtIo.writeCompressed(saved, dir.resolve(layout.id() + ".nbt"));
        } catch (IOException e) {
            throw new IllegalStateException("cannot write ponder schematic " + layout.id(), e);
        }
    }

    /** 每个场景讲的事。 */
    private static void check(GameTestHelper h, Layout layout, WorldPlacer p) {
        ServerLevel level = p.level();
        switch (layout.id()) {
            case "benches" -> {
                h.assertTrue(p.abs(BENCHES_PIANO).equals(StarbloomBenchBlock.linked(level, p.abs(BENCHES_AT_KEYS))), "the bench at the keys plays the piano");
                h.assertTrue(StarbloomBenchBlock.linked(level, p.abs(BENCHES_WRONG)) == null, "the bench beside the piano is just a bench");
                h.assertTrue(p.abs(BENCHES_HARP).equals(StarbloomBenchBlock.linked(level, p.abs(BENCHES_BEHIND_HARP))), "the bench behind the harp plays it");
                // 换成双人琴凳（场景后半段）：照样关联钢琴
                level.setBlockAndUpdate(p.abs(BENCHES_AT_KEYS), Blocks.AIR.defaultBlockState());
                p.place(BENCHES_DUET, state(Benches.DUET.get(), Direction.SOUTH));
                h.assertTrue(level.getBlockState(p.abs(BENCHES_AT_KEYS)).getValue(StarbloomDuetBenchBlock.SIDE), "the duet bench's other cell is the old bench's");
                h.assertTrue(p.abs(BENCHES_PIANO).equals(StarbloomBenchBlock.linked(level, p.abs(BENCHES_DUET))), "the duet bench plays the piano");
            }
            case "music_stand" -> {
                BlockState stand = level.getBlockState(p.abs(STAND));
                h.assertTrue(StarbloomBenchBlock.linked(level, p.abs(STAND_BENCH)) == null, "the bench faces no instrument");
                // 凳子上坐着拉小提琴的乐手时，空乐谱架就是它的谱台
                level.setBlockAndUpdate(p.abs(STAND_BENCH), level.getBlockState(p.abs(STAND_BENCH)).setValue(StarbloomBenchBlock.PLAYS, MusicStandBlock.Holds.VIOLIN));
                h.assertTrue(p.abs(STAND_BENCH).equals(MusicStandBlock.seatedPlayer(level, p.abs(STAND), stand)), "the stand serves the seated violinist");
                BlockState holding = stand.setValue(MusicStandBlock.HOLDS, MusicStandBlock.Holds.VIOLIN);
                h.assertTrue(MusicStandBlock.seatedPlayer(level, p.abs(STAND), holding) == null, "a stand holding a violin is the violin itself");
            }
            case "posts_strings" -> {
                behind(h, p, STRINGS_CELLO, InstrumentKind.CELLO);
                behind(h, p, STRINGS_BASS, InstrumentKind.DOUBLE_BASS);
            }
            case "posts_mallets" -> {
                post(h, p, MALLETS_GLOCKENSPIEL, InstrumentKind.GLOCKENSPIEL, MALLETS_GLOCKENSPIEL.north());
                post(h, p, MALLETS_BELLS, InstrumentKind.TUBULAR_BELLS, MALLETS_BELLS.north());
                post(h, p, MALLETS_SNARE, InstrumentKind.SNARE_DRUM, MALLETS_SNARE.north());
                post(h, p, MALLETS_BASS_DRUM, InstrumentKind.BASS_DRUM, MALLETS_BASS_DRUM.west());
            }
            case "timpani" -> {
                Set<BlockPos> reached = new HashSet<>(StarbloomBenchBlock.linkedAll(level, p.abs(TIMPANI_BENCH)));
                h.assertTrue(reached.equals(Set.of(p.abs(TIMPANI_WEST), p.abs(TIMPANI_EAST))), "the duet bench plays both drums: " + reached);
                level.setBlockAndUpdate(p.abs(TIMPANI_BENCH_SIDE), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(p.abs(TIMPANI_BENCH), state(Benches.BENCH.get(), Direction.SOUTH));
                h.assertTrue(p.abs(TIMPANI_EAST).equals(StarbloomBenchBlock.linked(level, p.abs(TIMPANI_BENCH))), "a single bench plays the drum in front of it");
            }
            case "pipe_organ" -> {
                OrganStructure organ = OrganStructure.scan(level, level.getBlockState(p.abs(ORGAN_CONSOLE)), p.abs(ORGAN_CONSOLE));
                int tin = 0;
                for (int t : ORGAN_TIN_HEIGHT) tin += t;
                h.assertTrue(organ.chests() == ORGAN_CHESTS.size() + ORGAN_RAISED_CHESTS.size(), "every wind chest is connected: " + organ);
                h.assertTrue(organ.tin() == tin && organ.wood() == 3 * ORGAN_WOOD.size() && organ.reed() == ORGAN_TRUMPETS.size(),
                    "every pipe is counted: " + organ);
                h.assertTrue(p.abs(ORGAN_CONSOLE).equals(StarbloomBenchBlock.linked(level, p.abs(ORGAN_BENCH))), "the bench in front of the middle plays the organ");
                BlockState top = level.getBlockState(p.abs(ORGAN_TIN.get(1).above(ORGAN_TIN_HEIGHT[1] - 1)));
                BlockState bottom = level.getBlockState(p.abs(ORGAN_TIN.get(1)));
                h.assertTrue(!top.getValue(OrganPipeBlock.SHAPE).equals(bottom.getValue(OrganPipeBlock.SHAPE)), "a stacked pipe has a top and a bottom");
            }
            case "podium" -> {
                h.assertTrue(p.abs(PODIUM_PIANO).equals(StarbloomBenchBlock.linked(level, p.abs(PODIUM_PIANO_BENCH))), "the piano's bench plays the piano");
                h.assertTrue(StarbloomBenchBlock.linked(level, p.abs(PODIUM_STAND_BENCH)) == null, "the bench at the music stand is a seat");
                h.assertTrue(level.getBlockState(p.abs(PODIUM.west())).is(level.getBlockState(p.abs(PODIUM)).getBlock()), "the podium is two wide");
            }
            default -> throw new IllegalStateException("unchecked ponder layout " + layout.id());
        }
    }

    /** 大提琴、低音提琴的人就在乐器这一格里、琴后那一半，和琴朝同一个方向（场景里说「在琴后」）。 */
    private static void behind(GameTestHelper h, WorldPlacer p, BlockPos instrument, InstrumentKind kind) {
        BlockPos main = p.abs(instrument);
        Direction facing = p.level().getBlockState(main).getValue(BlockStateProperties.HORIZONTAL_FACING);
        Vec3 at = InstrumentPosts.position(main, facing, kind);
        h.assertTrue(Mth.floor(at.x) == main.getX() && Mth.floor(at.z) == main.getZ(), kind + " player is inside the instrument's cell: " + at);
        Vec3 off = at.subtract(Vec3.atCenterOf(main));
        h.assertTrue(off.x * facing.getStepX() + off.z * facing.getStepZ() < 0, kind + " player is behind the instrument: " + at);
        h.assertTrue(InstrumentPosts.performerFacing(kind, facing) == facing, kind + " player faces the same way as the instrument");
    }

    /** 工位在这一格：空着、下面有地面。 */
    private static void post(GameTestHelper h, WorldPlacer p, BlockPos instrument, InstrumentKind kind, BlockPos expected) {
        ServerLevel level = p.level();
        Direction facing = level.getBlockState(p.abs(instrument)).getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos post = BlockPos.containing(InstrumentPosts.position(p.abs(instrument), facing, kind));
        h.assertTrue(post.equals(p.abs(expected)), kind + " post at " + post + ", expected " + p.abs(expected));
        h.assertTrue(level.getBlockState(post).isAir() && !level.getBlockState(post.below()).isAir(), kind + " post is clear with ground under it");
    }
}
