package dev.starbloom.orchestra.client.music;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.starbloom.orchestra.StarbloomOrchestra;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 演奏时画在身上的一件模型：拿在手上演奏的乐器、琴弓、琴槌（docs/ORCHESTRA.md 第 14–16 节）。只供渲染，不是物品的模型。
 * <p>
 * 模型是 models/item/ 下单独加载的那一份（{@link ModelEvent.RegisterAdditional}），
 * 按 {@link ItemDisplayContext#NONE} 画：这些模型都没有 none 的显示变换，所以以方块为单位、中心在原点（先往回挪半格），
 * 同 26.1 里 items/held/ 下的物品模型定义经 ItemStackRenderState 画出来的样子。stack 只决定渲染类型、染色和附魔光效
 * （用的都是乐器本身、不带附魔）。
 *
 * @param stack 画的时候交给物品渲染器的物品
 * @param model 单独加载的模型
 */
@Mod.EventBusSubscriber(modid = StarbloomOrchestra.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public record HeldModel(ItemStack stack, ResourceLocation model) {
    /** 要单独加载的模型（models/item/ 下）：手持乐器拿在手上演奏的样子、琴弓、各种琴槌。 */
    static final List<String> MODELS = List.of(
        "hexen_violin_held", "hexen_viola_held", "hexen_flute_held", "hexen_oboe_held", "hexen_bassoon_held", "hexen_trumpet_held",
        "shinobue_held", "kagura_suzu_held", "shakuhachi_held", "hexen_violin_bow",
        "hexen_glockenspiel_mallet_held", "hexen_chime_hammer_held", "hexen_timpani_mallet_held", "hexen_bass_drum_beater_held",
        "hexen_snare_stick_held", "hexen_cymbal_mallet_held", "hexen_triangle_beater_held");

    /** models/item/&lt;name&gt;.json 这一份模型，画的时候用 stack。 */
    static HeldModel of(ItemStack stack, String name) {
        return new HeldModel(stack, location(name));
    }

    static ResourceLocation location(String name) {
        return StarbloomOrchestra.id("item/" + name);
    }

    /** 画在 pose 处（方块为单位、模型中心在原点），用 light 的光照，不带受伤变红的覆盖色。 */
    public void render(PoseStack pose, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel baked = mc.getModelManager().getModel(model);
        mc.getItemRenderer().render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, OverlayTexture.NO_OVERLAY, baked);
    }

    /** 加载模型时把这些模型也带上（1.20.1 不读 assets/&lt;命名空间&gt;/items/ 下的物品模型定义）。 */
    @SubscribeEvent
    static void register(ModelEvent.RegisterAdditional event) {
        for (String name : MODELS) event.register(location(name));
    }
}
