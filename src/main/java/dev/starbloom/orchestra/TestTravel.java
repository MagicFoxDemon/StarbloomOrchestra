package dev.starbloom.orchestra;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

/** 客户端测试场景用：把玩家送到某处（先加载那里的区块）。 */
public final class TestTravel {
    private TestTravel() {}

    public static boolean teleport(ServerPlayer player, ServerLevel target, Vec3 pos, float yaw, float pitch) {
        target.getChunk(BlockPos.containing(pos));
        return player.changeDimension(new DimensionTransition(target, pos, Vec3.ZERO, yaw, pitch,
                DimensionTransition.PLAY_PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET))) != null;
    }
}
