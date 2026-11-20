package dev.starbloom.orchestra.client.music;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.starbloom.orchestra.StarbloomOrchestra;
import dev.starbloom.orchestra.music.BenchSeat;
import dev.starbloom.orchestra.music.HandheldInstrumentItem;
import dev.starbloom.orchestra.music.InstrumentBlock;
import dev.starbloom.orchestra.music.InstrumentKind;
import dev.starbloom.orchestra.music.StarbloomBenchBlock;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * 坐在星辰花琴凳上演奏的人（docs/ORCHESTRA.md 第 14 节）：玩家、原版人形生物、附属 NPC 共用同一份手位。
 *
 * <ul>
 * <li>手持乐器：乐器和双手的位置来自 {@link HeldRig#place}，坐标是玩家模型根坐标（像素，以脖子为原点）。</li>
 * <li>键盘、竖琴：双手的位置写在琴凳坐标里（像素；琴凳朝北：x 向东、y 向上、z 向南，坐的人双脚在
 *     {@link StarbloomBenchBlock#FEET}），跟着这件乐器此刻在响的音走：低音左手、高音右手，弹下时手往下按一下。</li>
 * </ul>
 * 这两种坐标就是「手要到的位置」（IK 目标）：原版的人形模型手臂是直的，转过去指着它；NPC 的骨架有手肘，两段手臂正好够到
 * （{@link NpcMusician}）。以后别的骨架只要把这两种坐标换算到自己的骨骼上就能套用。
 * <p>
 * 1.21.1 没有渲染状态：画一个人之前（{@link RenderLivingEvent.Pre}）按实体算好这一帧的 {@link Drawing}，模型的 setupAnim
 * 之后（{@code MusicianModelMixin}）按它摆手臂、腿，乐器层按它画乐器；画完（{@link RenderLivingEvent.Post}）就去掉。
 */
@EventBusSubscriber(modid = StarbloomOrchestra.ID, value = Dist.CLIENT)
public final class MusicianPoses {
    /** HANDHELD 手持乐器；KEYS 键盘；HARP 竖琴；BOWED 大提琴、低音提琴琴后拉弓；MALLETS 钟琴、管钟、定音鼓拿槌敲（{@link StationPoses}）。 */
    public enum Mode { HANDHELD, KEYS, HARP, BOWED, MALLETS }

    /**
     * 一位演奏者此刻的样子。坐标都在「演奏者面朝北」的方块像素里（x 向东、y 向上、z 向南；琴凳上就是琴凳坐标）。
     * @param kind 手里的手持乐器，或琴凳关联的乐器、工位上的乐器
     * @param move 手持乐器、拉弓的琴在响时为 1（弓来回、乐器轻摆）
     * @param left 键盘、竖琴：左手的位置；敲的乐器：左手槌头的位置（像素）；手持乐器、拉弓时为 null，敲的乐器左手不拿槌时也为 null
     * @param right 同上，右手
     * @param feet 演奏者双脚的位置
     * @param standing 站着演奏（低音提琴、钟琴、管钟）
     * @param seat 坐着时凳面比双脚高多少像素；edge 凳面前沿在双脚前多少像素
     */
    public record Musician(Mode mode, InstrumentKind kind, float move, @Nullable Vector3f left, @Nullable Vector3f right, Vector3f feet,
                           boolean standing, float seat, float edge) {}

    /**
     * 这一帧正在画的这个人：在演奏的话是谁、怎么演奏、要画的乐器。26.1 记在渲染状态里；1.21.1 没有渲染状态，
     * 在 {@link RenderLivingEvent.Pre} 里按实体算好，挂在这个实体上直到 {@link RenderLivingEvent.Post}。两手姿势（ArmPose）、
     * {@code MusicianModelMixin}、乐器层都取这一份，同一帧里用的是同一份手位和乐器。只有经 {@code LivingEntityRenderer.render}
     * 正在画的玩家、原版人形生物（还有女仆，{@code MaidMusicians}）才有；第一人称画手不经过那里，所以不受影响（同 26.1）。
     */
    static final class Drawing {
        final float partialTick, ageInTicks;
        final int light;
        /** 不演奏时为 null。 */
        @Nullable Musician musician;
        /** 要画的乐器（和弓、琴槌）。 */
        HandheldPoses.@Nullable Held held;
        /** 两只手里原本拿着的东西这一帧不画（{@code MusicianHandItemMixin}；26.1 是清空渲染状态里两只手的物品）。 */
        boolean hideHandItems;
        /** 车万女仆（GeckoLib 模型）不在琴凳、工位上时手里拿着的手持乐器（{@code MaidMusicians}），别人都是 null。 */
        @Nullable InstrumentKind carried;

        Drawing(LivingEntity entity, float partialTick, int light) {
            this.partialTick = partialTick;
            // 同原版 LivingEntityRenderer.getBob：模型 setupAnim 的 ageInTicks
            this.ageInTicks = entity.tickCount + partialTick;
            this.light = light;
        }

        /** 身体此刻朝哪（度），同原版画这个人时插值出来的身体朝向（没骑着活物时）。 */
        float bodyRot(LivingEntity entity) {
            return Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        }
    }

    private static final Map<LivingEntity, Drawing> DRAWING = new IdentityHashMap<>();

    /** 这个实体此刻正在画、并且是演奏者（或者至少是会被摆姿势的人）时它的那一份；不在画时为 null。 */
    static @Nullable Drawing drawing(LivingEntity entity) {
        return DRAWING.get(entity);
    }

    /** 开始画这个实体（{@link RenderLivingEvent.Pre}）：记下这一帧的那一份，{@link RenderLivingEvent.Post} 时去掉。 */
    static void begin(LivingEntity entity, Drawing drawing) {
        // 画的时候抛了异常就收不到 Post，留下的旧条目在这里清掉
        if (DRAWING.size() > 256) DRAWING.clear();
        DRAWING.put(entity, drawing);
    }

    /** 这个实体这一帧不画手里原本拿着的东西（演奏时乐器另外画，或者两只手都在琴上）：{@code MusicianHandItemMixin} 用。 */
    public static boolean hidesHandItems(LivingEntity entity) {
        Drawing d = DRAWING.get(entity);
        return d != null && d.hideHandItems;
    }

    /**
     * 别的骨架在模型 setupAnim 前后摆演奏姿势（车万女仆的基岩版模型，装了女仆模组时由 {@code MaidMusicians} 登记）。
     * pose 是渲染器画这个人用的那一份，这时已经换到了模型的坐标（y 向下、脚在 24 像素）。
     */
    interface Poser {
        /** 模型 setupAnim 之前（{@code MusicianModelMixin}）。 */
        default void beforeSetupAnim(EntityModel<?> model, LivingEntity entity, PoseStack pose) {}

        /** setupAnim 之后；pose 为 null 时是套在外面的那几层衣服又 setupAnim 了一次（{@code MusicianDrawMixin}），整个人不要再挪。 */
        void afterSetupAnim(EntityModel<?> model, LivingEntity entity, @Nullable PoseStack pose);
    }

    private static final List<Poser> POSERS = new CopyOnWriteArrayList<>();

    static void addPoser(Poser poser) {
        POSERS.add(poser);
    }

    /** 一位键盘手、竖琴手两只手此刻在键盘上的位置（0 最低音、1 最高音，平滑地移过去）和按下的程度。 */
    static final class Hands {
        float low = 0.3f, high = 0.7f, lowPress, highPress;
        long last;
    }

    private static final Map<Integer, Hands> HANDS = new HashMap<>();
    /** 键盘一半以下归左手。 */
    private static final int SPLIT = 60;

    private MusicianPoses() {}

    /** 这位坐在琴凳上的人此刻怎么演奏；没坐在琴凳上、手里没有乐器又没关联乐器时为 null（只是坐着）。 */
    public static @Nullable Musician of(LivingEntity entity) {
        if (entity.getVehicle() instanceof dev.starbloom.orchestra.music.InstrumentSeat post) return StationPoses.post(entity, post);
        if (!(entity.getVehicle() instanceof BenchSeat seat)) return null;
        Level level = entity.level();
        BlockPos bench = seat.bench();
        BlockState benchState = level.getBlockState(bench);
        if (!(benchState.getBlock() instanceof StarbloomBenchBlock)) return null;
        BlockPos linked = StarbloomBenchBlock.linked(level, bench, benchState);
        if (linked == null) {
            if (!(entity.getMainHandItem().getItem() instanceof HandheldInstrumentItem item)) return null;
            boolean playing = VoiceBank.recentlyPlayed(bench) || MusicClient.recentlyPlayed(entity.getId())
                || VoiceBank.holding(MusicClient.heldKey(entity.getId()));
            return new Musician(Mode.HANDHELD, item.kind(), playing ? 1 : 0, null, null, feet(benchState, false), false, SEAT,
                (float) (StarbloomBenchBlock.FEET.z - StarbloomBenchBlock.FRONT_EDGE));
        }
        BlockState mainState = level.getBlockState(linked);
        if (!(mainState.getBlock() instanceof InstrumentBlock block)) return null;
        InstrumentKind kind = block.kind(mainState);
        if (kind == null) return null;
        Vector3f feet = feet(benchState, true);
        if (kind == InstrumentKind.TIMPANI) return StationPoses.timpani(level, bench, feet);
        Hands hands = hands(entity.getId(), kind, VoiceBank.notesAt(linked), SPLIT);
        Direction facing = benchState.getValue(StarbloomBenchBlock.FACING);
        Vector3f[] at = kind == InstrumentKind.CONCERT_HARP ? harp(hands, lateral(bench, facing, linked))
            : keys(kind, hands, keySpan(bench, facing, linked, mainState, block, kind), feet);
        return new Musician(kind == InstrumentKind.CONCERT_HARP ? Mode.HARP : Mode.KEYS, kind, 0, at[0], at[1], feet, false, SEAT,
            (float) (feet.z - StarbloomBenchBlock.FRONT_EDGE));
    }

    /** 琴凳的凳面比坐的人双脚高多少像素。 */
    private static final float SEAT = (float) (StarbloomBenchBlock.TOP - StarbloomBenchBlock.FEET.y);

    private static Vector3f feet(BlockState benchState, boolean linked) {
        var f = StarbloomBenchBlock.feet(benchState, linked);
        return new Vector3f((float) f.x, (float) f.y, (float) f.z);
    }

    /** 两只手在键盘上的位置（0–1）和按下的程度：左手跟着低音、右手跟着高音，平滑移过去；没音时回到各自的半边。 */
    static Hands hands(int id, InstrumentKind kind, List<int[]> notes, int split) {
        long now = System.nanoTime();
        Hands h = HANDS.computeIfAbsent(id, k -> new Hands());
        float dt = h.last == 0 ? 1 : Math.min(1, (now - h.last) / 1e9f);
        float lowSum = 0, highSum = 0, lowPress = 0, highPress = 0;
        int lowCount = 0, highCount = 0;
        float range = Math.max(1, kind.high() - kind.low());
        for (int[] n : notes) {
            float u = Math.max(0, Math.min(1, (n[0] - kind.low()) / range));
            float press = Math.max(0, 1 - n[1] / 120f);
            if (n[0] < split) {
                lowSum += u;
                lowCount++;
                lowPress = Math.max(lowPress, press);
            } else {
                highSum += u;
                highCount++;
                highPress = Math.max(highPress, press);
            }
        }
        float targetLow = lowCount > 0 ? lowSum / lowCount : 0.3f, targetHigh = highCount > 0 ? highSum / highCount : 0.7f;
        float k = 1 - (float) Math.exp(-Math.max(dt, 0) / 0.06f);
        h.low += (targetLow - h.low) * k;
        h.high += (targetHigh - h.high) * k;
        // 两只手不交叉
        if (h.high - h.low < 0.12f) {
            float mid = (h.low + h.high) / 2;
            h.low = mid - 0.06f;
            h.high = mid + 0.06f;
        }
        h.lowPress = lowPress;
        h.highPress = highPress;
        h.last = now;
        if (HANDS.size() > 256) HANDS.clear();
        return h;
    }

    /** 琴键顶面的高（像素，从地面算）：同 tools/instrument_models/keyboards.py、organ.py 里各件乐器的键盘。 */
    private static float keyTop(InstrumentKind kind) {
        return switch (kind) {
            case HARPSICHORD -> 12.2f;
            case CELESTA -> 11.2f;
            case PIPE_ORGAN -> 14.8f;
            default -> 13f;
        };
    }

    /** 键盘在琴凳坐标里的左右两端（像素）：只看挨着琴凳那一排的格子，两头各留出琴身的边。 */
    private static float[] keySpan(BlockPos bench, Direction facing, BlockPos main, BlockState mainState, InstrumentBlock block, InstrumentKind kind) {
        Direction right = facing.getClockWise();
        BlockPos front = bench.relative(facing);
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (BlockPos c : block.cells(mainState, main)) {
            int depth = (c.getX() - front.getX()) * facing.getStepX() + (c.getZ() - front.getZ()) * facing.getStepZ();
            if (depth != 0 || c.getY() != front.getY()) continue;
            int lateral = (c.getX() - bench.getX()) * right.getStepX() + (c.getZ() - bench.getZ()) * right.getStepZ();
            lo = Math.min(lo, lateral * 16);
            hi = Math.max(hi, lateral * 16 + 16);
        }
        if (lo > hi) return new float[]{2, 14};
        float inset = kind == InstrumentKind.HARPSICHORD || kind == InstrumentKind.CELESTA ? 1.5f : 2.5f;
        return new float[]{lo + inset, hi - inset};
    }

    /** 键盘：手搭在琴键靠外的一段上（离琴凳前沿 1.2 像素），左右按音高，弹下时往下按。手伸得到的范围以内。 */
    private static Vector3f[] keys(InstrumentKind kind, Hands h, float[] span, Vector3f feet) {
        float y = keyTop(kind) + 1.0f;
        float xl = Math.max(feet.x - 12, Math.min(feet.x + 12, span[0] + h.low * (span[1] - span[0])));
        float xr = Math.max(feet.x - 12, Math.min(feet.x + 12, span[0] + h.high * (span[1] - span[0])));
        return new Vector3f[]{new Vector3f(xl, y - 0.9f * h.lowPress, -1.2f), new Vector3f(xr, y - 0.9f * h.highPress, -1.2f)};
    }

    /** 竖琴琴弦所在的平面（琴凳坐标的 z，像素）：琴在琴凳正前方一格，弦在那一格朝北放置时 z = 13（tools/instrument_models/strings.py 的 HARP_SHIFT）。 */
    public static final float HARP_STRINGS_Z = 13 - 16;
    /** 手在琴弦上左右走的范围（琴那一格里的 x，像素）：最低音弦在 x = 5.9；最右边几根高音短弦挂在肩膀上方，手只走到 11.2。 */
    private static final float HARP_LOW_X = 6.1f, HARP_HIGH_X = 11.2f;

    /** main 那一格比琴凳主块往演奏者右手边偏多少像素（单人琴凳正前方的乐器为 0）。 */
    private static float lateral(BlockPos bench, Direction facing, BlockPos main) {
        Direction right = facing.getClockWise();
        return 16 * ((main.getX() - bench.getX()) * right.getStepX() + (main.getZ() - bench.getZ()) * right.getStepZ());
    }

    /**
     * 竖琴：琴弦平面左右立在前面一格、正对演奏者，低音在左；弦的下端沿共鸣箱往右斜升（x 每往右 1 像素高 2.74），越往右弦越短、越高。
     * 两只手按音高在弦上左右移，停在弦的中段偏上（左手 15、右手往上到 19 像素，不低于弦下端 1.5 像素），手心停在弦前 0.6 像素
     * （手指搭在弦上），拨弦时往回一勾。
     */
    private static Vector3f[] harp(Hands h, float dx) {
        return new Vector3f[]{harpHand(h.low, h.lowPress, dx), harpHand(h.high, h.highPress, dx)};
    }

    private static Vector3f harpHand(float u, float press, float dx) {
        float x = HARP_LOW_X + u * (HARP_HIGH_X - HARP_LOW_X);
        float bottom = 4.3f + 2.74f * (x - 5.9f);
        return new Vector3f(dx + x, Math.max(15 + 4 * u, bottom + 1.5f), HARP_STRINGS_Z + 0.6f + 1.0f * press);
    }

    /**
     * 原版的手臂是直的、没有手肘：槌头要落的点常常比一臂还近，直接指过去手会越过它、槌杆朝外翻。
     * 所以手臂指向槌头上方 5、往回 3 像素的地方（模型像素：y 向下、z 向后），槌从手里斜着落到槌头的位置。
     */
    static Vector3f malletAim(Vector3f head) {
        return new Vector3f(head.x, head.y - 5, head.z + 3);
    }

    /** 琴凳坐标（像素）→ 人形模型根坐标（像素），以坐的人双脚为准。 */
    static Vector3f benchToModel(Vector3f bench, Vector3f feet) {
        return HeldRig.toModel(bench, feet);
    }

    // —— 原版人形模型（玩家、僵尸、骷髅……）——

    /**
     * 记下这位演奏者（{@link Drawing#musician}）；在演奏时记下要画的乐器（{@link Drawing#held}），并且不画手里原本拿着的物品。
     * 玩家经 {@link HandheldPoses#prepare}、原版人形生物经下面的 {@link #beforeRender} 进来。
     */
    static void prepare(LivingEntity entity, Drawing d) {
        Musician m = of(entity);
        d.musician = m;
        if (m == null) return;
        d.hideHandItems = true;
        d.held = switch (m.mode()) {
            case HANDHELD -> HandheldPoses.heldFor(m.kind(), entity, m.move());
            case BOWED -> HandheldPoses.bowFor(m.kind(), entity, m.move());
            case MALLETS -> new HandheldPoses.Held(m.kind(), null, StationPoses.tool(m.kind(), entity), 0);
            default -> null;
        };
    }

    /**
     * 画一个实体之前（排在最后，被别人取消了就不算）：玩家（两种皮肤）和原版人形生物记下这一帧怎么演奏。
     * 这时玩家渲染器已经按手里的物品定下两手姿势，坐在大提琴后的玩家在这里改成拉弓（{@link HandheldPoses#prepare}）。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void beforeRender(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        Drawing d = new Drawing(entity, event.getPartialTick(), event.getPackedLight());
        if (event.getRenderer() instanceof PlayerRenderer renderer && entity instanceof AbstractClientPlayer player) {
            HandheldPoses.prepare(player, renderer.getModel(), d);
        } else if (event.getRenderer() instanceof HumanoidMobRenderer<?, ?> && entity instanceof Mob) {
            prepare(entity, d);
        } else {
            return;
        }
        begin(entity, d);
    }

    @SubscribeEvent
    static void afterRender(RenderLivingEvent.Post<?, ?> event) {
        DRAWING.remove(event.getEntity());
    }

    /**
     * 渲染器里模型的 setupAnim 跑完之后（{@code MusicianModelMixin}，还有原版那几层套在外面的衣服 {@code MusicianDrawMixin}）：
     * 原版人形模型按 {@link #afterSetupAnim} 摆，别的骨架交给登记过的（女仆）。
     */
    public static void afterModelSetup(EntityModel<?> model, LivingEntity entity) {
        afterModelSetup(model, entity, null);
    }

    /** 同上，pose 是渲染器画身体用的那一份（{@code MusicianModelMixin}），别的骨架可以用它挪整个人（女仆坐的高度）。 */
    public static void afterModelSetup(EntityModel<?> model, LivingEntity entity, @Nullable PoseStack pose) {
        if (DRAWING.get(entity) == null) return;
        if (model instanceof HumanoidModel<?> humanoid) {
            afterSetupAnim(humanoid, entity);
        } else {
            for (Poser poser : POSERS) poser.afterSetupAnim(model, entity, pose);
        }
    }

    /** 渲染器里模型的 setupAnim 之前（{@code MusicianModelMixin}）：只交给登记过的别的骨架（原版人形模型不用）。 */
    public static void beforeModelSetup(EntityModel<?> model, LivingEntity entity, PoseStack pose) {
        if (DRAWING.get(entity) == null || model instanceof HumanoidModel<?>) return;
        for (Poser poser : POSERS) poser.beforeSetupAnim(model, entity, pose);
    }

    /**
     * 原版在 setupAnim 里摆完姿势之后（{@code MusicianModelMixin}）：双臂伸向手位。放在最后是因为僵尸之类的模型会在 setupAnim
     * 末尾再抬一次手，在这之前摆的会被盖掉。
     */
    public static void afterSetupAnim(HumanoidModel<?> model, LivingEntity entity) {
        Drawing d = DRAWING.get(entity);
        Musician m = d == null ? null : d.musician;
        if (m == null) return;
        boolean leftHanded = entity.getMainArm() == HumanoidArm.LEFT;
        if (m.mode() == Mode.HANDHELD) {
            HeldRig.Place place = HeldRig.place(m.kind(), model, false, true, leftHanded, d.ageInTicks, m.move());
            HandheldPoses.reach(leftHanded ? model.leftArm : model.rightArm, place.mainHand(), 0, 0, leftHanded ? 1 : -1);
            HandheldPoses.reach(leftHanded ? model.rightArm : model.leftArm, place.offHand(), 0, 0, leftHanded ? -1 : 1);
        } else if (m.mode() == Mode.BOWED) {
            // 同玩家坐在大提琴后：右手运弓、左手按琴颈，腿夹着琴（低音提琴站着）
            HandheldPoses.pose(m.kind(), model, entity);
        } else if (m.mode() == Mode.MALLETS) {
            for (boolean left : new boolean[]{true, false}) {
                Vector3f target = left ? m.left() : m.right();
                var arm = left ? model.leftArm : model.rightArm;
                if (target == null) {
                    arm.xRot = 0;
                    arm.yRot = 0;
                    arm.zRot = left ? -0.05f : 0.05f;
                } else {
                    HandheldPoses.reach(arm, malletAim(benchToModel(target, m.feet())), 0, 0, left ? 1 : -1);
                }
            }
            if (m.standing()) {
                model.rightLeg.xRot = 0;
                model.leftLeg.xRot = 0;
                model.rightLeg.yRot = 0.08f;
                model.leftLeg.yRot = -0.08f;
            } else {
                model.rightLeg.xRot = -0.8f;
                model.leftLeg.xRot = -0.8f;
                model.rightLeg.yRot = 0.12f;
                model.leftLeg.yRot = -0.12f;
            }
            model.rightLeg.zRot = 0;
            model.leftLeg.zRot = 0;
        } else if (m.left() != null && m.right() != null) {
            HandheldPoses.reach(model.leftArm, benchToModel(m.left(), m.feet()), 0, 0, 1);
            HandheldPoses.reach(model.rightArm, benchToModel(m.right(), m.feet()), 0, 0, -1);
            // 原版的腿没有膝盖：坐在乐器前时往前下方斜伸（原版骑乘那样平伸会插进琴身）；竖琴的底座离琴凳前沿只有 3 像素，
            // 腿再往下垂一些，脚落在底座后面的踏板那里（斜伸 0.8 会从琴身里穿过去）
            float legs = m.mode() == Mode.HARP ? -0.5f : -0.8f;
            model.rightLeg.xRot = legs;
            model.leftLeg.xRot = legs;
            model.rightLeg.yRot = 0.12f;
            model.leftLeg.yRot = -0.12f;
            model.rightLeg.zRot = 0;
            model.leftLeg.zRot = 0;
        }
        if (model instanceof PlayerModel<?> player) copyOverlays(player);
    }

    /**
     * 1.21.1 的玩家模型里袖子、裤腿是单独的部件，原版在 setupAnim 末尾照着手臂、腿抄一遍；在那之后才摆的手臂、腿要再抄一遍
     * （26.1 的袖子、裤腿长在手臂、腿上，自己就跟着）。外套跟着身体，身体没动。
     */
    private static void copyOverlays(PlayerModel<?> model) {
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void layers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : event.getEntityTypes()) {
            if (event.getRenderer(type) instanceof HumanoidMobRenderer renderer) renderer.addLayer(new MobLayer(renderer));
        }
    }

    /**
     * 原版人形模型（玩家、生物）手里的琴槌：手在肩指向 {@link #malletAim} 的那条线上、一臂远（手心离肩 9 像素），
     * 槌从手里伸到槌头该落的点。
     */
    static void drawMallets(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> model, Musician m, @Nullable HeldModel tool) {
        if (tool == null) return;
        for (boolean left : new boolean[]{true, false}) {
            Vector3f target = left ? m.left() : m.right();
            if (target == null) continue;
            Vector3f t = benchToModel(target, m.feet());
            var arm = left ? model.leftArm : model.rightArm;
            Vector3f shoulder = new Vector3f(arm.x + (left ? 1 : -1), arm.y, arm.z);
            Vector3f hand = malletAim(t).sub(shoulder).normalize().mul(9).add(shoulder);
            HandheldPoses.drawOn(pose, buffers, light, model,
                HeldRig.along(hand, t, StationPoses.toolGrip(m.kind()), StationPoses.toolHead(m.kind())), tool);
        }
    }

    /**
     * 玩家、原版人形生物这一帧要画的乐器（和弓、琴槌）：画在 {@link HeldRig} 按已经摆好的模型算出的位置上（两种人共用的那一层）。
     * ageInTicks 是渲染器交给这一层的（同 setupAnim 用的）。
     */
    static void drawHeld(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> model, LivingEntity entity, float ageInTicks) {
        Drawing d = DRAWING.get(entity);
        HandheldPoses.Held held = d == null ? null : d.held;
        if (held == null || entity.isInvisible()) return;
        Musician m = d.musician;
        if (m != null && m.mode() == Mode.MALLETS) {
            // 钟琴、管钟、定音鼓、打击乐：琴槌画在手上
            drawMallets(pose, buffers, light, model, m, held.bow());
            return;
        }
        HeldRig.Place place = held.kind().seated() ? HeldRig.seated(held.kind(), model, ageInTicks, held.move())
            : HeldRig.place(held.kind(), model, model.crouching, false, entity.getMainArm() == HumanoidArm.LEFT, ageInTicks, held.move());
        if (held.instrument() != null && place.instrument() != null) HandheldPoses.drawOn(pose, buffers, light, model, place.instrument(), held.instrument());
        if (held.bow() != null && place.bow() != null) HandheldPoses.drawOn(pose, buffers, light, model, place.bow(), held.bow());
    }

    /** 原版人形生物手里的乐器（和弓）：画在 {@link HeldRig} 算好的位置上（同玩家的那一层）。 */
    private static final class MobLayer<T extends Mob, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
        MobLayer(RenderLayerParent<T, M> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, T mob, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            drawHeld(pose, buffers, light, getParentModel(), mob, ageInTicks);
        }
    }
}
