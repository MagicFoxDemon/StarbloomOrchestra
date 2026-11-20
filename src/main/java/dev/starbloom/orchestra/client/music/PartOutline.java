package dev.starbloom.orchestra.client.music;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.starbloom.orchestra.StarbloomOrchestra;
import dev.starbloom.orchestra.music.InstrumentBlock;
import dev.starbloom.orchestra.music.orchestra.Orchestra;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * 选中声部的描边（docs/ORCHESTRA.md 第 8 节）：指挥选中一个声部时，演奏它的乐器外面画一圈这个声部颜色的线。
 * 看得见的轮廓是实线；被别的东西挡住的乐器（或乐器被挡住的那一截）也描出来，淡一些；这个声部有音进来时亮一下。
 *
 * <p>做法：乐器的方块模型（真实的几何，透明的像素不算）在世界画完之后按世界的投影画到一张和屏幕一样大的空白图上：
 * g 是剪影，r 是其中没被挡住的部分（和半透明之前留下的深度比，所以玻璃、水挡不住）。再用一个全屏通道在剪影外面一圈画线，
 * 附近有看得见的剪影就是实线，只有被挡住的剪影就是淡线。手挡在前面的地方不画（全屏四边形放在远平面上做深度测试，
 * 这时主画面的深度里只有手）。时机在世界、手和原版后处理都画完、界面开始之前（{@code PartOutlineMixin}），
 * 不经过 Sodium 会替换的区块渲染，所以有没有 Sodium 走的都是同一条路（没有在装了 Sodium 的游戏里试过）。
 * 线宽按屏幕的像素（720p 时 2 像素、1080p 约 2.7 像素，最多 5 像素），线条是纯色，没有贴在屏幕上的纹理。
 *
 * <p>两个着色器是 {@code assets/starbloom_orchestra/shaders/core/part_outline_mask.*}（剪影）和
 * {@code part_outline.*}（全屏描边），在 {@link RegisterShadersEvent} 里加载，资源重载时由原版关掉旧的。
 * 剪影图、深度的那份拷贝是自己的 {@link TextureTarget}，窗口大小变了跟着改，换掉时释放。
 *
 * <p>只有选了这个声部的人看得到：选中是各自客户端的事，并且要拿着绑在这座指挥台上的指挥棒，或者开着它的界面。
 * 没选中时一个通道都不跑。
 */
@Mod.EventBusSubscriber(modid = StarbloomOrchestra.ID, value = Dist.CLIENT)
public final class PartOutline {
    private PartOutline() {}

    /** 每个顶点：相对乐器主格的位置 3、图集 UV 2。 */
    private static final int STRIDE = 5;
    /** 看得见的线、被挡住的线的不透明度。 */
    private static final float SEEN_ALPHA = 0.95f, HIDDEN_ALPHA = 0.36f;
    /** 一帧最多画这么多个顶点（32 件乐器绰绰有余）。 */
    private static final int MAX_VERTICES = 1 << 17;

    /** 一件乐器的几何：各格的方块状态没变就一直用。 */
    private record Shape(List<BlockState> states, float[] data, @Nullable ResourceLocation atlas) {}

    private static final Map<BlockPos, Shape> SHAPES = new HashMap<>();
    private static Conducting.@Nullable Selection active;
    private static long passes;
    private static @Nullable RenderTarget depth, mask;
    private static @Nullable BufferBuilder meshBuffer;
    /** 剪影、全屏描边两个着色器（{@link RegisterShadersEvent} 加载）。 */
    private static @Nullable ShaderInstance maskShader, outlineShader;
    /** 世界渲染时镜头的朝向（世界坐标轴到镜头）和位置，和 {@link #PROJECTION} 同一时刻记下。 */
    private static final Matrix4f VIEW = new Matrix4f();
    private static Vec3 cameraPos = Vec3.ZERO;

    /** 通道一共跑了多少次（客户端场景确认没选中时不跑）。 */
    public static long passes() { return passes; }

    /**
     * 世界渲染真正用的投影矩阵（含视角晃动、受伤倾斜、反胃的扭曲），每帧在
     * {@link RenderLevelStageEvent.Stage#AFTER_BLOCK_ENTITIES} 从事件里记下。
     */
    public static final Matrix4f PROJECTION = new Matrix4f();

    /** 投影乘镜头朝向：把相对镜头的世界坐标换到屏幕上。 */
    private static Matrix4f viewProjection() {
        return new Matrix4f(PROJECTION).mul(VIEW);
    }

    /** 模组总线上的事件。 */
    @Mod.EventBusSubscriber(modid = StarbloomOrchestra.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        static void shaders(RegisterShadersEvent event) throws IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), StarbloomOrchestra.id("part_outline_mask"),
                    DefaultVertexFormat.POSITION_TEX), shader -> maskShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), StarbloomOrchestra.id("part_outline"),
                    DefaultVertexFormat.POSITION), shader -> outlineShader = shader);
        }
    }

    /** 这一帧要不要描：选中了声部、有人演奏它，并且是自己在指挥这座指挥台（拿着绑定的棒，或开着它的界面）。 */
    static Conducting.@Nullable Selection wanted() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return null;
        Conducting.Selection s = Conducting.selection();
        if (s == null || s.members().isEmpty()) return null;
        if (mc.screen instanceof PodiumScreen screen && screen.podium().equals(s.podium())) return s;
        for (ItemStack stack : new ItemStack[]{mc.player.getMainHandItem(), mc.player.getOffhandItem()}) {
            GlobalPos bound = Orchestra.batonPodium(stack);
            if (bound != null && bound.pos().equals(s.podium()) && bound.dimension().equals(mc.level.dimension())) return s;
        }
        return null;
    }

    /**
     * 实体、方块实体画完之后（半透明之前）：记下这一帧的投影、镜头，并留一份深度，玻璃和水挡不住线。
     * 拷完把原来绑着的帧缓冲绑回去。
     */
    @SubscribeEvent
    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        PROJECTION.set(event.getProjectionMatrix());
        VIEW.set(event.getPoseStack().last().pose());
        cameraPos = event.getCamera().getPosition();
        active = wanted();
        if (active == null) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int bound = GlStateManager.getBoundFramebuffer();
        depth = fit(depth, main, true);
        depth.copyDepthFrom(main);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, bound);
    }

    /**
     * 和主画面一样大的目标：没有、或深度带不带模板和主画面不一样时重建（深度只能在同样的格式之间拷），
     * 大小不对时改大小。建、改大小时原版会打开深度测试，这里改回原来的样子。
     */
    private static RenderTarget fit(@Nullable RenderTarget t, RenderTarget main, boolean withDepth) {
        boolean stencil = withDepth && main.isStencilEnabled();
        boolean rebuild = t == null || t.isStencilEnabled() != stencil;
        if (!rebuild && t.width == main.width && t.height == main.height) return t;
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        if (rebuild) {
            if (t != null) t.destroyBuffers();
            t = new TextureTarget(main.width, main.height, withDepth, Minecraft.ON_OSX);
            if (stencil) t.enableStencil();
        } else {
            t.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        if (depthTest) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();
        return t;
    }

    /** 由 Mixin 在世界和手都画完之后、界面之前调用（原版的后处理之后）。 */
    public static void draw() {
        Conducting.Selection s = active;
        active = null;
        ShaderInstance maskProgram = maskShader, outlineProgram = outlineShader;
        if (s == null || depth == null || maskProgram == null || outlineProgram == null) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (mc.level == null || depth.width != main.width || depth.height != main.height) return;
        if (meshBuffer == null) meshBuffer = new BufferBuilder(1 << 18);
        if (SHAPES.size() > 96) SHAPES.clear();

        Vec3 camera = cameraPos;
        meshBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        ResourceLocation atlas = null;
        int vertices = 0;
        for (BlockPos member : s.members()) {
            Shape shape = shape(member);
            if (shape == null || shape.data.length == 0) continue;
            if (atlas == null) atlas = shape.atlas;
            float ox = (float) (member.getX() - camera.x), oy = (float) (member.getY() - camera.y), oz = (float) (member.getZ() - camera.z);
            float[] d = shape.data;
            for (int i = 0; i + STRIDE <= d.length && vertices < MAX_VERTICES; i += STRIDE, vertices++) {
                meshBuffer.vertex(d[i] + ox, d[i + 1] + oy, d[i + 2] + oz).uv(d[i + 3], d[i + 4]).endVertex();
            }
        }
        if (atlas == null || vertices == 0) {
            meshBuffer.discard();
            return;
        }
        BufferBuilder.RenderedBuffer mesh = meshBuffer.end();
        // 建、改尺寸的时候 RenderTarget 会把帧缓冲换回 0；放在所有提前返回之后，后面一定走到 main.bindWrite 换回主画面
        mask = fit(mask, main, false);

        int argb = s.color();
        float width = Math.min(5f, Math.max(2f, main.height / 400f));
        maskProgram.safeGetUniform("ViewProj").set(viewProjection());
        outlineProgram.safeGetUniform("Color").set((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, SEEN_ALPHA);
        outlineProgram.safeGetUniform("Style").set(width, HIDDEN_ALPHA, Conducting.pulse(s), 0f);

        // 剪影：清成全 0，叠加混合（r、g 只要有一个面写过就是 1），不做深度测试、不剔除背面。
        mask.setClearColor(0f, 0f, 0f, 0f);
        mask.clear(Minecraft.ON_OSX);
        mask.bindWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(() -> maskProgram);
        RenderSystem.setShaderTexture(0, atlas);
        maskProgram.setSampler("DepthSampler", depth.getDepthTextureId());
        BufferUploader.drawWithShader(mesh);

        // 描边：叠到主画面上（半透明混合）；四边形在远平面，LEQUAL 深度测试挡掉手（深度只写着手）。
        main.bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(() -> outlineProgram);
        outlineProgram.setSampler("InSampler", mask.getColorTextureId());
        BufferBuilder quad = Tesselator.getInstance().getBuilder();
        quad.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        quad.vertex(0f, 0f, 0f).endVertex();
        quad.vertex(1f, 0f, 0f).endVertex();
        quad.vertex(1f, 1f, 0f).endVertex();
        quad.vertex(0f, 1f, 0f).endVertex();
        BufferUploader.drawWithShader(quad.end());

        // 还原成渲染类型之间的默认状态。
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        passes++;
    }

    /** 乐器（主格在 member）各格的模型面，位置相对主格；不是乐器了时为 null。 */
    private static @Nullable Shape shape(BlockPos member) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        BlockState mainState = level.getBlockState(member);
        if (!(mainState.getBlock() instanceof InstrumentBlock block)) {
            SHAPES.remove(member);
            return null;
        }
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos c : block.cells(mainState, member)) cells.add(c.immutable());
        List<BlockState> states = new ArrayList<>(cells.size());
        for (BlockPos c : cells) states.add(level.getBlockState(c));
        Shape cached = SHAPES.get(member);
        if (cached != null && cached.states.equals(states)) return cached;

        float[] data = new float[STRIDE * 4 * 64];
        int size = 0;
        ResourceLocation atlas = null;
        RandomSource random = RandomSource.create();
        Direction[] sides = Direction.values();
        for (int c = 0; c < cells.size(); c++) {
            BlockPos cell = cells.get(c);
            BlockState state = states.get(c);
            if (!(state.getBlock() instanceof InstrumentBlock)) continue;
            // 和区块里画方块一样取模型面：同一个种子（每个面重新播种），带上方块的模型数据，不分渲染类型。
            BakedModel model = mc.getBlockRenderer().getBlockModel(state);
            ModelData atCell = level.getModelDataManager().getAt(cell);
            ModelData modelData = model.getModelData(level, cell, state, atCell != null ? atCell : ModelData.EMPTY);
            long seed = state.getSeed(cell);
            float ox = cell.getX() - member.getX(), oy = cell.getY() - member.getY(), oz = cell.getZ() - member.getZ();
            for (int d = 0; d <= sides.length; d++) {
                random.setSeed(seed);
                for (BakedQuad q : model.getQuads(state, d < sides.length ? sides[d] : null, random, modelData, null)) {
                    if (atlas == null) atlas = q.getSprite().atlasLocation();
                    if (size + STRIDE * 4 > data.length) data = Arrays.copyOf(data, data.length * 2);
                    int[] v = q.getVertices();
                    for (int k = 0; k < 4; k++) size = put(data, size, v, k, ox, oy, oz);
                }
            }
        }
        Shape shape = new Shape(states, Arrays.copyOf(data, size), atlas);
        SHAPES.put(member, shape);
        return shape;
    }

    /** 方块模型的顶点数据（{@link DefaultVertexFormat#BLOCK} 的排法）里第 vertex 个顶点的位置和图集 UV。 */
    private static int put(float[] data, int at, int[] v, int vertex, float ox, float oy, float oz) {
        int base = vertex * IQuadTransformer.STRIDE;
        data[at] = Float.intBitsToFloat(v[base + IQuadTransformer.POSITION]) + ox;
        data[at + 1] = Float.intBitsToFloat(v[base + IQuadTransformer.POSITION + 1]) + oy;
        data[at + 2] = Float.intBitsToFloat(v[base + IQuadTransformer.POSITION + 2]) + oz;
        data[at + 3] = Float.intBitsToFloat(v[base + IQuadTransformer.UV0]);
        data[at + 4] = Float.intBitsToFloat(v[base + IQuadTransformer.UV0 + 1]);
        return at + STRIDE;
    }
}
