package dev.starbloom.orchestra.client.music;

import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart;
import com.github.tartaricacid.touhoulittlemaid.api.animation.IAnimation;
import com.github.tartaricacid.touhoulittlemaid.api.event.client.AddMaidLayerEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.client.DefaultGeckoAnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.EntityMaidModel;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.state.EntityMaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.state.ModelType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.starbloom.orchestra.StarbloomOrchestra;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * 车万女仆坐上星辰花琴凳、站到乐器工位上时的演奏姿势（docs/ORCHESTRA.md 第 16 节）。只在装了车万女仆时编译、注册
 * （{@code StarbloomOrchestra} 按名字找到 {@link #init}），用的都是女仆模组公开的接口：
 *
 * <ul>
 * <li><b>基岩版模型</b>（女仆模组自带的模型都是）：在渲染状态里给这只女仆的动画列表末尾加一段 {@link IAnimation}，
 *     在女仆模组自己的动画之后摆双臂、双腿、整个人的高度；手位就是玩家和其他生物用的那一份（{@link MusicianPoses.Musician}，
 *     「演奏者面朝北」的方块像素），这里换算到女仆模型的像素（模型按 {@code render_entity_scale} 缩放）。
 *     女仆的手臂和原版一样是直的、从肩上的支点垂下，所以沿用原版的转法（{@link HandheldPoses#reach}）。
 *     手持乐器、琴弓、琴槌由 {@link AddMaidLayerEvent.Legacy} 加的一层画出来。</li>
 * <li><b>GeckoLib 模型</b>：骨骼由女仆模组在别的线程里算好，没有公开的办法在那之后改。这里用
 *     {@link DefaultGeckoAnimationEvent} 加默认动作：按骑着的实体（{@code vehicle#starbloom_orchestra:bench_seat} 琴凳上弹、
 *     {@code instrument_seat} 坐着拉大提琴、{@code standing_post} 站着拉、敲；按同名的实体标签），再按手里的手持乐器
 *     （{@code hold_mainhand#starbloom_orchestra:maid_pose/...}，物品标签）摆双臂，坐着时拉弓；手不跟着音走，模型包自己写了同名动作时用模型包的。
 *     乐器、琴弓、琴槌由 {@link AddMaidLayerEvent.Gecko} 加的一层按手的挂点和头骨画上去（{@link GeckoLayer}）。</li>
 * </ul>
 */
public final class MaidMusicians {
    /** 基岩版女仆模型手臂从肩上的支点到手心（模型像素）：模板的手臂长 8。 */
    private static final float GRIP = 7;
    /** 大腿一半的粗（模型像素）：模板的腿 3 宽。 */
    private static final float THIGH_HALF = 1.5f;

    /** 测试用（{@link MaidHallViews}）：基岩版模型的演奏姿势摆过几次。 */
    public static int posed;

    private MaidMusicians() {}

    /** 测试用：一只女仆，model 为 null 时是默认模型。 */
    public static net.minecraft.world.entity.Mob testMaid(net.minecraft.server.level.ServerLevel level, @Nullable String model) {
        EntityMaid maid = EntityMaid.TYPE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (maid == null) throw new IllegalStateException("cannot create a maid");
        if (model != null) maid.setModelId(model);
        return maid;
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(MaidMusicians::modifiers);
        NeoForge.EVENT_BUS.addListener(MaidMusicians::legacyLayer);
        NeoForge.EVENT_BUS.addListener(MaidMusicians::geckoLayer);
        NeoForge.EVENT_BUS.addListener(MaidMusicians::geckoClips);
    }

    private static void modifiers(RegisterRenderStateModifiersEvent event) {
        event.<EntityMaid, EntityMaidRenderState>registerEntityModifier(EntityMaidRenderer.class, MaidMusicians::prepare);
    }

    /**
     * 女仆模组提取完渲染状态之后：记下演奏者和要画的乐器、琴弓、琴槌，手里原本的东西不画（两种模型都由我们这边的层画）；
     * 基岩版模型再在动画列表末尾加上 {@link #POSE}，GeckoLib 模型的手臂是默认动作（见类说明）。
     */
    private static void prepare(EntityMaid maid, EntityMaidRenderState state) {
        HandheldPoses.Held held = MusicianPoses.prepare(maid, state);
        state.setRenderData(CARRIED, null);
        if (state.modelType != ModelType.SIMPLE_BEDROCK && state.getRenderData(MusicianPoses.MUSICIAN) == null
            && maid.getMainHandItem().getItem() instanceof dev.starbloom.orchestra.music.HandheldInstrumentItem item) {
            // GeckoLib：不在琴凳上也拿着手持乐器时，默认动作（hold_mainhand#…）照样把它端在演奏的位置，乐器就画在那里（不拉弓、不按键）
            held = HandheldPoses.heldFor(item.kind(), maid, 0);
            state.rightHandItemState.clear();
            state.leftHandItemState.clear();
            state.setRenderData(CARRIED, item.kind());
        }
        state.setRenderData(HandheldPoses.HELD, held);
        if (state.modelType != ModelType.SIMPLE_BEDROCK || state.getRenderData(MusicianPoses.MUSICIAN) == null) return;
        List<IAnimation<EntityMaidRenderState>> animations = new ArrayList<>(state.animations);
        animations.add(POSE);
        state.animations = animations;
    }

    private static void legacyLayer(AddMaidLayerEvent.Legacy event) {
        event.getRenderer().addLayer(new Layer(event.getRenderer()));
    }

    private static void geckoLayer(AddMaidLayerEvent.Gecko event) {
        event.getRenderer().addLayer(new GeckoLayer());
    }

    private static void geckoClips(DefaultGeckoAnimationEvent event) {
        event.addAnimation(DefaultGeckoAnimationEvent.AnimationType.MAID, StarbloomOrchestra.id("animation/maid_musician.animation.json"));
    }

    // —— 基岩版模型 ——

    /** 女仆模型里的换算：模型像素（y 向下、脚在 +24）按 scale 缩放到方块像素，整个人（根骨骼）抬高 lift 方块像素。 */
    private record Frame(float scale, float lift, Vector3f feet) {
        /** 演奏者坐标（方块像素）→ 根骨骼里的模型像素（女仆的手臂、腿、头都直接挂在根上）。 */
        Vector3f toRoot(Vector3f block) {
            return new Vector3f(-(block.x - feet.x) / scale, 24 - (block.y - feet.y - lift) / scale, (block.z - feet.z) / scale);
        }

        Vector3f fromRoot(Vector3f model) {
            return new Vector3f(feet.x - model.x * scale, feet.y + lift + (24 - model.y) * scale, feet.z + model.z * scale);
        }

        /** 方块像素 → 根骨骼的模型像素（画琴弓用）。 */
        Matrix4f toRootMatrix() {
            return new Matrix4f().translate(0, 24, 0).scale(-1 / scale, -1 / scale, 1 / scale).translate(-feet.x, -feet.y - lift, -feet.z);
        }
    }

    private static @Nullable Frame frame(EntityMaidRenderState state, MusicianPoses.Musician m, HashMap<String, BedrockPart> models) {
        float scale = state.modelInfo == null ? 1 : state.modelInfo.getRenderEntityScale();
        if (scale <= 0) return null;
        float lift = 0;
        BedrockPart leg = models.get("legRight");
        if (!m.standing() && leg != null) {
            // 坐着：大腿压在凳面上（髋 = 凳面 + 半条大腿粗）；站着：脚落在地上
            float hip = (24 - leg.getInitialPose().y()) * scale;
            lift = m.seat() + THIGH_HALF * scale - hip;
        }
        return new Frame(scale, lift, m.feet());
    }

    /** 加在女仆模组的动画之后：根骨骼的高度、双臂、双腿。 */
    private static final IAnimation<EntityMaidRenderState> POSE = (state, models) -> {
        MusicianPoses.Musician m = state.getRenderData(MusicianPoses.MUSICIAN);
        if (m == null) return;
        Frame f = frame(state, m, models);
        BedrockPart root = IAnimation.root(models);
        if (f == null || root == null) return;
        posed++;
        // 女仆模组坐下时把整个人往下挪 0.3 格（为它自己的坐垫），这里换成琴凳、工位的高度
        root.offsetX = 0;
        root.offsetY = -f.lift() / (16 * f.scale());
        root.offsetZ = 0;
        root.xRot = root.yRot = root.zRot = 0;
        BedrockPart left = models.get("armLeft"), right = models.get("armRight");
        switch (m.mode()) {
            case HANDHELD -> handheld(state, m, models, left, right);
            case BOWED -> {
                HeldRig.Place p = bowed(m, f, left, right, state.ageInTicks);
                if (p != null) {
                    reach(right, f.toRoot(p.mainHand()), -1);
                    reach(left, f.toRoot(p.offHand()), 1);
                }
            }
            case MALLETS -> {
                mallet(left, m.left() == null ? null : aim(f.toRoot(m.left())), 1);
                mallet(right, m.right() == null ? null : aim(f.toRoot(m.right())), -1);
            }
            default -> {
                if (m.left() != null) reach(left, f.toRoot(m.left()), 1);
                if (m.right() != null) reach(right, f.toRoot(m.right()), -1);
            }
        }
        legs(m, models.get("legLeft"), models.get("legRight"));
    };

    /** 手持乐器：身体、头的坐标系换到女仆模型上（脖子在头的支点），乐器和双手的位置同玩家（{@link HeldRig#place}）。 */
    private static void handheld(EntityMaidRenderState state, MusicianPoses.Musician m, HashMap<String, BedrockPart> models,
                                 @Nullable BedrockPart left, @Nullable BedrockPart right) {
        HeldRig.Place p = handheldPlace(state, m, models);
        if (p == null) return;
        boolean leftHanded = state.mainArm == HumanoidArm.LEFT;
        reach(leftHanded ? left : right, p.mainHand(), leftHanded ? 1 : -1);
        reach(leftHanded ? right : left, p.offHand(), leftHanded ? -1 : 1);
    }

    private static HeldRig.@Nullable Place handheldPlace(EntityMaidRenderState state, MusicianPoses.Musician m, HashMap<String, BedrockPart> models) {
        BedrockPart head = models.get("head");
        if (head == null) return null;
        BedrockPart body = models.get("body");
        Matrix4f bodyFrame = new Matrix4f().translate(head.x, head.y, head.z);
        if (body != null) bodyFrame.rotateZYX(body.zRot, body.yRot, body.xRot);
        Matrix4f headFrame = new Matrix4f().translate(head.x, head.y, head.z).rotateZYX(head.zRot, head.yRot, head.xRot);
        return HeldRig.place(m.kind(), bodyFrame, headFrame, state.mainArm == HumanoidArm.LEFT, state.ageInTicks, m.move());
    }

    /** 大提琴、低音提琴：肩的位置换到方块像素，琴弓和两只手的位置同玩家（{@link HeldRig#seatedBlock}）。 */
    private static HeldRig.@Nullable Place bowed(MusicianPoses.Musician m, Frame f, @Nullable BedrockPart left, @Nullable BedrockPart right, float t) {
        if (left == null || right == null) return null;
        Vector3f rs = f.fromRoot(new Vector3f(right.x - 1, right.y, right.z)), ls = f.fromRoot(new Vector3f(left.x + 1, left.y, left.z));
        return HeldRig.seatedBlock(m.kind(), rs, ls, GRIP * f.scale(), t, m.move());
    }

    /** 同 {@link MusicianPoses#malletAim}，按女仆的手臂长缩小：手臂指向槌头上方往回一点，槌从手里斜着落下。 */
    private static Vector3f aim(Vector3f head) {
        float k = GRIP / 9;
        return new Vector3f(head.x, head.y - 5 * k, head.z + 3 * k);
    }

    private static void mallet(@Nullable BedrockPart arm, @Nullable Vector3f aim, float side) {
        if (arm == null) return;
        if (aim != null) {
            reach(arm, aim, side);
        } else {
            // 不拿槌的手垂在身旁
            arm.xRot = 0;
            arm.yRot = 0;
            arm.zRot = -0.08f * side;
        }
    }

    private static void reach(@Nullable ModelPart arm, Vector3f hand, float side) {
        if (arm != null) HandheldPoses.reach(arm, hand, 0, 0, side);
    }

    /** 腿：站着伸直；大提琴两膝分开夹着琴；键盘、竖琴、定音鼓前往前下方斜伸（平伸会插进乐器）；手持乐器照女仆模组的坐姿。 */
    private static void legs(MusicianPoses.Musician m, @Nullable BedrockPart left, @Nullable BedrockPart right) {
        if (left == null || right == null) return;
        if (m.standing()) {
            set(left, 0, -0.08f, 0);
            set(right, 0, 0.08f, 0);
        } else if (m.mode() == MusicianPoses.Mode.BOWED) {
            set(left, -1.35f, -0.5f, 0);
            set(right, -1.35f, 0.42f, 0);
        } else if (m.mode() != MusicianPoses.Mode.HANDHELD) {
            set(left, -1.05f, -0.12f, 0);
            set(right, -1.05f, 0.12f, 0);
        }
    }

    private static void set(ModelPart part, float x, float y, float z) {
        part.xRot = x;
        part.yRot = y;
        part.zRot = z;
    }

    /** 手持乐器、琴弓、琴槌：画在根骨骼的像素里（女仆模组的手持物品那一层同样先走一遍根骨骼）。 */
    private static final class Layer extends RenderLayer<EntityMaidRenderState, EntityMaidModel> {
        Layer(EntityMaidRenderer renderer) {
            super(renderer);
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, EntityMaidRenderState state, float yRot, float xRot) {
            HandheldPoses.Held held = state.getRenderData(HandheldPoses.HELD);
            MusicianPoses.Musician m = state.getRenderData(MusicianPoses.MUSICIAN);
            if (held == null || m == null || state.isInvisible || state.modelType != ModelType.SIMPLE_BEDROCK) return;
            EntityMaidModel model = getParentModel();
            HashMap<String, BedrockPart> parts = new HashMap<>();
            for (String name : new String[]{"armLeft", "armRight", "legRight", "head", "body"}) {
                if (model.getPart(name) != SimpleBedrockParts.EMPTY) parts.put(name, model.getPart(name));
            }
            Frame f = frame(state, m, parts);
            if (f == null) return;
            switch (m.mode()) {
                case HANDHELD -> {
                    HeldRig.Place p = handheldPlace(state, m, parts);
                    if (p == null) return;
                    if (held.instrument() != null && p.instrument() != null) draw(pose, collector, light, state, model, p.instrument(), held.instrument());
                    if (held.bow() != null && p.bow() != null) draw(pose, collector, light, state, model, p.bow(), held.bow());
                }
                case BOWED -> {
                    HeldRig.Place p = bowed(m, f, parts.get("armLeft"), parts.get("armRight"), state.ageInTicks);
                    if (p != null && p.bow() != null && held.bow() != null) {
                        draw(pose, collector, light, state, model, f.toRootMatrix().mul(p.bow()), held.bow());
                    }
                }
                case MALLETS -> {
                    if (held.bow() == null) return;
                    for (boolean left : new boolean[]{true, false}) {
                        Vector3f target = left ? m.left() : m.right();
                        BedrockPart arm = parts.get(left ? "armLeft" : "armRight");
                        if (target == null || arm == null) continue;
                        Vector3f t = f.toRoot(target);
                        Vector3f shoulder = new Vector3f(arm.x + (left ? 1 : -1), arm.y, arm.z);
                        Vector3f hand = aim(t).sub(shoulder).normalize().mul(GRIP).add(shoulder);
                        draw(pose, collector, light, state, model,
                            HeldRig.along(hand, t, StationPoses.toolGrip(m.kind()), StationPoses.toolHead(m.kind())), held.bow());
                    }
                }
                default -> { }
            }
        }

        private static void draw(PoseStack pose, SubmitNodeCollector collector, int light, EntityMaidRenderState state, EntityMaidModel model,
                                 Matrix4f pixels, ItemStackRenderState item) {
            pose.pushPose();
            model.root().translateAndRotate(pose);
            pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
            pose.mulPose(pixels);
            pose.scale(16, 16, 16);
            item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
            pose.popPose();
        }
    }

    // —— GeckoLib 模型 ——

    /** GeckoLib 女仆不在琴凳、工位上，手里拿着的手持乐器（见 {@link #prepare}）。 */
    private static final net.minecraft.util.context.ContextKey<dev.starbloom.orchestra.music.InstrumentKind> CARRIED =
        new net.minecraft.util.context.ContextKey<>(StarbloomOrchestra.id("maid_carried_instrument"));

    /**
     * 这一帧 GeckoLib 女仆的手和头（女仆模组的动画算完之后），都在层的坐标里、按模型像素：
     * left / right 是两只手的挂点；neck 是头骨的支点，head 是头骨的转动（看头朝哪）。
     */
    private record Anchors(Vector3f left, Vector3f right, Vector3f neck, org.joml.Matrix3f head) {
        static @Nullable Anchors of(com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.gecko.GeckoMaidRenderData data, PoseStack pose) {
            var state = data.modelState;
            if (state == null) return null;
            Matrix4f inv = new Matrix4f(pose.last().pose()).invert();
            Vector3f left = locator(state, com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoLocatorType.LEFT_HAND, pose, inv);
            Vector3f right = locator(state, com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoLocatorType.RIGHT_HAND, pose, inv);
            Matrix4f[] head = new Matrix4f[1];
            Vector3f[] pivot = new Vector3f[1];
            state.visitRenderBones(pose.last(), (bone, p) -> {
                if (head[0] == null && bone.name().equals("Head")) {
                    head[0] = new Matrix4f(inv).mul(p.pose());
                    pivot[0] = new Vector3f(bone.pivot());
                }
            });
            if (left == null || right == null || head[0] == null) return null;
            Vector3f neck = head[0].transformPosition(new Vector3f(pivot[0]).div(16)).mul(16);
            return new Anchors(left, right, neck, head[0].get3x3(new org.joml.Matrix3f()));
        }

        private static @Nullable Vector3f locator(com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.GeoModelState state,
                                                  com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoLocatorType type,
                                                  PoseStack pose, Matrix4f inv) {
            Vector3f[] out = new Vector3f[1];
            state.visitLocatorGroup(type, pose, p -> {
                if (out[0] == null) out[0] = new Matrix4f(inv).mul(p.last().pose()).transformPosition(new Vector3f()).mul(16);
            });
            return out[0];
        }
    }

    /**
     * GeckoLib 模型的乐器、琴弓、琴槌：女仆模组在别的线程里按 {@code maid_musician.animation.json} 的默认动作算好骨骼，
     * 这一层在那之后按手的挂点和头骨把它们画上去（{@link AddMaidLayerEvent.Gecko}）。
     */
    private static final class GeckoLayer implements com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer<EntityMaidRenderState,
        com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.gecko.GeckoMaidRenderData> {
        @Override
        public void submit(SubmitNodeCollector collector, PoseStack pose, EntityMaidRenderState state,
                           com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.gecko.GeckoMaidRenderData data,
                           net.minecraft.client.renderer.state.level.CameraRenderState camera) {
            HandheldPoses.Held held = state.getRenderData(HandheldPoses.HELD);
            MusicianPoses.Musician m = state.getRenderData(MusicianPoses.MUSICIAN);
            var carried = state.getRenderData(CARRIED);
            if (held == null || (m == null && carried == null) || state.isInvisible || state.modelType == ModelType.SIMPLE_BEDROCK) return;
            Anchors a = Anchors.of(data, pose);
            if (a == null) return;
            int light = state.lightCoords, outline = state.outlineColor;
            if (m == null) {
                handheld(pose, collector, light, outline, carried, a, held);
                return;
            }
            // 层的坐标里 1 个模型像素在世界里有多大（女仆模型按 render_entity_scale 缩放过）
            float scale = (float) Math.cbrt(pose.last().pose().determinant3x3());
            if (!(scale > 1e-4f)) return;
            switch (m.mode()) {
                case HANDHELD -> handheld(pose, collector, light, outline, m.kind(), a, held);
                case BOWED -> {
                    if (held.bow() == null) return;
                    // 弓根在右手，弓杆从琴弦上弓毛该压的那一点过（同玩家，HeldRig.seatedBlock）
                    Vector3f contact = toLayer(HeldRig.stringContact(m.kind()), m.feet(), scale);
                    draw(pose, collector, light, outline, span(a.right(), contact, BOW_FROG, BOW_TIP, BOW_HAIR, FORWARD,
                        HeldRig.bowScale(m.kind()), false), held.bow());
                }
                case MALLETS -> {
                    if (held.bow() == null) return;
                    for (boolean left : new boolean[]{true, false}) {
                        Vector3f target = left ? m.left() : m.right();
                        if (target == null) continue;
                        draw(pose, collector, light, outline, HeldRig.along(left ? a.left() : a.right(), toLayer(target, m.feet(), scale),
                            StationPoses.toolGrip(m.kind()), StationPoses.toolHead(m.kind())), held.bow());
                    }
                }
                default -> { }
            }
        }
    }

    // 层的坐标（模型像素）：x 向右、y 向上、z 向后（脸朝 -z），原点在骑着的座位上、同演奏者的方块像素只差双脚的位置
    private static final Vector3f UP = new Vector3f(0, 1, 0), FORWARD = new Vector3f(0, 0, -1), RIGHT = new Vector3f(1, 0, 0);
    /** 琴弓（物品像素）：弓根、弓尖方向上的一点、弓毛那一侧（同 HeldRig 里的弓）。 */
    private static final Vector3f BOW_FROG = new Vector3f(8.1f, 1.8f, 8), BOW_TIP = new Vector3f(8.1f, 16, 8), BOW_HAIR = new Vector3f(1, 0, 0);

    /** 演奏者坐标（方块像素）→ 层的坐标：去掉双脚的位置，按模型的缩放换算。 */
    private static Vector3f toLayer(Vector3f block, Vector3f feet, float scale) {
        return new Vector3f(block).sub(feet).div(scale);
    }

    /** 嘴（吹口、号嘴贴着的地方）、下巴偏左（小提琴的腮托）：头骨支点往上、往前，跟着头转。 */
    private static Vector3f onHead(Anchors a, float x, float y, float z) {
        return a.head().transform(new Vector3f(x, y, z)).add(a.neck());
    }

    /** 手持乐器：两头各对着一个点（嘴或下巴、某只手），物品的某一面尽量朝着给定的方向。 */
    private static void handheld(PoseStack pose, SubmitNodeCollector collector, int light, int outline, dev.starbloom.orchestra.music.InstrumentKind kind,
                                 Anchors a, HandheldPoses.Held held) {
        if (held.instrument() == null) return;
        Vector3f mouth = onHead(a, 0, 2.2f, -3.6f);
        Matrix4f body;
        switch (kind) {
            case VIOLIN, VIOLA -> {
                // 腮托在下巴左边，琴颈在托琴的左手里；琴面朝上、往右（拉弓那边）偏 15°
                float scale = kind == dev.starbloom.orchestra.music.InstrumentKind.VIOLA ? 0.75f : 0.65f;
                Vector3f roll = new Vector3f(UP).add(new Vector3f(RIGHT).mul(0.27f));
                body = span(onHead(a, -1.2f, 0.8f, -3.0f), a.left(), new Vector3f(8, 1, 9), new Vector3f(8, 13, 11), new Vector3f(0, 0, -1), roll, scale, true);
                if (held.bow() != null) {
                    Vector3f contact = body.transformPosition(new Vector3f(0, 5 - 8, 6.4f - 8));
                    Vector3f back = body.transformDirection(new Vector3f(0, 0, 1)).normalize();
                    draw(pose, collector, light, outline, span(a.right(), contact, BOW_FROG, BOW_TIP, BOW_HAIR, back, scale, false), held.bow());
                }
            }
            // 吹孔在嘴边，笛身伸向右手；按键那面朝上
            case FLUTE -> body = span(mouth, a.right(), new Vector3f(2.8f, 8, 8), new Vector3f(11.5f, 8, 8), new Vector3f(0, 1, 0), UP, 0.75f, true);
            // 哨片含在嘴里，管身往下伸到下面那只（右）手；按键那面朝前
            case OBOE -> body = span(mouth, a.right(), new Vector3f(8, 15.6f, 8), new Vector3f(8, 5, 8), new Vector3f(0, 0, 1),
                new Vector3f(FORWARD).negate(), 0.72f, true);
            // 管靴在右手（胯前），管身斜着伸到左手
            case BASSOON -> body = span(a.right(), a.left(), new Vector3f(9.4f, 0.5f, 10.6f), new Vector3f(6.6f, 7.5f, 10.6f), new Vector3f(0, 0, 1),
                new Vector3f(FORWARD).negate(), 0.9f, true);
            // 篠笛：同长笛，短一些
            case SHINOBUE -> body = span(mouth, a.right(), new Vector3f(3.4f, 8, 8), new Vector3f(11.1f, 8, 8), new Vector3f(0, 1, 0), UP, 0.62f, true);
            // 尺八：同双簧管竖着吹，歌口在嘴边
            case SHAKUHACHI -> body = span(mouth, a.right(), new Vector3f(8, 15.6f, 8), new Vector3f(8, 4.6f, 8), new Vector3f(0, 0, 1),
                new Vector3f(FORWARD).negate(), 0.72f, true);
            // 神乐铃：柄握在右手里，铃朝上
            case KAGURA_SUZU -> body = span(a.right(), new Vector3f(UP).mul(10).add(a.right()), new Vector3f(8, 3.6f, 8), new Vector3f(8, 15, 8),
                new Vector3f(0, 0, 1), new Vector3f(FORWARD).negate(), 0.5f, false);
            // 号嘴贴着嘴，号身朝前略往下
            default -> body = span(mouth, new Vector3f(FORWARD).mul(10).add(mouth).sub(0, 1.2f, 0), new Vector3f(0.4f, 9, 8), new Vector3f(7.4f, 9, 8),
                new Vector3f(0, 1, 0), UP, 0.65f, false);
        }
        draw(pose, collector, light, outline, body, held.instrument());
    }

    /**
     * 物品像素里的 from 点放到 p，from→to 的方向指向 q，物品的 up 轴尽量朝 worldUp；返回模型像素里的摆放（同 HeldRig 的写法：
     * 作用在以 8 为中心的物品像素上）。fit 为真时按 p 到 q 的距离把物品放大缩小一点（0.85–1.25 倍），两头正好落在两个点上。
     */
    private static Matrix4f span(Vector3f p, Vector3f q, Vector3f from, Vector3f to, Vector3f up, Vector3f worldUp, float scale, boolean fit) {
        Vector3f ia = new Vector3f(to).sub(from);
        float length = ia.length();
        ia.normalize();
        Vector3f iu = new Vector3f(up).sub(new Vector3f(ia).mul(ia.dot(up))).normalize();
        Vector3f ic = new Vector3f(ia).cross(iu);
        Vector3f wa = new Vector3f(q).sub(p);
        float distance = wa.length();
        if (distance < 1e-4f) wa.set(FORWARD);
        wa.normalize();
        Vector3f wu = new Vector3f(worldUp).sub(new Vector3f(wa).mul(wa.dot(worldUp)));
        if (wu.lengthSquared() < 1e-6f) wu.set(Math.abs(wa.y) > 0.9f ? FORWARD : UP).sub(new Vector3f(wa).mul(wa.dot(Math.abs(wa.y) > 0.9f ? FORWARD : UP)));
        wu.normalize();
        Vector3f wc = new Vector3f(wa).cross(wu);
        org.joml.Matrix3f item = new org.joml.Matrix3f(ia, iu, ic), world = new org.joml.Matrix3f(wa, wu, wc);
        org.joml.Matrix3f r = world.mul(item.transpose());
        if (fit && length > 1e-4f) scale *= Math.max(0.85f, Math.min(1.25f, distance / (length * scale)));
        return new Matrix4f().translate(p).mul(new Matrix4f().set(r)).scale(scale).translate(8 - from.x, 8 - from.y, 8 - from.z);
    }

    /** 画在层的坐标（模型像素）里。 */
    private static void draw(PoseStack pose, SubmitNodeCollector collector, int light, int outline, Matrix4f pixels, ItemStackRenderState item) {
        pose.pushPose();
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        pose.mulPose(pixels);
        pose.scale(16, 16, 16);
        item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, outline);
        pose.popPose();
    }

    /** 女仆模组找不到部件时给的空部件。 */
    private static final class SimpleBedrockParts {
        static final BedrockPart EMPTY = com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.SimpleBedrockEntityModel.EMPTY;
    }
}
