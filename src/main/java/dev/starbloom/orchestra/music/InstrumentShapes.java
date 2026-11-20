package dev.starbloom.orchestra.music;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 碰撞形状的水平旋转：1.21.1 的 {@code Shapes} 还没有 {@code rotateHorizontal}，这里按方块中心绕竖轴转，
 * 结果与新版 {@code Shapes.rotateHorizontal(north)} 相同（NORTH 原样，EAST/SOUTH/WEST 依次顺时针转 90°/180°/270°，从上往下看）。
 */
public final class InstrumentShapes {
    private InstrumentShapes() {
    }

    /** 按 FACING=north 给出的形状，得到四个水平朝向各自的形状。 */
    public static Map<Direction, VoxelShape> rotateHorizontal(VoxelShape north) {
        Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
        out.put(Direction.NORTH, north);
        out.put(Direction.EAST, rotate(north, 1));
        out.put(Direction.SOUTH, rotate(north, 2));
        out.put(Direction.WEST, rotate(north, 3));
        return out;
    }

    /** 绕方块中心顺时针转 quarter 个 90°（从上往下看）：北边的点转到东边。 */
    private static VoxelShape rotate(VoxelShape shape, int quarter) {
        VoxelShape result = Shapes.empty();
        for (AABB box : shape.toAabbs()) {
            double x0 = box.minX, x1 = box.maxX, z0 = box.minZ, z1 = box.maxZ;
            double nx0, nx1, nz0, nz1;
            switch (quarter) {
                // (x, z) → (1 - z, x)
                case 1 -> { nx0 = 1 - z1; nx1 = 1 - z0; nz0 = x0; nz1 = x1; }
                // (x, z) → (1 - x, 1 - z)
                case 2 -> { nx0 = 1 - x1; nx1 = 1 - x0; nz0 = 1 - z1; nz1 = 1 - z0; }
                // (x, z) → (z, 1 - x)
                default -> { nx0 = z0; nx1 = z1; nz0 = 1 - x1; nz1 = 1 - x0; }
            }
            result = Shapes.or(result, Shapes.box(nx0, box.minY, nz0, nx1, box.maxY, nz1));
        }
        return result;
    }
}
