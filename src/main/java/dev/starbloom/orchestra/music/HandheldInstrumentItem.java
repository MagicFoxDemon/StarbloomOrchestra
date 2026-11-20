package dev.starbloom.orchestra.music;

import java.util.function.Consumer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.DistExecutor;

/**
 * 拿在手上的乐器（小提琴、长笛、小号）。右键举起来演奏：和拉弓一样进入「使用中」（同步给附近的人，
 * 他们那里会看到演奏姿势、物品换成演奏时的模型），客户端同时打开屏幕底部的演奏条；关掉演奏条后
 * 下一 tick 原版自动放下。右键乐谱架则把乐器放到架子上（见 {@link MusicStandBlock}）。
 */
public class HandheldInstrumentItem extends Item {
    private final InstrumentKind kind;

    public HandheldInstrumentItem(InstrumentKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public InstrumentKind kind() {
        return kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        if (level.isClientSide()) {
            // 界面类只在客户端存在：经静态入口打开，本类不出现任何客户端类型
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.starbloom.orchestra.client.music.MusicClient.openHandheld(kind, hand));
        }
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72_000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.starbloom.orchestra.client.music.HandheldPoses.initClient(consumer));
    }
}
