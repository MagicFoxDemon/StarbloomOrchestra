package dev.starbloom.orchestra.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.starbloom.orchestra.client.music.PartOutline;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 指挥选中的声部的描边（{@link PartOutline}）：记下世界渲染用的投影矩阵，画面画完之后把描边叠上去。
 * 和别的模组的全屏效果在同一个位置（雾的 endFrame 之前），当成画面的一部分。
 */
@Mixin(GameRenderer.class)
public abstract class PartOutlineMixin {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setProjectionMatrix(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/ProjectionType;)V", ordinal = 0))
    private void starbloomOrchestra$projection(DeltaTracker deltaTracker, CallbackInfo ci, @Local(ordinal = 0) Matrix4f projection) {
        PartOutline.PROJECTION.set(projection);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void starbloomOrchestra$draw(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        if (advanceGameTime) PartOutline.draw();
    }
}
