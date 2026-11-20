package dev.starbloom.orchestra.client.music;

import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.event.client.DefaultGeckoAnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.capability.GeckoMaidEntityCapabilityProvider;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionManager;
import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.GeckoEntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.geckolayer.GeckoLayerMaidHeld;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.layer.LayerMaidHeldItem;
import com.github.tartaricacid.touhoulittlemaid.client.resource.GeckoModelLoader;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.processor.ILocationBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.file.AnimationFile;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import dev.starbloom.orchestra.StarbloomOrchestra;
import dev.starbloom.orchestra.mixin.LivingEntityRendererAccess;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 车万女仆坐上星辰花琴凳、站到乐器工位上时的演奏姿势（docs/ORCHESTRA.md 第 16 节）。只在装了车万女仆时编译、注册
 * （{@code StarbloomOrchestra} 按名字找到 {@link #init}），对的是 1.21.1 的女仆模组 1.5.3，用的是它公开的接口
 * （{@link ILittleMaid} 扩展、{@link DefaultGeckoAnimationEvent}）和它的渲染器、层、GeckoLib 挂点这几个类；补默认动作
 * （{@link #ensureClips}）还用到它的动画缓存和按名字分动作的条件（不在 {@code api} 包里，女仆模组改了要跟着改）：
 *
 * <ul>
 * <li><b>基岩版模型</b>（女仆模组自带的模型都是）：女仆渲染器是原版的 {@code LivingEntityRenderer}，画之前发
 *     {@link RenderLivingEvent.Pre}（这里按实体算好这一帧的 {@link MusicianPoses.Drawing}），女仆模型的 setupAnim（女仆模组自己的
 *     动画，模型包的 JS 动画、硬编码动画都在里面）跑完之后（{@code MusicianModelMixin} 经 {@link MusicianPoses#afterModelSetup}
 *     交给这里的 {@link #POSER}）摆双臂、双腿、整个人的高度；26.1 是在渲染状态里给这只女仆的动画列表末尾加一段 {@code IAnimation}，次序相同。
 *     女仆模组坐下时用 {@code GlWrapper} 直接把画身体的 PoseStack 往下挪 0.3 格（26.1 是挪根骨骼），所以 setupAnim 之前记下那时的矩阵，
 *     之后换回来再按琴凳、工位的高度抬。手位就是玩家和其他生物用的那一份（{@link MusicianPoses.Musician}，「演奏者面朝北」的方块像素），
 *     这里换算到女仆模型的像素（模型按 {@code render_entity_scale} 缩放）。女仆的手臂和原版一样是直的、从肩上的支点垂下，
 *     所以沿用原版的转法（{@link HandheldPoses#reachAngles}）。手持乐器、琴弓、琴槌由 {@link Extension} 加的一层画出来。</li>
 * <li><b>GeckoLib 模型</b>：同 26.1，用 {@link DefaultGeckoAnimationEvent} 加默认动作：按骑着的实体
 *     （{@code vehicle#starbloom_orchestra:bench_seat} 琴凳上弹、{@code instrument_seat} 坐着拉大提琴、{@code standing_post} 站着拉、敲；
 *     按同名的实体标签），再按手里的手持乐器（{@code hold_mainhand#starbloom_orchestra:maid_pose/...}，物品标签）摆双臂，坐着时拉弓；
 *     手不跟着音走，模型包自己写了同名动作时用模型包的。这个事件在游戏第一次加载资源时谁也收不到（见 {@link #ensureClips}），
 *     所以画之前再看一眼、缺了就补上。乐器、琴弓、琴槌由 {@link Extension} 加的一层按手的挂点和头骨画上去
 *     （{@link GeckoLayer}）。1.5.3 的骨骼是在渲染线程上、画之前才算的，层里读到的就是这一帧的。</li>
 * </ul>
 * 两种模型演奏时都不画女仆手里原本的东西：女仆模组画手持物品的那一层（{@link LayerMaidHeldItem}、{@link GeckoLayerMaidHeld}）
 * 换成一层外壳，{@link MusicianPoses#hidesHandItems} 时不画（26.1 是清空渲染状态里两只手的物品）。
 */
public final class MaidMusicians {
    /** 基岩版女仆模型手臂从肩上的支点到手心（模型像素）：模板的手臂长 8。 */
    private static final float GRIP = 7;
    /** 大腿一半的粗（模型像素）：模板的腿 3 宽。 */
    private static final float THIGH_HALF = 1.5f;

    /** 测试用（附属的 {@code MaidHallViews}）：基岩版模型的演奏姿势摆过几次。 */
    public static int posed;
    static int DEBUG_BOW;

    /**
     * 这一帧正在画的这只女仆（{@link RenderLivingEvent.Pre} 到 Post 之间）：画她的是哪个渲染器（GeckoLib 的还是基岩版的）、
     * 基岩版模型的缩放（女仆渲染器这一帧用的模型信息）；基岩版模型 setupAnim 之前画身体用的矩阵（{@link #POSER}）。
     */
    private static final class Look {
        final boolean gecko;
        final float scale;
        @Nullable Matrix4f beforePose;
        @Nullable Matrix3f beforeNormal;

        Look(boolean gecko, float scale) {
            this.gecko = gecko;
            this.scale = scale;
        }
    }

    private static final Map<LivingEntity, Look> LOOKS = new IdentityHashMap<>();

    private MaidMusicians() {}

    /** 测试用：一只女仆，model 为 null 时是默认模型。不冒聊天气泡（截图里会挡着别的女仆）。 */
    public static net.minecraft.world.entity.Mob testMaid(net.minecraft.server.level.ServerLevel level, @Nullable String model) {
        EntityMaid maid = EntityMaid.TYPE.create(level);
        if (maid == null) throw new IllegalStateException("cannot create a maid");
        if (model != null) maid.setModelId(model);
        maid.getConfigManager().setChatBubbleShow(false);
        return maid;
    }

    /**
     * modBus 留着给调用方（{@code StarbloomOrchestra} 按这个签名反射调用）；1.20.1 里用到的事件都在游戏事件总线上。
     * 女仆模组的文档要求 {@link DefaultGeckoAnimationEvent} 在模组初始化时注册（它发得很早）。两层由女仆模组按 {@link Extension} 加。
     */
    public static void init(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, RenderLivingEvent.Pre.class, MaidMusicians::beforeRender);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderLivingEvent.Post.class, MaidMusicians::afterRender);
        MusicianPoses.addPoser(POSER);
        MinecraftForge.EVENT_BUS.addListener(MaidMusicians::geckoClips);
    }

    private static @Nullable GeckoMaidEntity<?> geckoMaid(LivingEntity maid) {
        return maid.getCapability(GeckoMaidEntityCapabilityProvider.CAP).orElse(null);
    }

    /**
     * 画一只女仆之前（排在最后，被别人取消了就不算）：记下这一帧怎么演奏、要画的乐器。基岩版模型由女仆渲染器自己发这个事件，
     * GeckoLib 模型由它里面的 GeckoLib 渲染器发；别的渲染器（YSM 模型）不管。
     */
    private static void beforeRender(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        Look look;
        if (event.getRenderer() instanceof EntityMaidRenderer renderer) {
            // 女仆渲染器在发这个事件之前已经换好了这只女仆的模型和模型信息（可能被 RenderMaidEvent 换过）
            MaidModelInfo info = renderer.getMainInfo();
            look = new Look(false, info == null ? 1 : info.getRenderEntityScale());
        } else if (event.getRenderer() instanceof GeckoEntityMaidRenderer<?>) {
            look = new Look(true, 1);
            // 女仆渲染器在交给 GeckoLib 渲染器之前已经换好了模型信息，动画文件就是这一帧要用的
            GeckoMaidEntity<?> geo = geckoMaid(maid);
            if (geo != null) ensureClips(geo.getAnimationFileLocation());
        } else {
            return;
        }
        MusicianPoses.Drawing d = new MusicianPoses.Drawing(maid, event.getPartialTick(), event.getPackedLight());
        prepare(maid, d, look.gecko);
        MusicianPoses.begin(maid, d);
        // 画的时候抛了异常就收不到 Post，留下的旧条目在这里清掉
        if (LOOKS.size() > 256) LOOKS.clear();
        LOOKS.put(maid, look);
    }

    private static void afterRender(RenderLivingEvent.Post<?, ?> event) {
        LOOKS.remove(event.getEntity());
    }

    /**
     * 记下演奏者和要画的乐器、琴弓、琴槌，手里原本的东西不画（两种模型都由我们这边的层画）。GeckoLib 模型的手臂是默认动作（见类说明）；
     * 基岩版模型的姿势在 {@link #afterSetupAnim} 里摆。
     */
    private static void prepare(EntityMaid maid, MusicianPoses.Drawing d, boolean gecko) {
        MusicianPoses.prepare(maid, d);
        if (d.musician == null && gecko && maid.getMainHandItem().getItem() instanceof dev.starbloom.orchestra.music.HandheldInstrumentItem item) {
            // GeckoLib：不在琴凳上也拿着手持乐器时，默认动作（hold_mainhand#…）照样把它端在演奏的位置，乐器就画在那里（不拉弓、不按键）
            d.held = HandheldPoses.heldFor(item.kind(), maid, 0);
            d.hideHandItems = true;
            d.carried = item.kind();
        }
    }

    private static void geckoClips(DefaultGeckoAnimationEvent event) {
        event.addAnimation(DefaultGeckoAnimationEvent.AnimationType.MAID, CLIPS);
        clips = null;
    }

    /** GeckoLib 女仆的默认动作（{@code maid_musician.animation.json}）。 */
    private static final ResourceLocation CLIPS = StarbloomOrchestra.id("animation/maid_musician.animation.json");
    /** 解析好的 {@link #CLIPS}（女仆模组的解析器）；{@link DefaultGeckoAnimationEvent} 再来时（重载资源）重读。 */
    private static volatile @Nullable AnimationFile clips;
    /** 按动画文件的 id：补过的是哪一份（女仆模组重载模型时换成新的一份，到时再看一遍）。 */
    private static final Map<ResourceLocation, AnimationFile> PATCHED = new HashMap<>();

    /**
     * 女仆模组 1.5.3 在第一次加载资源时就发 {@link DefaultGeckoAnimationEvent}，那时 NeoForge 的游戏事件总线还没开
     * （{@code ClientModLoader} 等第一次加载完才开，没开时发的事件直接丢掉），谁也收不到，要等 F3+T 之类的重载才收得到。
     * 所以 GeckoLib 女仆画之前看一眼她的动画文件里有没有这几段，没有就照女仆模组合并默认动作的做法补上
     * （{@code GeckoModelLoader.registerMaidAnimations}）：模型包自己有的同名动作不动，补上的按名字登记到女仆模组分动作用的条件里。
     */
    private static void ensureClips(ResourceLocation id) {
        AnimationFile file = GeckoLibCache.getInstance().getAnimations().get(id);
        if (file == null || PATCHED.get(id) == file) return;
        AnimationFile ours = clips();
        if (ours == null) return;
        PATCHED.put(id, file);
        ours.animations().forEach((name, animation) -> {
            if (!file.animations().containsKey(name)) {
                file.putAnimation(name, animation);
                ConditionManager.addTest(id, name);
            }
        });
    }

    private static @Nullable AnimationFile clips() {
        if (clips == null) {
            AnimationFile file = new AnimationFile();
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(CLIPS)) {
                GeckoModelLoader.mergeAnimationFile(in, file);
            } catch (IOException e) {
                LogUtils.getLogger().warn("Cannot read {}", CLIPS, e);
                return null;
            }
            clips = file;
        }
        return clips;
    }

    // —— 测试用（MaidViews）——

    /** 这一帧画这只女仆的是不是 GeckoLib 渲染器：只在画她的时候（{@link RenderLivingEvent.Pre} 到 Post 之间）有，别的时候为 null。 */
    static @Nullable Boolean drawnAsGecko(LivingEntity maid) {
        Look look = LOOKS.get(maid);
        return look == null ? null : look.gecko;
    }

    /** 这个女仆渲染器（基岩版、GeckoLib 两种）画手持物品的那一层换成了外壳、画乐器的那一层也在。 */
    static boolean layersReplaced(Object renderer) {
        List<?> layers;
        if (renderer instanceof EntityMaidRenderer bedrock) {
            layers = ((LivingEntityRendererAccess) bedrock).starbloom_orchestra$layers();
        } else if (renderer instanceof GeckoEntityMaidRenderer<?> gecko) {
            layers = gecko.getLayerRenderers();
        } else {
            return false;
        }
        boolean shell = false, ours = false;
        for (Object layer : layers) {
            if (layer instanceof LayerMaidHeldItem || layer instanceof GeckoLayerMaidHeld<?, ?>) return false;
            if (layer instanceof HideHeldItem || layer instanceof HideGeckoHeld) shell = true;
            if (layer instanceof Layer || layer instanceof GeckoLayer) ours = true;
        }
        return shell && ours;
    }

    /** 这只 GeckoLib 女仆眼下用的动画文件里有没有本模组的默认动作（模型包自带的，或 {@link #ensureClips} 补上的）。 */
    static boolean hasClips(LivingEntity maid) {
        GeckoMaidEntity<?> geo = geckoMaid(maid);
        if (geo == null) return false;
        AnimationFile file = GeckoLibCache.getInstance().getAnimations().get(geo.getAnimationFileLocation());
        return file != null && file.animations().containsKey("vehicle#starbloom_orchestra:bench_seat")
            && file.animations().containsKey("vehicle#starbloom_orchestra:standing_post");
    }

    /** 女仆模组此刻按骑着的实体给这只 GeckoLib 女仆挑的动作（{@code vehicle#...}），同它画之前查的那一路；没有时为空串。 */
    static String vehicleClip(LivingEntity maid) {
        if (!(maid instanceof Mob mob)) return "";
        GeckoMaidEntity<?> geo = geckoMaid(maid);
        if (geo == null) return "";
        var vehicle = ConditionManager.getVehicle(geo.getAnimationFileLocation());
        return vehicle == null ? "" : vehicle.doTest(mob);
    }

    // —— 加层（女仆模组的扩展接口）——

    /**
     * 女仆模组按注解 {@link LittleMaidExtension} 找到的扩展（26.1 是 {@code AddMaidLayerEvent.Legacy}、{@code .Gecko}）：
     * 女仆的两种渲染器建好时各调一次（每次重载资源都重建）。女仆模组在专用服务端上也会建这个类的实例，
     * 所以这里只把参数原样转交，加载这个类时不碰客户端的类。
     */
    @LittleMaidExtension
    public static final class Extension implements ILittleMaid {
        @Override
        public void addAdditionMaidLayer(EntityMaidRenderer renderer, EntityRendererProvider.Context context) {
            bedrockLayers(renderer);
        }

        @Override
        public void addAdditionGeckoMaidLayer(GeckoEntityMaidRenderer<? extends Mob> renderer, EntityRendererProvider.Context context) {
            geckoLayers(renderer);
        }
    }

    /**
     * 基岩版女仆渲染器：画手持物品的那一层换成外壳，画乐器的那一层紧跟在它后面。不能加在最后：女仆模组的背包层挪了 PoseStack
     * （往上半格、往后）不还原，后面的背后物品、旗帜两层是照着那里画的（26.1 每层各拿一份 PoseStack，没有这回事）。
     */
    @SuppressWarnings("unchecked")
    static void bedrockLayers(EntityMaidRenderer renderer) {
        List<RenderLayer<Mob, BedrockModel<Mob>>> layers =
            (List<RenderLayer<Mob, BedrockModel<Mob>>>) (List<?>) ((LivingEntityRendererAccess) renderer).starbloom_orchestra$layers();
        int at = 0;
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i) instanceof LayerMaidHeldItem held) {
                layers.set(i, new HideHeldItem(renderer, held));
                at = i + 1;
            }
        }
        layers.add(at, new Layer(renderer));
    }

    /** GeckoLib 女仆渲染器：同上（YSM 借用 GeckoLib 的层时按 {@code copy} 复制过去）。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void geckoLayers(GeckoEntityMaidRenderer<? extends Mob> renderer) {
        IGeoEntityRenderer<Mob> geo = (IGeoEntityRenderer<Mob>) (IGeoEntityRenderer) renderer;
        List<GeoLayerRenderer> layers = renderer.getLayerRenderers();
        int at = 0;
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i) instanceof GeckoLayerMaidHeld<?, ?> held) {
                layers.set(i, new HideGeckoHeld(geo, held));
                at = i + 1;
            }
        }
        layers.add(at, new GeckoLayer(geo));
    }

    /** 女仆模组画手持物品的那一层（基岩版）的外壳：演奏时不画（{@link MusicianPoses#hidesHandItems}），别的时候照原样画。 */
    private static final class HideHeldItem extends RenderLayer<Mob, BedrockModel<Mob>> {
        private final RenderLayer<Mob, BedrockModel<Mob>> held;

        HideHeldItem(RenderLayerParent<Mob, BedrockModel<Mob>> parent, RenderLayer<Mob, BedrockModel<Mob>> held) {
            super(parent);
            this.held = held;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, Mob mob, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!MusicianPoses.hidesHandItems(mob)) held.render(pose, buffers, light, mob, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        }
    }

    /** 同上，GeckoLib 版。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class HideGeckoHeld extends GeoLayerRenderer<Mob, IGeoEntityRenderer<Mob>> {
        private final GeoLayerRenderer held;

        HideGeckoHeld(IGeoEntityRenderer<Mob> renderer, GeoLayerRenderer held) {
            super(renderer);
            this.held = held;
        }

        @Override
        public GeoLayerRenderer<Mob, IGeoEntityRenderer<Mob>> copy(IGeoEntityRenderer<Mob> renderer) {
            return new HideGeckoHeld(renderer, held.copy(renderer));
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, Mob mob, float limbSwing, float limbSwingAmount,
                           float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!MusicianPoses.hidesHandItems(mob)) held.render(pose, buffers, light, mob, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
        }
    }

    // —— 基岩版模型 ——

    /** 女仆模型里的换算：模型像素（y 向下、脚在 +24）按 scale 缩放到方块像素，整个人抬高 lift 方块像素。 */
    private record Frame(float scale, float lift, Vector3f feet) {
        /** 演奏者坐标（方块像素）→ 模型像素（女仆的手臂、腿、头都直接挂在模型上，不在别的骨骼下）。 */
        Vector3f toRoot(Vector3f block) {
            return new Vector3f(-(block.x - feet.x) / scale, 24 - (block.y - feet.y - lift) / scale, (block.z - feet.z) / scale);
        }

        Vector3f fromRoot(Vector3f model) {
            return new Vector3f(feet.x - model.x * scale, feet.y + lift + (24 - model.y) * scale, feet.z + model.z * scale);
        }

        /** 方块像素 → 模型像素（画琴弓用）。 */
        Matrix4f toRootMatrix() {
            return new Matrix4f().translate(0, 24, 0).scale(-1 / scale, -1 / scale, 1 / scale).translate(-feet.x, -feet.y - lift, -feet.z);
        }
    }

    private static @Nullable Frame frame(float scale, MusicianPoses.Musician m, Map<String, BedrockPart> models) {
        if (!(scale > 0)) return null;
        float lift = 0;
        BedrockPart leg = models.get("legRight");
        if (!m.standing() && leg != null) {
            // 坐着：大腿压在凳面上（髋 = 凳面 + 半条大腿粗）；站着：脚落在地上
            // 腿的支点就是模型文件里的位置（女仆模组的动画只转腿、挪的是 offset，不挪支点；26.1 取的是部件的初始位置）
            float hip = (24 - leg.y) * scale;
            lift = m.seat() + THIGH_HALF * scale - hip;
        }
        return new Frame(scale, lift, m.feet());
    }

    /** 基岩版女仆模型的 setupAnim 前后（{@code MusicianModelMixin}）。 */
    private static final MusicianPoses.Poser POSER = new MusicianPoses.Poser() {
        @Override
        public void beforeSetupAnim(EntityModel<?> model, LivingEntity entity, PoseStack pose) {
            Look look = LOOKS.get(entity);
            MusicianPoses.Drawing d = MusicianPoses.drawing(entity);
            if (look == null || look.gecko || d == null || d.musician == null || !(model instanceof BedrockModel<?>)) return;
            look.beforePose = new Matrix4f(pose.last().pose());
            look.beforeNormal = new Matrix3f(pose.last().normal());
        }

        @Override
        public void afterSetupAnim(EntityModel<?> model, LivingEntity entity, @Nullable PoseStack pose) {
            MaidMusicians.afterSetupAnim(model, entity, pose);
        }
    };

    /** 女仆模组的动画之后（女仆模型的 setupAnim 跑完）：整个人的高度、双臂、双腿。 */
    private static void afterSetupAnim(EntityModel<?> entityModel, LivingEntity entity, @Nullable PoseStack pose) {
        if (!(entityModel instanceof BedrockModel<?> model) || !(entity instanceof EntityMaid maid)) return;
        MusicianPoses.Drawing d = MusicianPoses.drawing(maid);
        MusicianPoses.Musician m = d == null ? null : d.musician;
        Look look = LOOKS.get(maid);
        if (m == null || look == null || look.gecko) return;
        Map<String, BedrockPart> models = model.getModelMap();
        Frame f = frame(look.scale, m, models);
        if (f == null) return;
        posed++;
        Matrix4f beforePose = look.beforePose;
        Matrix3f beforeNormal = look.beforeNormal;
        look.beforePose = null;
        look.beforeNormal = null;
        if (pose != null && beforePose != null && beforeNormal != null) {
            // 女仆模组坐下时把整个人往下挪 0.3 格（为它自己的坐垫，GlWrapper 直接挪 PoseStack；骑扫帚时还会转），
            // 这里换回 setupAnim 之前的矩阵，再换成琴凳、工位的高度（模型坐标 y 向下，1 格按模型的缩放）。身体和后面各层都画在这里
            pose.last().pose().set(beforePose);
            pose.last().normal().set(beforeNormal);
            pose.translate(0, -f.lift() / (16 * f.scale()), 0);
        }
        BedrockPart left = models.get("armLeft"), right = models.get("armRight");
        switch (m.mode()) {
            case HANDHELD -> handheld(maid, d, m, models, left, right);
            case BOWED -> {
                HeldRig.Place p = bowed(m, f, left, right, d.ageInTicks);
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
    }

    /** 手持乐器：身体、头的坐标系换到女仆模型上（脖子在头的支点），乐器和双手的位置同玩家（{@link HeldRig#place}）。 */
    private static void handheld(EntityMaid maid, MusicianPoses.Drawing d, MusicianPoses.Musician m, Map<String, BedrockPart> models,
                                 @Nullable BedrockPart left, @Nullable BedrockPart right) {
        HeldRig.Place p = handheldPlace(maid, d, m, models);
        if (p == null) return;
        boolean leftHanded = maid.getMainArm() == HumanoidArm.LEFT;
        reach(leftHanded ? left : right, p.mainHand(), leftHanded ? 1 : -1);
        reach(leftHanded ? right : left, p.offHand(), leftHanded ? -1 : 1);
    }

    private static HeldRig.@Nullable Place handheldPlace(EntityMaid maid, MusicianPoses.Drawing d, MusicianPoses.Musician m,
                                                         Map<String, BedrockPart> models) {
        BedrockPart head = models.get("head");
        if (head == null) return null;
        BedrockPart body = models.get("body");
        Matrix4f bodyFrame = new Matrix4f().translate(head.x, head.y, head.z);
        if (body != null) bodyFrame.rotateZYX(body.zRot, body.yRot, body.xRot);
        Matrix4f headFrame = new Matrix4f().translate(head.x, head.y, head.z).rotateZYX(head.zRot, head.yRot, head.xRot);
        return HeldRig.place(m.kind(), bodyFrame, headFrame, maid.getMainArm() == HumanoidArm.LEFT, d.ageInTicks, m.move());
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

    /** 同 {@link HandheldPoses#reach}（同一份算法）：女仆模组的基岩版部件不是原版的 ModelPart，所以直接改部件的转角。 */
    private static void reach(@Nullable BedrockPart arm, Vector3f hand, float side) {
        if (arm == null) return;
        float[] r = HandheldPoses.reachAngles(arm.x, arm.y, arm.z, hand, 0, 0, side);
        if (r == null) return;
        arm.xRot = r[0];
        arm.yRot = r[1];
        arm.zRot = 0;
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

    private static void set(BedrockPart part, float x, float y, float z) {
        part.xRot = x;
        part.yRot = y;
        part.zRot = z;
    }

    /**
     * 手持乐器、琴弓、琴槌：画在模型像素里。女仆渲染器画完身体后各层就在模型的坐标里（女仆模组的手持物品那一层也是这样，
     * 直接走手臂部件），上面抬高的那一下也已经在 PoseStack 里了（26.1 是先走一遍根骨骼）。
     */
    private static final class Layer extends RenderLayer<Mob, BedrockModel<Mob>> {
        Layer(EntityMaidRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, Mob mob, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (mob instanceof EntityMaid maid) renderBedrock(pose, buffers, light, getParentModel(), maid);
        }
    }

    private static void renderBedrock(PoseStack pose, MultiBufferSource buffers, int light, BedrockModel<?> model, EntityMaid maid) {
        MusicianPoses.Drawing d = MusicianPoses.drawing(maid);
        Look look = LOOKS.get(maid);
        if (d == null || look == null || look.gecko) return;
        HandheldPoses.Held held = d.held;
        MusicianPoses.Musician m = d.musician;
        if (held == null || m == null || maid.isInvisible()) return;
        Map<String, BedrockPart> parts = model.getModelMap();
        Frame f = frame(look.scale, m, parts);
        if (f == null) return;
        switch (m.mode()) {
            case HANDHELD -> {
                HeldRig.Place p = handheldPlace(maid, d, m, parts);
                if (p == null) return;
                if (held.instrument() != null && p.instrument() != null) draw(pose, buffers, light, p.instrument(), held.instrument());
                if (held.bow() != null && p.bow() != null) draw(pose, buffers, light, p.bow(), held.bow());
            }
            case BOWED -> {
                HeldRig.Place p = bowed(m, f, parts.get("armLeft"), parts.get("armRight"), d.ageInTicks);
                if (p != null && p.bow() != null && held.bow() != null) {
                    draw(pose, buffers, light, f.toRootMatrix().mul(p.bow()), held.bow());
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
                    draw(pose, buffers, light, HeldRig.along(hand, t, StationPoses.toolGrip(m.kind()), StationPoses.toolHead(m.kind())), held.bow());
                }
            }
            default -> { }
        }
    }

    // —— GeckoLib 模型 ——

    /**
     * 这一帧 GeckoLib 女仆的手和头（女仆模组的动画算完之后），都在层的坐标里、按模型像素：
     * left / right 是两只手的挂点（{@code LeftHandLocator}、{@code RightHandLocator}，同女仆模组的手持物品层）；
     * neck 是头骨（{@code Head}）的支点，head 是头骨的转动（看头朝哪）。
     */
    private record Anchors(Vector3f left, Vector3f right, Vector3f neck, Matrix3f head) {
        static @Nullable Anchors of(ILocationModel model) {
            Vector3f left = locator(model.leftHandBones()), right = locator(model.rightHandBones());
            List<? extends ILocationBone> chain = model.headBones();
            if (left == null || right == null || chain.isEmpty()) return null;
            // 从层的坐标一路走到头骨，同女仆模组画骨骼时（RenderUtils.prepMatrixForBone）
            PoseStack p = new PoseStack();
            for (ILocationBone bone : chain) RenderUtils.prepMatrixForBone(p, bone);
            Matrix4f head = new Matrix4f(p.last().pose());
            ILocationBone bone = chain.get(chain.size() - 1);
            Vector3f neck = head.transformPosition(new Vector3f(bone.getPivotX(), bone.getPivotY(), bone.getPivotZ()).div(16)).mul(16);
            return new Anchors(left, right, neck, head.get3x3(new Matrix3f()));
        }

        /** 挂点在层的坐标里的位置（模型像素），同女仆模组的手持物品层（{@link RenderUtils#prepMatrixForLocator}）。 */
        private static @Nullable Vector3f locator(List<? extends ILocationBone> bones) {
            if (bones.isEmpty()) return null;
            PoseStack p = new PoseStack();
            RenderUtils.prepMatrixForLocator(p, bones);
            return p.last().pose().transformPosition(new Vector3f()).mul(16);
        }
    }

    /**
     * GeckoLib 模型的乐器、琴弓、琴槌：女仆模组按 {@code maid_musician.animation.json} 的默认动作算好骨骼、画完身体，
     * 这一层在那之后按手的挂点和头骨把它们画上去（{@link Extension} 加的）。
     */
    private static final class GeckoLayer extends GeoLayerRenderer<Mob, IGeoEntityRenderer<Mob>> {
        GeckoLayer(IGeoEntityRenderer<Mob> renderer) {
            super(renderer);
        }

        @Override
        public GeoLayerRenderer<Mob, IGeoEntityRenderer<Mob>> copy(IGeoEntityRenderer<Mob> renderer) {
            return new GeckoLayer(renderer);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, Mob mob, float limbSwing, float limbSwingAmount,
                           float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!(mob instanceof EntityMaid maid)) return;
            Look look = LOOKS.get(maid);
            if (look == null || !look.gecko) return;
            renderGecko(pose, buffers, light, maid, getLocationModel(mob));
        }
    }

    private static void renderGecko(PoseStack pose, MultiBufferSource buffers, int light, EntityMaid maid, @Nullable ILocationModel model) {
        MusicianPoses.Drawing d = MusicianPoses.drawing(maid);
        if (d == null || model == null) return;
        HandheldPoses.Held held = d.held;
        MusicianPoses.Musician m = d.musician;
        var carried = d.carried;
        if (held == null || (m == null && carried == null) || maid.isInvisible()) return;
        Anchors a = Anchors.of(model);
        if (a == null) return;
        if (m == null) {
            handheld(pose, buffers, light, carried, a, held);
            return;
        }
        // 层的坐标里 1 个模型像素在世界里有多大（女仆模型按 render_entity_scale 缩放过）
        float scale = (float) Math.cbrt(pose.last().pose().determinant3x3());
        if (!(scale > 1e-4f)) return;
        switch (m.mode()) {
            case HANDHELD -> handheld(pose, buffers, light, m.kind(), a, held);
            case BOWED -> {
                if (held.bow() == null) return;
                // 弓根在右手，弓杆从琴弦上弓毛该压的那一点过（同玩家，HeldRig.seatedBlock）
                Vector3f contact = toLayer(HeldRig.stringContact(m.kind()), m.feet(), scale);
                if (DEBUG_BOW++ % 60 == 0) LogUtils.getLogger().info("DEBUGBOW right {} left {} neck {} contact {} scale {} feet {} pose {}",
                    a.right(), a.left(), a.neck(), contact, scale, m.feet(), pose.last().pose());
                draw(pose, buffers, light, span(a.right(), contact, BOW_FROG, BOW_TIP, BOW_HAIR, FORWARD,
                    HeldRig.bowScale(m.kind()), false), held.bow());
            }
            case MALLETS -> {
                if (held.bow() == null) return;
                for (boolean left : new boolean[]{true, false}) {
                    Vector3f target = left ? m.left() : m.right();
                    if (target == null) continue;
                    draw(pose, buffers, light, HeldRig.along(left ? a.left() : a.right(), toLayer(target, m.feet(), scale),
                        StationPoses.toolGrip(m.kind()), StationPoses.toolHead(m.kind())), held.bow());
                }
            }
            default -> { }
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
    private static void handheld(PoseStack pose, MultiBufferSource buffers, int light, dev.starbloom.orchestra.music.InstrumentKind kind,
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
                    draw(pose, buffers, light, span(a.right(), contact, BOW_FROG, BOW_TIP, BOW_HAIR, back, scale, false), held.bow());
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
        draw(pose, buffers, light, body, held.instrument());
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
        Matrix3f item = new Matrix3f(ia, iu, ic), world = new Matrix3f(wa, wu, wc);
        Matrix3f r = world.mul(item.transpose());
        if (fit && length > 1e-4f) scale *= Math.max(0.85f, Math.min(1.25f, distance / (length * scale)));
        return new Matrix4f().translate(p).mul(new Matrix4f().set(r)).scale(scale).translate(8 - from.x, 8 - from.y, 8 - from.z);
    }

    /** 画在层的坐标（模型像素）里：两种模型的层都是。 */
    private static void draw(PoseStack pose, MultiBufferSource buffers, int light, Matrix4f pixels, HeldModel item) {
        pose.pushPose();
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        pose.mulPoseMatrix(pixels);
        pose.scale(16, 16, 16);
        item.render(pose, buffers, light);
        pose.popPose();
    }
}
