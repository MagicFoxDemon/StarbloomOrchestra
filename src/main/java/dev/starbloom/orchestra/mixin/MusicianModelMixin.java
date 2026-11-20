package dev.starbloom.orchestra.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.starbloom.orchestra.client.music.MusicianPoses;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 坐在星辰花琴凳上演奏的人形（玩家、僵尸、骷髅……）：在模型自己的 setupAnim 全部跑完之后再摆双臂。
 * 僵尸之类的模型在 setupAnim 末尾还会再抬一次手，所以不能放在 HumanoidModel.setupAnim 里。只在这个人这一帧是演奏者时起作用
 * （{@link MusicianPoses#afterModelSetup}）。
 * 1.21.1 的 {@code LivingEntityRenderer.render} 摆完姿势就地画身体，随后各层（盔甲照着身体抄姿势、乐器层）也都用这里摆好的模型，
 * 不像 26.1 那样画之前再 setupAnim 一次；只有几层套在外面的衣服会自己再 setupAnim，见 {@link MusicianDrawMixin}。
 * 装了车万女仆时，女仆的基岩版模型也从这里摆（{@code MaidMusicians} 登记）：女仆模组的动画在 setupAnim 里还会直接挪画身体用的
 * PoseStack（坐下时往下 0.3 格），所以 setupAnim 之前也交给它一次（{@link MusicianPoses#beforeModelSetup}），好在之后换成琴凳的高度。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MusicianModelMixin {
    @Shadow protected EntityModel<?> model;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void starbloom_orchestra$beforeSetupAnim(LivingEntity entity, float entityYaw, float partialTicks, PoseStack pose, MultiBufferSource buffers,
                                                     int light, CallbackInfo ci) {
        MusicianPoses.beforeModelSetup(model, entity, pose);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
    private void musicianArms(LivingEntity entity, float entityYaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light,
                              CallbackInfo ci) {
        MusicianPoses.afterModelSetup(model, entity, pose);
    }
}
