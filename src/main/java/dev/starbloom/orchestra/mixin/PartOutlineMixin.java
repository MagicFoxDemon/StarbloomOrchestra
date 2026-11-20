package dev.starbloom.orchestra.mixin;

import dev.starbloom.orchestra.client.music.PartOutline;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 指挥选中的声部的描边（{@link PartOutline}）：画面画完之后把描边叠上去。
 * 位置在 {@code GameRenderer#render} 里世界、手、原版后处理都画完之后，界面开始前清深度的那一次
 * {@code RenderSystem.clear} 之前（这时主画面的深度里只有手），和别的模组的全屏效果在同一个位置，当成画面的一部分。
 * 世界渲染用的投影由 {@link PartOutline} 在渲染阶段事件里记下，不用再从这里取。
 */
@Mixin(GameRenderer.class)
public abstract class PartOutlineMixin {
    @Inject(method = "render(FJZ)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", ordinal = 0))
    private void starbloomOrchestra$draw(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        if (renderLevel) PartOutline.draw();
    }
}
