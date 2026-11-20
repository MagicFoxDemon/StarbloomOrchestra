package dev.starbloom.orchestra.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.starbloom.orchestra.client.music.MusicianPoses;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 演奏时（举着手持乐器、坐在琴凳上、站在工位上）两只手里原本拿着的东西不画：乐器、琴弓、琴槌由演奏者的那一层另外画，
 * 弹键盘、竖琴时两只手都在琴上。26.1 是在渲染状态里清空两只手的物品；1.21.1 的手持物品层直接读实体手里的物品，所以在这里跳过
 * （玩家的那一层 {@code PlayerItemInHandLayer} 也走这里）。只看 {@link MusicianPoses#hidesHandItems}。
 */
@Mixin(ItemInHandLayer.class)
public abstract class MusicianHandItemMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At("HEAD"), cancellable = true)
    private void starbloom$hideWhilePlaying(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float limbSwing,
                                            float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
                                            CallbackInfo ci) {
        if (MusicianPoses.hidesHandItems(entity)) ci.cancel();
    }
}
