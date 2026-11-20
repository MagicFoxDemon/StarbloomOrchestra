package dev.starbloom.orchestra.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.starbloom.orchestra.client.music.MusicianPoses;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 坐在星辰花琴凳上、站在工位上演奏的原版人形生物套在外面的那一层衣服（溺尸的外层、流浪者和骷髅等）。
 * 这些层在 1.20.1 走 {@code RenderLayer.coloredCutoutModelCopyLayerRender}：把身体的姿势抄给自己的模型后又按实体 setupAnim 一次，
 * {@link MusicianModelMixin} 摆的手臂、腿就又回到骑乘的姿势。所以那次 setupAnim 之后再摆一遍
 * （26.1 的身体、盔甲、外层都在 {@code ModelFeatureRenderer.renderModel} 画之前再 setupAnim，那时在那里摆）。
 */
@Mixin(RenderLayer.class)
public abstract class MusicianDrawMixin {
    @Inject(method = "coloredCutoutModelCopyLayerRender(Lnet/minecraft/client/model/EntityModel;Lnet/minecraft/client/model/EntityModel;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFFFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
    private static void logos$musicianPose(EntityModel<?> modelParent, EntityModel<?> model, ResourceLocation texture, PoseStack pose,
                                           MultiBufferSource buffers, int light, LivingEntity entity, float limbSwing, float limbSwingAmount,
                                           float ageInTicks, float netHeadYaw, float headPitch, float partialTick, float red, float green, float blue,
                                           CallbackInfo ci) {
        MusicianPoses.afterModelSetup(model, entity);
    }
}
