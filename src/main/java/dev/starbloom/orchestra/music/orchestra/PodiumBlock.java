package dev.starbloom.orchestra.music.orchestra;

import com.mojang.serialization.MapCodec;
import dev.starbloom.orchestra.music.InstrumentShapes;
import dev.starbloom.orchestra.music.Instruments;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 指挥台（docs/ORCHESTRA.md 第 3 节）：两格宽、一格深，台面高半格。FACING 是指挥面朝的方向（朝着乐团，放置时玩家面对的方向）；
 * 主块在左、侧块在指挥的右手边。模型和方块实体都在主块上，拆任意一块整座拆掉，只掉一座。
 * 空手右键打开指挥台界面；拿着纸带右键放进（换进）总谱；拿着指挥棒右键：没绑定这座时绑定，绑定了就开始 / 停止演奏；
 * 红石上升沿开始演奏。
 */
public class PodiumBlock extends Block implements EntityBlock {
    public static final MapCodec<PodiumBlock> CODEC = simpleCodec(PodiumBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /** 是不是指挥右手边那一格。 */
    public static final BooleanProperty SIDE = BooleanProperty.create("side");
    /** 谱架上有没有总谱（模型摊开一本谱子）；只看主块的。 */
    public static final BooleanProperty SCORE = BooleanProperty.create("score");
    private static final Map<Direction, VoxelShape> SHAPES = InstrumentShapes.rotateHorizontal(Block.box(0, 0, 0, 16, 8, 16));

    public PodiumBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SIDE, false).setValue(SCORE, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    public static boolean isMain(BlockState state) {
        return !state.getValue(SIDE);
    }

    /** 指挥的右手边。 */
    public static Direction right(Direction facing) {
        return facing.getClockWise();
    }

    public static BlockPos mainPos(BlockState state, BlockPos pos) {
        return isMain(state) ? pos : pos.relative(right(state.getValue(FACING)).getOpposite());
    }

    public static List<BlockPos> cells(BlockState mainState, BlockPos main) {
        return List.of(main, main.relative(right(mainState.getValue(FACING))));
    }

    /** 指挥站的位置：台面中央（两格交界处），台面高半格。 */
    public static Vec3 standAt(BlockState mainState, BlockPos main) {
        Direction r = right(mainState.getValue(FACING));
        return Vec3.atBottomCenterOf(main).add(r.getStepX() * 0.5, 0.5, r.getStepZ() * 0.5);
    }

    /** 玩家脚下的指挥台（主块）；不站在指挥台上时为 null。 */
    public static @Nullable BlockPos under(Player player) {
        Level level = player.level();
        for (double dy : new double[]{-0.01, -0.51}) {
            BlockPos p = BlockPos.containing(player.getX(), player.getY() + dy, player.getZ());
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof PodiumBlock) return mainPos(s, p);
        }
        return null;
    }

    /** 指挥状态：站在指挥台上、主手拿着指挥棒。这时左键不攻击、不挖方块（不会误伤乐手、拆掉乐器）。 */
    public static boolean conducting(Player player) {
        return player.getMainHandItem().is(Orchestra.BATON.get()) && under(player) != null;
    }

    public static @Nullable PodiumBlockEntity entityAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PodiumBlock)) return null;
        return level.getBlockEntity(mainPos(state, pos)) instanceof PodiumBlockEntity be ? be : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return isMain(state) ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMain(state) ? new PodiumBlockEntity(pos, state) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || !isMain(state) || type != Orchestra.PODIUM_ENTITY.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<PodiumBlockEntity>) PodiumBlockEntity::serverTick;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos side = context.getClickedPos().relative(right(facing));
        Level level = context.getLevel();
        if (!level.getBlockState(side).canBeReplaced(context) || !level.getWorldBorder().isWithinBounds(side)) return null;
        return defaultBlockState().setValue(FACING, facing).setValue(SIDE, false).setValue(SCORE, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        level.setBlock(pos.relative(right(state.getValue(FACING))), state.setValue(SIDE, true).setValue(SCORE, false), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        boolean side = state.getValue(SIDE);
        Direction toSibling = side ? right(facing).getOpposite() : right(facing);
        if (direction == toSibling && !(neighborState.is(this) && neighborState.getValue(SIDE) != side
                && neighborState.getValue(FACING) == facing)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && !isMain(state)) {
            BlockPos main = mainPos(state, pos);
            BlockState mainState = level.getBlockState(main);
            if (mainState.is(this) && isMain(mainState)) {
                level.setBlock(main, Blocks.AIR.defaultBlockState(), Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_ALL);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * 换成别的方块（拆掉、被炸、被替换）时先让方块实体收尾（停奏、放开成员、掉出总谱），再按原版移除方块实体。
     * 1.21.1 的方块实体还没有 {@code preRemoveSideEffects}，原版的讲台、唱片机也是在这里做的；只在服务端调用。
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide() && level.getBlockEntity(pos) instanceof PodiumBlockEntity be) {
            be.preRemoveSideEffects(pos, state);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** 成功一律用 sidedSuccess 的写法：客户端 SUCCESS（挥手），服务端 CONSUME（不再广播挥手）。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) dev.starbloom.orchestra.client.music.Conducting.openPodium(mainPos(state, pos));
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        BlockPos main = mainPos(state, pos);
        if (stack.is(Orchestra.BATON.get())) {
            if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
            Orchestra.batonOnPodium(stack, (ServerLevel) level, main, player);
            return ItemInteractionResult.CONSUME;
        }
        if (!Instruments.isRoll(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(main) instanceof PodiumBlockEntity be)) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (be.isPlaying()) {
            Orchestra.tell(player, "busy");
            return ItemInteractionResult.CONSUME;
        }
        ItemStack old = be.takeScore();
        be.setScore(stack.copyWithCount(1));
        if (!player.getAbilities().instabuild) stack.shrink(1);
        if (!old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighborPos,
                                   boolean movedByPiston) {
        if (level.isClientSide()) return;
        BlockPos main = mainPos(state, pos);
        BlockState mainState = level.getBlockState(main);
        if (!(mainState.getBlock() instanceof PodiumBlock) || !(level.getBlockEntity(main) instanceof PodiumBlockEntity be)) return;
        boolean signal = false;
        for (BlockPos cell : cells(mainState, main)) signal |= level.hasNeighborSignal(cell);
        be.onSignal(signal);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SIDE, SCORE);
    }
}
