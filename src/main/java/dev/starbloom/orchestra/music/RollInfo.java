package dev.starbloom.orchestra.music;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 打孔纸带物品上的 NBT 摘要（照原版地图的做法），内容在服务端存档里，
 * 见 {@link RollStore}。纸带写好后内容不变，所以客户端可以按 ID + 哈希缓存。
 */
public record RollInfo(UUID id, String title, String author, int durationMs, int noteCount, List<String> parts, long hash) {
    /** 存在 {@link ItemStack} 的 NBT 里的键名。 */
    public static final String TAG = "starbloom_orchestra_roll";

    public static final Codec<RollInfo> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.CODEC.fieldOf("id").forGetter(RollInfo::id),
        Codec.STRING.optionalFieldOf("title", "").forGetter(RollInfo::title),
        Codec.STRING.optionalFieldOf("author", "").forGetter(RollInfo::author),
        Codec.INT.optionalFieldOf("duration_ms", 0).forGetter(RollInfo::durationMs),
        Codec.INT.optionalFieldOf("notes", 0).forGetter(RollInfo::noteCount),
        Codec.STRING.listOf().optionalFieldOf("parts", List.of()).forGetter(RollInfo::parts),
        Codec.LONG.optionalFieldOf("hash", 0L).forGetter(RollInfo::hash)
    ).apply(i, RollInfo::new));

    public static RollInfo of(UUID id, MusicRoll roll, long hash) {
        return new RollInfo(id, roll.title(), roll.author(), roll.durationMs(), roll.noteCount(),
            roll.parts().stream().map(MusicRoll.Part::name).toList(), hash);
    }

    public static boolean has(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG, Tag.TAG_COMPOUND);
    }

    public static @Nullable RollInfo get(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG, Tag.TAG_COMPOUND)) return null;
        return CODEC.parse(NbtOps.INSTANCE, tag.getCompound(TAG)).result().orElse(null);
    }

    public static void set(ItemStack stack, RollInfo info) {
        CODEC.encodeStart(NbtOps.INSTANCE, info).result().ifPresent(t -> stack.getOrCreateTag().put(TAG, t));
    }

    /** 「3:25」这样的时长文字。 */
    public static String clock(int ms) {
        int s = Math.max(0, ms) / 1000;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }
}
