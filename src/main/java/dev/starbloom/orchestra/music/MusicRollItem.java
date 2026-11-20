package dev.starbloom.orchestra.music;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** 打孔纸带：提示文字显示曲名、作者、时长、音符数与声部。内容在服务端，物品只带 {@link RollInfo}。 */
public class MusicRollItem extends Item {
    public MusicRollItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> out, TooltipFlag flag) {
        RollInfo info = RollInfo.get(stack);
        if (info == null) {
            out.add(Component.translatable("item.starbloom_orchestra.music_roll.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!info.title().isEmpty()) out.add(Component.literal(info.title()).withStyle(ChatFormatting.GOLD));
        if (!info.author().isEmpty()) {
            out.add(Component.translatable("item.starbloom_orchestra.music_roll.author", info.author()).withStyle(ChatFormatting.GRAY));
        }
        out.add(Component.translatable("item.starbloom_orchestra.music_roll.length", RollInfo.clock(info.durationMs()), info.noteCount())
            .withStyle(ChatFormatting.GRAY));
        if (info.parts().size() > 1) {
            out.add(Component.translatable("item.starbloom_orchestra.music_roll.parts", String.join("、", info.parts()))
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
