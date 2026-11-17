package dev.starbloom.orchestra.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import dev.starbloom.orchestra.client.music.LoopedSoundBuffer;
import java.util.OptionalInt;
import javax.sound.sampled.AudioFormat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 乐器采样的循环点：缓冲第一次上传到 OpenAL 之后设上（见 {@link LoopedSoundBuffer}）。其他声音不受影响。 */
@Mixin(SoundBuffer.class)
public abstract class SoundBufferLoopMixin implements LoopedSoundBuffer {
    @Shadow private boolean hasAlBuffer;
    @Shadow private int alBuffer;
    @Shadow @Final private AudioFormat format;
    @Shadow @Final private int size;
    @Unique private int starbloom_orchestra$loopStart = -1;
    @Unique private int starbloom_orchestra$loopEnd = -1;
    @Unique private boolean starbloom_orchestra$loopSet;

    @Override
    public void starbloom_orchestra$loop(int start, int end) {
        this.starbloom_orchestra$loopStart = start;
        this.starbloom_orchestra$loopEnd = end;
    }

    @Inject(method = "getAlBuffer", at = @At("RETURN"))
    private void starbloom_orchestra$applyLoop(CallbackInfoReturnable<OptionalInt> cir) {
        if (starbloom_orchestra$loopSet || starbloom_orchestra$loopEnd <= 0 || !hasAlBuffer) return;
        starbloom_orchestra$loopSet = true;
        LoopedSoundBuffer.apply(alBuffer, format, size, starbloom_orchestra$loopStart, starbloom_orchestra$loopEnd);
    }
}
