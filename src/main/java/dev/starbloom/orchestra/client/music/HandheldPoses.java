package dev.starbloom.orchestra.client.music;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.starbloom.orchestra.StarbloomOrchestra;
import dev.starbloom.orchestra.music.HandheldInstrumentItem;
import dev.starbloom.orchestra.music.InstrumentKind;
import dev.starbloom.orchestra.music.InstrumentSeat;
import dev.starbloom.orchestra.music.Instruments;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.IArmPoseTransformer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 手持乐器的演奏姿势：只在「使用中」（举起来演奏）时接管双臂和乐器的画法，平时完全原版（docs/COMBAT_SKELETON.md 第 1 节）。
 * 「使用中」本来就会同步给附近的人，所以别人那里也看得到，不用另发包。
 * 乐器不再挂在手上：小提琴夹在下巴下（跟着身体），长笛、小号在嘴边（跟着头），由这里的玩家层按 {@link HeldRig}
 * 画出来（models/item/ 下单独加载的 *_held 模型，{@link HeldModel}），双臂再伸向乐器上的手位——小提琴是经典的样子，
 * 非惯用手托琴、惯用手运弓。两只手里原本拿的东西这时不画。
 * 最近 0.6 秒有音时弓来回拉、管乐器轻轻起伏，不弹时静止。姿势经 {@link HumanoidModel.ArmPose#create} 加进 {@link HumanoidModel.ArmPose}。
 *
 * <p>坐在大提琴、低音提琴后面（骑着 {@link InstrumentSeat}）时也在这里：乐器是方块、不画，只画琴弓；
 * 双腿分开夹着大提琴（低音提琴站着拉，腿伸直），左手按琴颈、右手运弓，那件乐器最近在响时弓来回拉（纸带、红石放的也算）。
 */
@Mod.EventBusSubscriber(modid = StarbloomOrchestra.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HandheldPoses {
    public static final HumanoidModel.ArmPose VIOLIN = HumanoidModel.ArmPose.create(
        "STARBLOOM_ORCHESTRA_VIOLIN", true, armPose(InstrumentKind.VIOLIN));
    public static final HumanoidModel.ArmPose FLUTE = HumanoidModel.ArmPose.create(
        "STARBLOOM_ORCHESTRA_FLUTE", true, armPose(InstrumentKind.FLUTE));
    public static final HumanoidModel.ArmPose TRUMPET = HumanoidModel.ArmPose.create(
        "STARBLOOM_ORCHESTRA_TRUMPET", true, armPose(InstrumentKind.TRUMPET));
    public static final HumanoidModel.ArmPose VIOLA = HumanoidModel.ArmPose.create(
        "STARBLOOM_ORCHESTRA_VIOLA", true, armPose(InstrumentKind.VIOLA));
    public static final HumanoidModel.ArmPose OBOE = HumanoidModel.ArmPose.create(
        "STARBLOOM_ORCHESTRA_OBOE", true, armPose(InstrumentKind.OBOE));
    public static final HumanoidModel.ArmPose BASSOON = HumanoidModel.ArmPose.create(
        "STARBLOOM_ORCHESTRA_BASSOON", true, armPose(InstrumentKind.BASSOON));
    public static final HumanoidModel.ArmPose SHINOBUE = HumanoidModel.ArmPose.create(
        "STARBLOOM_ORCHESTRA_SHINOBUE", true, armPose(InstrumentKind.SHINOBUE));
    public static final HumanoidModel.ArmPose KAGURA_SUZU = HumanoidModel.ArmPose.create(
        "STARBLOOM_ORCHESTRA_KAGURA_SUZU", true, armPose(InstrumentKind.KAGURA_SUZU));
    public static final HumanoidModel.ArmPose SHAKUHACHI = HumanoidModel.ArmPose.create(
        "STARBLOOM_ORCHESTRA_SHAKUHACHI", true, armPose(InstrumentKind.SHAKUHACHI));
    public static final HumanoidModel.ArmPose CELLO = HumanoidModel.ArmPose.create(
        "STARBLOOM_ORCHESTRA_CELLO", true, armPose(InstrumentKind.CELLO));
    public static final HumanoidModel.ArmPose DOUBLE_BASS = HumanoidModel.ArmPose.create(
        "STARBLOOM_ORCHESTRA_DOUBLE_BASS", true, armPose(InstrumentKind.DOUBLE_BASS));

    /**
     * 两手姿势（双手，原版只为拿着乐器的那只手调用）。1.21.1 的 ArmPose 只有「是否双手」一个开关：使用物品时原版本来就只摆
     * 用着的那只手，不用 26.1 的「不再摆另一只手」。
     * 只在正画着这个人时摆（{@link MusicianPoses#drawing}）：1.21.1 第一人称画手时也会走一遍 setupAnim，那时不动（26.1 画手不走 setupAnim）。
     */
    private static IArmPoseTransformer armPose(InstrumentKind kind) {
        return (model, entity, arm) -> {
            if (MusicianPoses.drawing(entity) == null) return;
            standingBase(model);
            pose(kind, model, entity);
        };
    }

    /**
     * 两手姿势摆的时候，原版还没写这一帧的蹲姿：1.21.1 的 setupAnim 不先复位模型，身体、头、肩的高度和身体前倾还是上一次
     * （可能是另一个人）画完的样子，要等摆完手臂才按蹲没蹲重写。所以先写成站着的值（同原版随后不蹲时写的，也同 26.1 复位后的值），
     * 姿势照 26.1 那样按站着算、再把蹲下的偏移算进去；原版随后照样按蹲没蹲覆盖这几个值，画出来不受影响。
     */
    static void standingBase(HumanoidModel<?> model) {
        model.body.xRot = 0;
        model.body.y = 0;
        model.head.y = 0;
        model.rightArm.y = 2;
        model.leftArm.y = 2;
    }

    private static HumanoidModel.ArmPose poseOf(InstrumentKind kind) {
        return switch (kind) {
            case VIOLIN -> VIOLIN;
            case FLUTE -> FLUTE;
            case VIOLA -> VIOLA;
            case OBOE -> OBOE;
            case BASSOON -> BASSOON;
            case SHINOBUE -> SHINOBUE;
            case KAGURA_SUZU -> KAGURA_SUZU;
            case SHAKUHACHI -> SHAKUHACHI;
            case CELLO -> CELLO;
            case DOUBLE_BASS -> DOUBLE_BASS;
            default -> TRUMPET;
        };
    }

    /** 正在演奏的乐器：种类、要画的模型（弓弦乐器另有弓；坐着拉的乐器是方块，instrument 为 null）、最近是否在弹。 */
    /** bow：弓弦乐器的弓；工位上敲的乐器借这一格放琴槌（{@link StationPoses}）。 */
    public record Held(InstrumentKind kind, @Nullable HeldModel instrument, @Nullable HeldModel bow, float move) {}

    /** 画乐器用的模型：models/item/ 下拿在手上演奏的那一份（小提琴不带弓）和 hexen_violin_bow。 */
    private static final Map<InstrumentKind, HeldModel> BODIES = new EnumMap<>(InstrumentKind.class);
    private static @Nullable HeldModel bow;
    private static final IClientItemExtensions ITEM_EXT = new IClientItemExtensions() {
        @Override
        public HumanoidModel.@Nullable ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            if (!entity.isUsingItem() || entity.getUsedItemHand() != hand) return null;
            if (!(stack.getItem() instanceof HandheldInstrumentItem item)) return null;
            return poseOf(item.kind());
        }
    };

    private HandheldPoses() {}

    public static void initClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(ITEM_EXT);
    }

    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Instruments.SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(Instruments.STANDING_POST.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(dev.starbloom.orchestra.music.Benches.SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
    }

    @SubscribeEvent
    static void layers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            EntityRenderer<? extends Player> renderer = event.getSkin(skin);
            if (renderer instanceof PlayerRenderer player) player.addLayer(new Layer(player));
        }
    }

    /**
     * 画一个玩家之前（{@link MusicianPoses#beforeRender}，这时玩家渲染器已经按手里的物品定下两手姿势）：记下这一帧怎么演奏、要画的乐器。
     */
    static void prepare(AbstractClientPlayer entity, PlayerModel<?> model, MusicianPoses.Drawing d) {
        // 坐在星辰花琴凳上：手持乐器、键盘、竖琴、定音鼓都按琴凳那一套来（同原版人形生物、附属 NPC）；
        // 站在钟琴、管钟前的工位上也是（拿槌敲，{@link StationPoses}）
        if (entity.getVehicle() instanceof dev.starbloom.orchestra.music.BenchSeat
            || entity.getVehicle() instanceof InstrumentSeat post && post.kind() != null && !post.kind().seated()) {
            MusicianPoses.prepare(entity, d);
            return;
        }
        if (entity.getVehicle() instanceof InstrumentSeat seat && seat.kind() != null) {
            seated(model, d, seat);
            return;
        }
        if (!entity.isUsingItem() || !(entity.getUseItem().getItem() instanceof HandheldInstrumentItem item)) return;
        // 乐器由下面的层画在下巴、嘴边；两只手原本拿的（包括乐器本身）都不画
        d.hideHandItems = true;
        boolean playing = MusicClient.recentlyPlayed(entity.getId()) || VoiceBank.holding(MusicClient.heldKey(entity.getId()));
        d.held = heldFor(item.kind(), entity, playing ? 1 : 0);
    }

    /** 要画的乐器（和弓）：模型换成 models/item/ 下拿在手上演奏的那一份。 */
    public static Held heldFor(InstrumentKind kind, LivingEntity entity, float move) {
        return new Held(kind, body(kind), kind == InstrumentKind.VIOLIN || kind == InstrumentKind.VIOLA ? bow() : null, move);
    }

    /** 坐（站）在大提琴、低音提琴后：只有弓要画（乐器是方块）。 */
    public static Held bowFor(InstrumentKind kind, LivingEntity entity, float move) {
        return new Held(kind, null, bow(), move);
    }

    /** 坐在弦乐器后面：乐器是方块，只画弓；手里原本拿的不画；姿势挂在右臂上（按右手运弓布置，左撇子也一样）。 */
    private static void seated(PlayerModel<?> model, MusicianPoses.Drawing d, InstrumentSeat seat) {
        InstrumentKind kind = seat.kind();
        d.hideHandItems = true;
        model.rightArmPose = poseOf(kind);
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        d.held = new Held(kind, null, bow(), VoiceBank.recentlyPlayed(seat.instrument()) ? 1 : 0);
    }

    private static HeldModel body(InstrumentKind kind) {
        return BODIES.computeIfAbsent(kind, k -> switch (k) {
            case VIOLIN -> HeldModel.of(new ItemStack(Instruments.VIOLIN.get()), "hexen_violin_held");
            case FLUTE -> HeldModel.of(new ItemStack(Instruments.FLUTE.get()), "hexen_flute_held");
            case VIOLA -> HeldModel.of(new ItemStack(Instruments.VIOLA.get()), "hexen_viola_held");
            case OBOE -> HeldModel.of(new ItemStack(Instruments.OBOE.get()), "hexen_oboe_held");
            case BASSOON -> HeldModel.of(new ItemStack(Instruments.BASSOON.get()), "hexen_bassoon_held");
            case SHINOBUE -> HeldModel.of(new ItemStack(Instruments.SHINOBUE.get()), "shinobue_held");
            case KAGURA_SUZU -> HeldModel.of(new ItemStack(Instruments.KAGURA_SUZU.get()), "kagura_suzu_held");
            case SHAKUHACHI -> HeldModel.of(new ItemStack(Instruments.SHAKUHACHI.get()), "shakuhachi_held");
            default -> HeldModel.of(new ItemStack(Instruments.TRUMPET.get()), "hexen_trumpet_held");
        });
    }

    private static HeldModel bow() {
        if (bow == null) bow = HeldModel.of(new ItemStack(Instruments.VIOLIN.get()), "hexen_violin_bow");
        return bow;
    }

    /** 双臂伸向乐器上的两个手位（原版只为拿着乐器的那只手调用两手姿势，所以两只手臂都在这里摆，同原版拉弓）。 */
    static void pose(InstrumentKind kind, HumanoidModel<?> model, LivingEntity entity) {
        MusicianPoses.Drawing d = MusicianPoses.drawing(entity);
        float t = d == null ? entity.tickCount : d.ageInTicks;
        Held held = d == null ? null : d.held;
        if (kind.seated()) {
            seatedPose(kind, model, t, held);
            return;
        }
        float move = held == null ? 0f : held.move();
        boolean leftHanded = entity.getMainArm() == HumanoidArm.LEFT;
        boolean crouching = model.crouching;
        HeldRig.Place place = HeldRig.place(kind, model, crouching, true, leftHanded, t, move);
        // 蹲下时原版在这之后把肩下移 3.2、手臂再往前抬 0.4，这里先扣掉
        float drop = crouching ? 3.2f : 0, lift = crouching ? 0.4f : 0;
        reach(leftHanded ? model.leftArm : model.rightArm, place.mainHand(), drop, lift, leftHanded ? 1 : -1);
        reach(leftHanded ? model.rightArm : model.leftArm, place.offHand(), drop, lift, leftHanded ? -1 : 1);
    }

    /** 坐（站）在弦乐器后面：右手运弓、左手按琴颈；大提琴双腿分开夹着琴，低音提琴站着拉、腿伸直。 */
    private static void seatedPose(InstrumentKind kind, HumanoidModel<?> model, float t, @Nullable Held held) {
        HeldRig.Place place = HeldRig.seated(kind, model, t, held == null ? 0 : held.move());
        reach(model.rightArm, place.mainHand(), 0, 0, -1);
        reach(model.leftArm, place.offHand(), 0, 0, 1);
        if (kind == InstrumentKind.DOUBLE_BASS) {
            model.rightLeg.xRot = 0;
            model.leftLeg.xRot = 0;
            model.rightLeg.yRot = 0.1f;
            model.leftLeg.yRot = -0.1f;
        } else {
            // 琴往左倾，琴身偏在左膝一侧：右腿分得少一点，两膝都贴着琴
            model.rightLeg.xRot = -1.35f;
            model.leftLeg.xRot = -1.35f;
            model.rightLeg.yRot = 0.42f;
            model.leftLeg.yRot = -0.5f;
        }
        model.rightLeg.zRot = 0;
        model.leftLeg.zRot = 0;
    }

    /**
     * 手臂指向手位。原版手臂沿自身 +Y 垂下，先绕 X 再绕 Y 转（ModelPart 按 ZYX 合成），所以方向
     * (sin x·sin y, cos x, sin x·cos y) 反解得 x、y。手臂中线在支点外侧 1 像素（side）。
     */
    static void reach(ModelPart arm, Vector3f hand, float drop, float lift, float side) {
        float[] r = reachAngles(arm.x, arm.y, arm.z, hand, drop, lift, side);
        if (r == null) return;
        arm.xRot = r[0];
        arm.yRot = r[1];
        arm.zRot = 0;
    }

    /**
     * {@link #reach} 的算法：支点在 (x, y, z) 的手臂指向 hand 时的 {xRot, yRot}（zRot 为 0），手位离支点太近时为 null。
     * 不是原版 ModelPart 的骨骼（车万女仆基岩版模型的部件，{@code MaidMusicians}）用这个。
     */
    static float @Nullable [] reachAngles(float x, float y, float z, Vector3f hand, float drop, float lift, float side) {
        float dx = hand.x - (x + side), dy = hand.y - (y + drop), dz = hand.z - z;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-3f) return null;
        return new float[]{-(float) Math.acos(Mth.clamp(dy / len, -1, 1)) - lift, (float) Math.atan2(-dx, -dz)};
    }

    /** 把乐器（和弓）画在 {@link HeldRig} 算好的位置上（同原版人形生物的那一层，{@link MusicianPoses#drawHeld}）。 */
    private static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            MusicianPoses.drawHeld(pose, buffers, light, getParentModel(), player, ageInTicks);
        }
    }

    /**
     * 把一件乐器（或弓）画在人形模型根坐标的 pixels 处（玩家和原版人形生物共用）。
     * 26.1 先走一遍模型的根部件（成年模型的根在原点、不转）；1.21.1 的人形模型没有根部件，成年模型的根坐标就是这一层的坐标，
     * 幼年模型照原版手持物品那一层把身体缩小一半。
     */
    static void drawOn(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> model, Matrix4f pixels, HeldModel item) {
        pose.pushPose();
        if (model.young) {
            pose.translate(0.0F, 0.75F, 0.0F);
            pose.scale(0.5F, 0.5F, 0.5F);
        }
        // HeldRig 用像素；物品模型以方块为单位、已居中
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        pose.mulPoseMatrix(pixels);
        pose.scale(16, 16, 16);
        item.render(pose, buffers, light);
        pose.popPose();
    }
}
