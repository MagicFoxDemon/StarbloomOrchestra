package dev.starbloom.orchestra.music;

import dev.starbloom.orchestra.music.orchestra.PodiumBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 琴凳、工位上的人往哪里看（docs/ORCHESTRA.md 第 14、15 节）。身体总是朝着座位的方向。
 * 玩家自己转头，最多转 {@link #PLAYER_TURN}°；生物不跟着自己的 AI 东张西望：乐器属于某个乐团时看着指挥台上指挥的脸，
 * 否则看正前方。看指挥时头最多偏 {@link #MOB_TURN}°、俯仰最多 {@link #MOB_PITCH}°（指挥在背后、身侧时就停在那里）。
 * 两端（服务端、客户端）每刻都在 positionRider 里算一遍，乐团信息随乐器方块实体同步，所以两边看的是同一个方向。
 */
public final class MusicianGaze {
    public static final float PLAYER_TURN = 75.0f;
    public static final float MOB_TURN = 60.0f;
    public static final float MOB_PITCH = 20.0f;
    /** 指挥的眼睛在台面上方多高（原版玩家站立时的眼高）。 */
    private static final double CONDUCTOR_EYES = 1.62;

    private MusicianGaze() {}

    /**
     * @param seat 座位（朝向就是身体的朝向）
     * @param member 这个座位演奏的乐器（加入乐团的方块）；没有时为 null
     */
    public static void apply(Entity seat, Entity passenger, @Nullable BlockPos member) {
        float body = seat.getYRot();
        passenger.setYBodyRot(body);
        if (passenger instanceof Player) {
            float diff = Mth.wrapDegrees(passenger.getYRot() - body);
            float clamped = Mth.clamp(diff, -PLAYER_TURN, PLAYER_TURN);
            passenger.yRotO += clamped - diff;
            passenger.setYRot(passenger.getYRot() + clamped - diff);
            passenger.setYHeadRot(passenger.getYRot());
            return;
        }
        float yaw = body, pitch = 0;
        Vec3 conductor = conductorEyes(seat, member);
        if (conductor != null) {
            Vec3 d = conductor.subtract(passenger.getEyePosition());
            double flat = Math.sqrt(d.x * d.x + d.z * d.z);
            if (flat > 1.0e-3) {
                float toward = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0f;
                yaw = body + Mth.clamp(Mth.wrapDegrees(toward - body), -MOB_TURN, MOB_TURN);
                pitch = Mth.clamp((float) (-Mth.atan2(d.y, flat) * Mth.RAD_TO_DEG), -MOB_PITCH, MOB_PITCH);
            }
        }
        passenger.setYRot(yaw);
        passenger.yRotO = yaw;
        passenger.setYHeadRot(yaw);
        passenger.setXRot(pitch);
        passenger.xRotO = pitch;
    }

    /** member 所属乐团的指挥台上，指挥的眼睛在世界里的位置；不属于乐团、指挥台不在了时为 null。 */
    static @Nullable Vec3 conductorEyes(Entity seat, @Nullable BlockPos member) {
        if (member == null) return null;
        var level = seat.level();
        if (!(level.getBlockEntity(member) instanceof InstrumentBlockEntity be)) return null;
        BlockPos podium = be.orchestra();
        if (podium == null || !level.isLoaded(podium)) return null;
        BlockState state = level.getBlockState(podium);
        if (!(state.getBlock() instanceof PodiumBlock) || !PodiumBlock.isMain(state)) return null;
        return PodiumBlock.standAt(state, podium).add(0, CONDUCTOR_EYES, 0);
    }
}
