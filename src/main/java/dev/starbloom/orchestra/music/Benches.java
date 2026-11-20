package dev.starbloom.orchestra.music;

import dev.starbloom.orchestra.OrchestraRegistries;
import dev.starbloom.orchestra.StarbloomOrchestra;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 星辰花琴凳的注册与生物那一边（docs/ORCHESTRA.md 第 14 节）：
 * 拿着手持乐器右键坐在琴凳上的生物，乐器交到它主手上（原来拿着的东西先替它收着，必定掉落、不会丢）；
 * 空手右键它，把乐器拿回来，原来的东西还给它。
 */
public final class Benches {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, StarbloomOrchestra.ID);
    private static final String STOWED_TAG = "starbloom_orchestra:stowed_main_hand";

    public static final RegistryObject<StarbloomBenchBlock> BENCH = OrchestraRegistries.BLOCKS.register("starbloom_bench",
        () -> new StarbloomBenchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.0f).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Item> BENCH_ITEM = OrchestraRegistries.ITEMS.register("starbloom_bench",
        () -> new BlockItem(BENCH.get(), new Item.Properties()));
    /** 双人琴凳：两格宽，坐在正中，两格宽的钢琴前用。 */
    public static final RegistryObject<StarbloomDuetBenchBlock> DUET = OrchestraRegistries.BLOCKS.register("starbloom_duet_bench",
        () -> new StarbloomDuetBenchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.0f).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Item> DUET_ITEM = OrchestraRegistries.ITEMS.register("starbloom_duet_bench",
        () -> new BlockItem(DUET.get(), new Item.Properties()));
    public static final RegistryObject<EntityType<BenchSeat>> SEAT = ENTITY_TYPES.register("bench_seat",
        () -> EntityType.Builder.<BenchSeat>of(BenchSeat::new, MobCategory.MISC).sized(0.25f, 0.25f).noSummon().clientTrackingRange(10)
            .updateInterval(20).build(StarbloomOrchestra.id("bench_seat").toString()));

    private Benches() {}

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
        MinecraftForge.EVENT_BUS.addListener(Benches::interact);
        MinecraftForge.EVENT_BUS.addListener(Benches::drops);
    }

    /** 替生物收着的主手物品（存在实体的 {@code PersistentData} 里，没有时为空）。 */
    public static ItemStack stowed(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(STOWED_TAG, Tag.TAG_COMPOUND)) return ItemStack.EMPTY;
        return ItemStack.of(data.getCompound(STOWED_TAG));
    }

    private static void setStowed(Mob mob, ItemStack stack) {
        if (stack.isEmpty()) mob.getPersistentData().remove(STOWED_TAG);
        else mob.getPersistentData().put(STOWED_TAG, stack.save(new CompoundTag()));
    }

    /** 右键坐在琴凳上的生物：拿着手持乐器时交给它，空手时把它的乐器拿回来。两边都取消，免得生物自己的交互（交易、对话）也跑一遍。 */
    private static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getTarget() instanceof Mob mob) || !(mob.getVehicle() instanceof BenchSeat seat)) return;
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        boolean give = held.getItem() instanceof HandheldInstrumentItem;
        boolean take = held.isEmpty() && mob.getMainHandItem().getItem() instanceof HandheldInstrumentItem;
        if (!give && !take) return;
        if (!event.getLevel().isClientSide()) {
            if (give) give(mob, held, player);
            else takeBack(mob, player);
            StarbloomBenchBlock.refresh(event.getLevel(), seat.bench());
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
    }

    /**
     * 把一件手持乐器交到生物主手上；它原来拿着乐器就把那件还给玩家，拿着别的就替它收着
     * （已经替它收着一件时——例如僵尸自己捡起了别的东西——手里这件掉在它脚下，不会丢）。
     */
    public static void give(Mob mob, ItemStack instrument, Player from) {
        ItemStack old = mob.getMainHandItem();
        if (old.getItem() instanceof HandheldInstrumentItem) {
            if (!from.getInventory().add(old.copy())) from.drop(old.copy(), false);
        } else if (!old.isEmpty()) {
            if (stowed(mob).isEmpty()) setStowed(mob, old.copy());
            else if (!mob.level().isClientSide()) mob.spawnAtLocation(old.copy());
        }
        mob.setItemSlot(EquipmentSlot.MAINHAND, instrument.copyWithCount(1));
        mob.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        if (!from.getAbilities().instabuild) instrument.shrink(1);
    }

    /** 把生物手里的乐器拿回来，替它收着的东西还给它。 */
    public static void takeBack(Mob mob, Player to) {
        ItemStack instrument = mob.getMainHandItem().copy();
        ItemStack stowed = stowed(mob);
        mob.setItemSlot(EquipmentSlot.MAINHAND, stowed.copy());
        mob.setDropChance(EquipmentSlot.MAINHAND, 0.085f);
        mob.getPersistentData().remove(STOWED_TAG);
        if (!to.getInventory().add(instrument)) to.drop(instrument, false);
    }

    /** 替生物收着的东西：死的时候照样掉出来。 */
    private static void drops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        ItemStack stowed = stowed(mob);
        if (stowed.isEmpty()) return;
        event.getDrops().add(new ItemEntity(mob.level(), mob.getX(), mob.getY(), mob.getZ(), stowed.copy()));
        mob.getPersistentData().remove(STOWED_TAG);
    }
}
