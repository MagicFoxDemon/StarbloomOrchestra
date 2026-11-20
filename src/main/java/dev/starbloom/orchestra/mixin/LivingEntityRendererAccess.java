package dev.starbloom.orchestra.mixin;

import java.util.List;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 渲染器的层（原版的 {@code layers} 是 protected）：装了车万女仆时 {@code MaidMusicians} 把女仆模组画手持物品的那一层换成一层外壳，
 * 女仆演奏时不画她手里原本的东西（26.1 是清空渲染状态里两只手的物品）。只读写这个列表，不改原版的行为。
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccess {
    @Accessor("layers")
    List<RenderLayer<?, ?>> starbloom_orchestra$layers();
}
