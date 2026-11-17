package dev.starbloom.orchestra.ponder;

import dev.starbloom.orchestra.music.Benches;
import dev.starbloom.orchestra.music.Instruments;
import dev.starbloom.orchestra.music.organ.OrganPipeBlock;
import dev.starbloom.orchestra.music.orchestra.Orchestra;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 「思索」（按住 W，Ponder）场景的布置，docs/PONDER.md。每个场景一份：地板（第 0 层，方格）和场景里会出现的全部方块。
 *
 * <p>同一份布置有两个用处：世界测试 {@code ponder_layouts} 在世界里像玩家那样真的放一遍（多格乐器由 {@code setPlacedBy}
 * 补上其余几格），核对场景里讲的事是真的（琴凳关联了钢琴、双人琴凳对着两面鼓、管风琴认出了这些音管……），
 * 带 {@code -PponderExport} 时再把放好的这一块存成 {@code assets/starbloom_orchestra/ponder/<名字>.nbt}；客户端的场景脚本
 * （{@code client.ponder.OrchestraScenes}）用这里的坐标选方块、指位置。
 *
 * <p>坐标是蓝图里的（地板在 y = 0）。思索界面一开始的镜头从 x、z 都最小的那个角往里看，所以乐器尽量朝北、朝西放，
 * 演奏的那一面朝着镜头。
 */
public final class PonderLayouts {
    private PonderLayouts() {}

    /** 往世界里放方块：{@code block} 原样放，{@code place} 放主块后像玩家放下那样补上其余几格。 */
    public interface Placer {
        void block(BlockPos pos, BlockState state);

        void place(BlockPos pos, BlockState state);

        /** 一根音管：从 bottom 往上叠 height 格，每格按上下邻格接成长管。 */
        void pipe(BlockPos bottom, Block pipe, Direction facing, int height);
    }

    /** id：蓝图的路径（ponder/&lt;id&gt;.nbt），plate：地板边长，size：存下来的大小。 */
    public record Layout(String id, int plate, Vec3i size, Consumer<Placer> build) {}

    static BlockState facing(Block block, Direction facing) {
        return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    static void floor(Placer p, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                p.block(new BlockPos(x, 0, z), ((x + z) & 1) == 0 ? Blocks.CALCITE.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
    }

    // —— 琴凳的归属：钢琴（朝北）、放对 / 放错的琴凳、竖琴后的琴凳 ——

    public static final BlockPos BENCHES_PIANO = new BlockPos(1, 1, 3);
    /** 钢琴占的四格：前排（琴键）z = 3，后排 z = 4。 */
    public static final BlockPos BENCHES_PIANO_FAR = new BlockPos(2, 1, 4);
    public static final BlockPos BENCHES_AT_KEYS = new BlockPos(1, 1, 2);
    /** 换成双人琴凳时的主块（另一格就是 {@link #BENCHES_AT_KEYS}，在坐的人右手边）。 */
    public static final BlockPos BENCHES_DUET = new BlockPos(2, 1, 2);
    public static final BlockPos BENCHES_WRONG = new BlockPos(3, 1, 3);
    /** 竖琴（朝东）和它琴后的琴凳：钢琴收起来以后才出现，放在靠中间的地方。 */
    public static final BlockPos BENCHES_HARP = new BlockPos(4, 1, 4);
    public static final BlockPos BENCHES_BEHIND_HARP = new BlockPos(3, 1, 4);

    public static final Layout BENCHES = new Layout("benches", 7, new Vec3i(7, 3, 7), p -> {
        floor(p, 7);
        p.place(BENCHES_PIANO, facing(Instruments.GRAND_PIANO.get(), Direction.NORTH));
        p.place(BENCHES_AT_KEYS, facing(Benches.BENCH.get(), Direction.SOUTH));
        p.place(BENCHES_WRONG, facing(Benches.BENCH.get(), Direction.WEST));
        p.place(BENCHES_HARP, facing(Instruments.CONCERT_HARP.get(), Direction.EAST));
        p.place(BENCHES_BEHIND_HARP, facing(Benches.BENCH.get(), Direction.EAST));
    });

    // —— 乐谱架与手持乐器：一张没对着乐器的琴凳，后面一个空乐谱架，谱面朝着凳子 ——

    public static final BlockPos STAND_BENCH = new BlockPos(2, 1, 1);
    public static final BlockPos STAND = new BlockPos(2, 1, 2);

    public static final Layout MUSIC_STAND = new Layout("music_stand", 5, new Vec3i(5, 2, 5), p -> {
        floor(p, 5);
        p.place(STAND_BENCH, facing(Benches.BENCH.get(), Direction.SOUTH));
        p.place(STAND, facing(Instruments.MUSIC_STAND.get(), Direction.NORTH));
    });

    // —— 大提琴、低音提琴：琴朝南，人在琴后（这一格里北边那一半，靠镜头这边；大提琴坐琴后的凳子，低音提琴站着） ——

    public static final BlockPos STRINGS_CELLO = new BlockPos(1, 1, 3);
    public static final BlockPos STRINGS_BASS = new BlockPos(3, 1, 3);
    public static final Direction STRINGS_FACING = Direction.SOUTH;

    public static final Layout POSTS_STRINGS = new Layout("posts_strings", 5, new Vec3i(5, 3, 5), p -> {
        floor(p, 5);
        p.place(STRINGS_CELLO, facing(Instruments.CELLO.get(), STRINGS_FACING));
        p.place(STRINGS_BASS, facing(Instruments.DOUBLE_BASS.get(), STRINGS_FACING));
    });

    // —— 站着敲的：钟琴、管钟、小军鼓朝北（人在北面），大鼓朝南（侧放，人在西边） ——

    public static final BlockPos MALLETS_GLOCKENSPIEL = new BlockPos(1, 1, 4);
    public static final BlockPos MALLETS_BELLS = new BlockPos(3, 1, 4);
    public static final BlockPos MALLETS_SNARE = new BlockPos(5, 1, 4);
    public static final BlockPos MALLETS_BASS_DRUM = new BlockPos(4, 1, 1);

    public static final Layout POSTS_MALLETS = new Layout("posts_mallets", 7, new Vec3i(7, 3, 7), p -> {
        floor(p, 7);
        p.place(MALLETS_GLOCKENSPIEL, facing(Instruments.GLOCKENSPIEL.get(), Direction.NORTH));
        p.place(MALLETS_BELLS, facing(Instruments.TUBULAR_BELLS.get(), Direction.NORTH));
        p.place(MALLETS_SNARE, facing(Instruments.SNARE_DRUM.get(), Direction.NORTH));
        p.place(MALLETS_BASS_DRUM, facing(Instruments.BASS_DRUM.get(), Direction.SOUTH));
    });

    // —— 定音鼓：两面鼓朝南，鼓后（北面）一张双人琴凳 ——

    public static final BlockPos TIMPANI_WEST = new BlockPos(1, 1, 2);
    public static final BlockPos TIMPANI_EAST = new BlockPos(2, 1, 2);
    /** 双人琴凳主块（另一格在西边，坐的人右手边），换成单人琴凳时也放在这一格。 */
    public static final BlockPos TIMPANI_BENCH = new BlockPos(2, 1, 1);
    public static final BlockPos TIMPANI_BENCH_SIDE = new BlockPos(1, 1, 1);

    public static final Layout TIMPANI = new Layout("timpani", 5, new Vec3i(5, 2, 5), p -> {
        floor(p, 5);
        p.place(TIMPANI_WEST, facing(Instruments.TIMPANI.get(), Direction.SOUTH));
        p.place(TIMPANI_EAST, facing(Instruments.TIMPANI.get(), Direction.SOUTH));
        p.place(TIMPANI_BENCH, facing(Benches.DUET.get(), Direction.SOUTH));
    });

    // —— 管风琴：演奏台（3 宽 2 高）朝北，正中前面一张琴凳；背后一排风箱、两边往后再往上接一级；音管立在风箱上 ——

    public static final BlockPos ORGAN_CONSOLE = new BlockPos(4, 1, 3);
    public static final BlockPos ORGAN_BENCH = new BlockPos(4, 1, 2);
    /** 演奏台背后的一排风箱（x = 2..6）。 */
    public static final List<BlockPos> ORGAN_CHESTS = List.of(new BlockPos(3, 1, 4), new BlockPos(4, 1, 4), new BlockPos(5, 1, 4),
        new BlockPos(2, 1, 4), new BlockPos(6, 1, 4));
    /** 两边往后一格、再往上一级的风箱。 */
    public static final List<BlockPos> ORGAN_RAISED_CHESTS = List.of(new BlockPos(2, 1, 5), new BlockPos(2, 2, 5),
        new BlockPos(6, 1, 5), new BlockPos(6, 2, 5));
    /** 锡管：立在中间三个风箱上（高 4、5、4）。 */
    public static final List<BlockPos> ORGAN_TIN = List.of(new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4));
    public static final int[] ORGAN_TIN_HEIGHT = {4, 5, 4};
    /** 木管：立在两边高起来的风箱上（高 3）。 */
    public static final List<BlockPos> ORGAN_WOOD = List.of(new BlockPos(2, 3, 5), new BlockPos(6, 3, 5));
    /** 号角管：立在两端的风箱上，喇叭朝北。 */
    public static final List<BlockPos> ORGAN_TRUMPETS = List.of(new BlockPos(2, 2, 4), new BlockPos(6, 2, 4));

    public static final Layout PIPE_ORGAN = new Layout("pipe_organ", 9, new Vec3i(9, 7, 9), p -> {
        floor(p, 9);
        p.place(ORGAN_CONSOLE, Instruments.ORGAN_CONSOLE.get().placed(Direction.NORTH));
        p.place(ORGAN_BENCH, facing(Benches.BENCH.get(), Direction.SOUTH));
        for (BlockPos c : ORGAN_CHESTS) p.block(c, Instruments.ORGAN_WIND_CHEST.get().defaultBlockState());
        for (BlockPos c : ORGAN_RAISED_CHESTS) p.block(c, Instruments.ORGAN_WIND_CHEST.get().defaultBlockState());
        for (int i = 0; i < ORGAN_TIN.size(); i++) p.pipe(ORGAN_TIN.get(i), Instruments.ORGAN_TIN_PIPE.get(), Direction.NORTH, ORGAN_TIN_HEIGHT[i]);
        for (BlockPos w : ORGAN_WOOD) p.pipe(w, Instruments.ORGAN_WOOD_PIPE.get(), Direction.NORTH, 3);
        for (BlockPos t : ORGAN_TRUMPETS) p.block(t, facing(Instruments.ORGAN_TRUMPET_PIPE.get(), Direction.NORTH));
    });

    // —— 指挥台与乐团：指挥台朝南（指挥面朝乐团），对面一台钢琴和它的琴凳、一把大提琴、一张对着乐谱架的琴凳 ——

    public static final BlockPos PODIUM = new BlockPos(4, 1, 1);
    public static final BlockPos PODIUM_PIANO = new BlockPos(1, 1, 5);
    public static final BlockPos PODIUM_PIANO_BENCH = new BlockPos(1, 1, 4);
    public static final BlockPos PODIUM_CELLO = new BlockPos(7, 1, 5);
    public static final BlockPos PODIUM_STAND = new BlockPos(5, 1, 4);
    public static final BlockPos PODIUM_STAND_BENCH = new BlockPos(5, 1, 5);

    public static final Layout PODIUM_LAYOUT = new Layout("podium", 9, new Vec3i(9, 3, 9), p -> {
        floor(p, 9);
        p.place(PODIUM, facing(Orchestra.PODIUM.get(), Direction.SOUTH));
        p.place(PODIUM_PIANO, facing(Instruments.GRAND_PIANO.get(), Direction.NORTH));
        p.place(PODIUM_PIANO_BENCH, facing(Benches.BENCH.get(), Direction.SOUTH));
        p.place(PODIUM_CELLO, facing(Instruments.CELLO.get(), Direction.NORTH));
        p.place(PODIUM_STAND, facing(Instruments.MUSIC_STAND.get(), Direction.SOUTH));
        p.place(PODIUM_STAND_BENCH, facing(Benches.BENCH.get(), Direction.NORTH));
    });

    public static final List<Layout> ALL = List.of(BENCHES, MUSIC_STAND, POSTS_STRINGS, POSTS_MALLETS, TIMPANI, PIPE_ORGAN, PODIUM_LAYOUT);

    /** 方便测试和脚本用：一格的方块状态按朝向放（不补其余几格）。 */
    public static BlockState state(Block block, Direction facing) {
        return facing(block, facing);
    }

    static boolean isPipe(Block block) {
        return block instanceof OrganPipeBlock;
    }
}
