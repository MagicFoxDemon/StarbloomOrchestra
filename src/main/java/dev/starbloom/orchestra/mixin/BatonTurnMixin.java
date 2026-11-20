package dev.starbloom.orchestra.mixin;

import dev.starbloom.orchestra.client.music.BatonInput;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1 的 Forge 没有 NeoForge 1.21.1 的 {@code CalculatePlayerTurnEvent}：指挥按住左键画拍子时
 * 清掉这一帧累积的鼠标位移并跳过转头，界面停在屏幕上，放开左键后视角不会猛地一跳。
 */
@Mixin(MouseHandler.class)
public abstract class BatonTurnMixin {
    @Shadow
    private double accumulatedDX;
    @Shadow
    private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void starbloom$holdCameraWhileConducting(CallbackInfo ci) {
        if (!BatonInput.isDrawing()) return;
        accumulatedDX = 0;
        accumulatedDY = 0;
        ci.cancel();
    }
}
