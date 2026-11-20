package dev.starbloom.orchestra.client.music;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.math.Transformation;
import dev.starbloom.orchestra.StarbloomOrchestra;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.IModelBuilder;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.SimpleUnbakedGeometry;
import net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 元素可以任意转动的方块 / 物品模型（几何加载器 `starbloom_orchestra:rotated_cuboids`）。
 *
 * <p>乐器模型按 26.1 的模型格式画：元素的 `rotation` 可以是 `x`/`y`/`z` 三个欧拉角（先绕 X、再绕 Y、最后绕 Z，
 * 和 26.1 的 `rotationZYX` 一样），也可以是任意角度的 `axis` + `angle`。1.21.1 原版的解析器只认一根轴、
 * ±22.5°/±45°，别的直接报错，所以这类模型由生成脚本（tools/instrument_kit.py）写成
 * `"loader": "starbloom_orchestra:rotated_cuboids"`，元素放在 `"cuboids"` 里（放在 `"elements"` 里原版会先解析、报错）。
 * 不需要转动的模型仍是原版格式。
 *
 * <p>烘焙和原版一样：元素先绕自己的 `origin` 转，再按方块状态的旋转绕方块中心转，朝向（明暗、剔除用）和法线按转完的顶点算；
 * 转动的元素不重排顶点顺序（原版对有 rotation 的元素也不重排）。`rescale` 按 26.1 的算法：每根轴按转完后最长的分量放大回去。
 * 面的 uv、cullface、tintindex、neoforge_data 都交给原版的元素解析器。
 */
@EventBusSubscriber(modid = StarbloomOrchestra.ID, value = Dist.CLIENT)
public final class RotatedCuboidModel extends SimpleUnbakedGeometry<RotatedCuboidModel> {
    /** 模型 JSON 里 `loader` 写的名字。 */
    public static final ResourceLocation LOADER = StarbloomOrchestra.id("rotated_cuboids");
    /** 放元素的键（不能叫 `elements`）。 */
    public static final String CUBOIDS = "cuboids";

    private static final FaceBakery BAKERY = new FaceBakery();
    /** 转动的元素先按 0° 烘焙（不转、也不重排顶点），再自己转顶点。 */
    private static final BlockElementRotation NO_TURN = new BlockElementRotation(new Vector3f(), Direction.Axis.Y, 0.0F, false);
    private static final Vector3f BLOCK_MIDDLE = new Vector3f(0.5F, 0.5F, 0.5F);

    /** 一个元素；没写 rotation 时 turn 为空（交给原版烘焙），否则是绕 origin（方块坐标 / 16）的变换。 */
    record Cuboid(BlockElement element, @Nullable Matrix4f turn, Vector3f origin) {}

    private final List<Cuboid> cuboids;

    private RotatedCuboidModel(List<Cuboid> cuboids) {
        this.cuboids = cuboids;
    }

    @SubscribeEvent
    static void registerLoader(ModelEvent.RegisterGeometryLoaders event) {
        event.register(LOADER, Loader.INSTANCE);
    }

    @Override
    protected void addQuads(IGeometryBakingContext context, IModelBuilder<?> builder, ModelBaker baker,
                            Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState) {
        // 同 NeoForge 的 ElementsModel：模型自己的根变换并进方块状态的变换
        Transformation root = context.getRootTransform();
        if (!root.isIdentity()) modelState = UnbakedGeometryHelper.composeRootTransformIntoModelState(modelState, root);
        for (Cuboid cuboid : cuboids) {
            BlockElement element = cuboid.element();
            for (Direction direction : element.faces.keySet()) {
                BlockElementFace face = element.faces.get(direction);
                TextureAtlasSprite sprite = spriteGetter.apply(context.getMaterial(face.texture()));
                BakedQuad quad = cuboid.turn() == null
                    ? BlockModel.bakeFace(element, face, sprite, direction, modelState)
                    : bakeTurned(cuboid, face, sprite, direction, modelState);
                if (face.cullForDirection() == null) builder.addUnculledFace(quad);
                else builder.addCulledFace(modelState.getRotation().rotateTransform(face.cullForDirection()), quad);
            }
        }
    }

    /** 转动的元素的一个面：按不转烘焙，顶点绕 origin 转、再按方块状态绕方块中心转，最后重算朝向和法线。 */
    private static BakedQuad bakeTurned(Cuboid cuboid, BlockElementFace face, TextureAtlasSprite sprite, Direction direction, ModelState state) {
        BlockElement element = cuboid.element();
        Transformation rotation = state.getRotation();
        BlockElementFace baked = face;
        // uvlock 只看方块状态的旋转（原版 FaceBakery 也是先按它换 uv、再烘焙）
        if (state.isUvLocked()) {
            baked = new BlockElementFace(face.cullForDirection(), face.tintIndex(), face.texture(),
                FaceBakery.recomputeUVs(face.uv(), direction, rotation), face.faceData(), face.parent());
        }
        BakedQuad flat = BAKERY.bakeQuad(element.from, element.to, baked, sprite, direction, BlockModelRotation.X0_Y0, NO_TURN, element.shade);
        int[] vertices = flat.getVertices().clone();
        Matrix4f model = rotation == Transformation.identity() ? null : rotation.getMatrix();
        Vector3f pos = new Vector3f();
        for (int i = 0; i < 4; i++) {
            int at = i * FaceBakery.VERTEX_INT_SIZE;
            pos.set(Float.intBitsToFloat(vertices[at]), Float.intBitsToFloat(vertices[at + 1]), Float.intBitsToFloat(vertices[at + 2]));
            turnAbout(pos, cuboid.origin(), cuboid.turn());
            if (model != null) turnAbout(pos, BLOCK_MIDDLE, model);
            vertices[at] = Float.floatToRawIntBits(pos.x());
            vertices[at + 1] = Float.floatToRawIntBits(pos.y());
            vertices[at + 2] = Float.floatToRawIntBits(pos.z());
        }
        Direction facing = FaceBakery.calculateFacing(vertices);
        ClientHooks.fillNormal(vertices, facing);
        return new BakedQuad(vertices, flat.getTintIndex(), facing, sprite, flat.isShade(), flat.hasAmbientOcclusion());
    }

    private static void turnAbout(Vector3f pos, Vector3f origin, Matrix4f transform) {
        pos.sub(origin);
        transform.transformPosition(pos);
        pos.add(origin);
    }

    /** 读 `rotation`：返回绕 origin 的变换（0° 也返回单位矩阵：和原版一样，写了 rotation 的元素不重排顶点）。 */
    static Matrix4f turn(JsonObject rotation) {
        Matrix4f turn;
        if (rotation.has("axis") || rotation.has("angle")) {
            String name = GsonHelper.getAsString(rotation, "axis");
            Direction.Axis axis = Direction.Axis.byName(name.toLowerCase(Locale.ROOT));
            if (axis == null) throw new JsonParseException("Invalid rotation axis: " + name);
            float angle = GsonHelper.getAsFloat(rotation, "angle");
            if (angle == 0.0F) return new Matrix4f();
            Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, axis);
            turn = new Matrix4f().rotation((float) Math.toRadians(angle),
                new Vector3f(positive.getStepX(), positive.getStepY(), positive.getStepZ()));
        } else if (rotation.has("x") || rotation.has("y") || rotation.has("z")) {
            float x = GsonHelper.getAsFloat(rotation, "x", 0.0F);
            float y = GsonHelper.getAsFloat(rotation, "y", 0.0F);
            float z = GsonHelper.getAsFloat(rotation, "z", 0.0F);
            if (x == 0.0F && y == 0.0F && z == 0.0F) return new Matrix4f();
            turn = new Matrix4f().rotationZYX((float) Math.toRadians(z), (float) Math.toRadians(y), (float) Math.toRadians(x));
        } else {
            throw new JsonParseException("Missing rotation value, expected either 'axis' and 'angle' or 'x', 'y' and 'z'");
        }
        if (GsonHelper.getAsBoolean(rotation, "rescale", false)) {
            // 26.1 CuboidRotation.computeRescale：每根轴转完后取最长分量的倒数
            Vector3f scale = new Vector3f();
            Vector3f unit = new Vector3f();
            for (int a = 0; a < 3; a++) {
                unit.set(a == 0 ? 1 : 0, a == 1 ? 1 : 0, a == 2 ? 1 : 0);
                turn.transformDirection(unit);
                float max = Math.max(Math.max(Math.abs(unit.x), Math.abs(unit.y)), Math.abs(unit.z));
                scale.setComponent(a, 1.0F / max);
            }
            turn.scale(scale);
        }
        return turn;
    }

    /** 解析 `cuboids`：每个元素去掉 rotation 交给原版的元素解析器（面、uv、边界检查），rotation 自己读。 */
    public static final class Loader implements IGeometryLoader<RotatedCuboidModel> {
        public static final Loader INSTANCE = new Loader();

        private Loader() {}

        @Override
        public RotatedCuboidModel read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
            if (!json.has(CUBOIDS)) throw new JsonParseException("A rotated_cuboids model must have a \"" + CUBOIDS + "\" member.");
            List<Cuboid> cuboids = new ArrayList<>();
            for (JsonElement entry : GsonHelper.getAsJsonArray(json, CUBOIDS)) {
                JsonObject element = entry.getAsJsonObject().deepCopy();
                JsonElement rotation = element.remove("rotation");
                // 26.1 元素自己的发光等级换成 NeoForge 的面数据（方块光照）
                JsonElement light = element.remove("light_emission");
                if (light != null && !element.has("neoforge_data")) {
                    JsonObject data = new JsonObject();
                    data.add("block_light", light);
                    element.add("neoforge_data", data);
                }
                BlockElement parsed = context.deserialize(element, BlockElement.class);
                Matrix4f turn = null;
                Vector3f origin = new Vector3f();
                if (rotation != null) {
                    JsonObject r = rotation.getAsJsonObject();
                    var o = GsonHelper.getAsJsonArray(r, "origin");
                    if (o.size() != 3) throw new JsonParseException("Expected 3 origin values, found: " + o.size());
                    origin.set(o.get(0).getAsFloat() / 16.0F, o.get(1).getAsFloat() / 16.0F, o.get(2).getAsFloat() / 16.0F);
                    turn = turn(r);
                }
                cuboids.add(new Cuboid(parsed, turn, origin));
            }
            return new RotatedCuboidModel(cuboids);
        }
    }
}
