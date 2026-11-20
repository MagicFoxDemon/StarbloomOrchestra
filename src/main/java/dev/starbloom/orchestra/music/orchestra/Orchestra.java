package dev.starbloom.orchestra.music.orchestra;

import dev.starbloom.orchestra.OrchestraRegistries;
import dev.starbloom.orchestra.StarbloomOrchestra;
import dev.starbloom.orchestra.music.Instruments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/** 乐团：指挥台、指挥棒的注册与服务端的处理（docs/ORCHESTRA.md）。 */
public final class Orchestra {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, StarbloomOrchestra.ID);
    /** 指挥棒绑定指挥台的 NBT 键名。 */
    public static final String BATON_PODIUM_TAG = "starbloom_orchestra_podium";

    public static final RegistryObject<PodiumBlock> PODIUM = OrchestraRegistries.BLOCKS.register("hexen_podium",
        () -> new PodiumBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.0f).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Item> PODIUM_ITEM = OrchestraRegistries.ITEMS.register("hexen_podium",
        () -> new BlockItem(PODIUM.get(), new Item.Properties()));
    public static final RegistryObject<BatonItem> BATON = OrchestraRegistries.ITEMS.register("hexen_baton",
        () -> new BatonItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<BlockEntityType<PodiumBlockEntity>> PODIUM_ENTITY =
        BLOCK_ENTITIES.register("podium", () -> BlockEntityType.Builder.of(PodiumBlockEntity::new, PODIUM.get()).build(null));

    private Orchestra() {}

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
        // 指挥状态下左键不攻击生物、不挖方块：客户端已经不发，这里防别的客户端或延迟
        MinecraftForge.EVENT_BUS.addListener((AttackEntityEvent e) -> {
            if (PodiumBlock.conducting(e.getEntity())) e.setCanceled(true);
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock e) -> {
            if (PodiumBlock.conducting(e.getEntity())) e.setCanceled(true);
        });
    }

    public static @Nullable GlobalPos batonPodium(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(BATON_PODIUM_TAG, Tag.TAG_COMPOUND)) return null;
        return GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.getCompound(BATON_PODIUM_TAG)).result().orElse(null);
    }

    public static void setBatonPodium(ItemStack stack, GlobalPos pos) {
        GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, pos).result()
            .ifPresent(t -> stack.getOrCreateTag().put(BATON_PODIUM_TAG, t));
    }

    public static void tell(Player player, String key, Object... args) {
        if (player instanceof ServerPlayer sp) sp.sendSystemMessage(Component.translatable("message.starbloom_orchestra.orchestra." + key, args), true);
    }

    public static void handleControl(EnsemblePayloads.PodiumControl p, ServerPlayer player) {
        if (!player.level().isLoaded(p.podium())) return;
        PodiumBlockEntity be = PodiumBlock.entityAt(player.level(), p.podium());
        if (be == null || player.distanceToSqr(be.standAt()) > Instruments.REACH * Instruments.REACH) return;
        switch (p.action()) {
            case EnsemblePayloads.PodiumControl.START -> {
                // 起奏前打两拍（b 是间隔）：只有站在这座台上、拿着指挥棒的人才算指挥
                boolean counted = p.b() > 0 && PodiumBlock.conducting(player) && p.podium().equals(PodiumBlock.under(player));
                if (!be.isPlaying() && !be.start(p.a(), counted ? p.b() : 0, counted ? player : null)) tell(player, "cannot_start");
            }
            case EnsemblePayloads.PodiumControl.STOP -> be.stop();
            case EnsemblePayloads.PodiumControl.GATHER -> {
                if (be.isPlaying()) tell(player, "busy");
                else tell(player, "gathered", be.gather(), be.members().size());
            }
            case EnsemblePayloads.PodiumControl.REASSIGN -> be.reassign();
            case EnsemblePayloads.PodiumControl.ASSIGN -> be.assign(p.a(), p.b());
            case EnsemblePayloads.PodiumControl.UNLOCK -> be.unlock(p.a());
            case EnsemblePayloads.PodiumControl.REMOVE -> be.remove(p.a());
            case EnsemblePayloads.PodiumControl.TEMPO -> be.setTempo(p.a());
            case EnsemblePayloads.PodiumControl.GAIN -> be.setGain(p.a(), p.b());
            case EnsemblePayloads.PodiumControl.MUTE -> be.toggleMute(p.a());
            case EnsemblePayloads.PodiumControl.SOLO -> be.toggleSolo(p.a());
            case EnsemblePayloads.PodiumControl.MASTER -> be.setMaster(p.a());
            case EnsemblePayloads.PodiumControl.METRONOME -> be.toggleMetronome();
            case EnsemblePayloads.PodiumControl.EJECT -> {
                if (be.isPlaying()) {
                    tell(player, "busy");
                    return;
                }
                ItemStack out = be.takeScore();
                if (!out.isEmpty() && !player.getInventory().add(out)) player.drop(out, false);
            }
            default -> {}
        }
    }

    /** 指挥打的一拍：只收站在这座指挥台上、拿着指挥棒的人的。 */
    public static void handleBeat(EnsemblePayloads.ConductBeat p, ServerPlayer player) {
        if (!player.level().isLoaded(p.podium())) return;
        if (!PodiumBlock.conducting(player) || !p.podium().equals(PodiumBlock.under(player))) return;
        PodiumBlockEntity be = PodiumBlock.entityAt(player.level(), p.podium());
        if (be == null || !be.isPlaying()) return;
        be.follow(player, p.positionMs(), p.rateMilli() / 1000.0, p.expressionMilli() / 1000f, p.held());
    }

    /** 指挥棒右键指挥台：没绑定这座时绑定；绑定了就开始 / 停止演奏。 */
    public static void batonOnPodium(ItemStack stack, ServerLevel level, BlockPos main, Player player) {
        GlobalPos here = GlobalPos.of(level.dimension(), main);
        if (!here.equals(batonPodium(stack))) {
            setBatonPodium(stack, here);
            tell(player, "bound");
            return;
        }
        if (!(level.getBlockEntity(main) instanceof PodiumBlockEntity be)) return;
        if (be.isPlaying()) {
            be.stop();
            tell(player, "stopped");
        } else if (be.start(0)) {
            tell(player, "started");
        } else {
            tell(player, be.score().isEmpty() ? "no_score" : "cannot_start");
        }
    }

    /** 指挥棒右键乐器：加进（或移出）指挥棒绑定的乐团。 */
    public static void batonOnInstrument(ItemStack stack, ServerLevel level, BlockPos instrument, Player player) {
        GlobalPos bound = batonPodium(stack);
        if (bound == null || !bound.dimension().equals(level.dimension())) {
            tell(player, "unbound");
            return;
        }
        if (!level.isLoaded(bound.pos()) || !(level.getBlockEntity(bound.pos()) instanceof PodiumBlockEntity be)) {
            tell(player, "podium_gone");
            return;
        }
        if (be.isPlaying()) {
            tell(player, "busy");
            return;
        }
        tell(player, be.toggle(instrument), be.members().size(), PodiumBlockEntity.MAX_MEMBERS);
    }
}
