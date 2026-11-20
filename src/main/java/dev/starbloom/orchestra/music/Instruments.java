package dev.starbloom.orchestra.music;

import dev.starbloom.orchestra.OrchestraRegistries;
import dev.starbloom.orchestra.ServerConfig;
import dev.starbloom.orchestra.StarbloomOrchestra;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * 魔女之森乐器：方块、物品、复制配方，以及服务端的网络处理
 * （实时演奏转发、播放控制、纸带上传与下载）。设计见 docs/HEXENWALD_INSTRUMENTS.md。
 */
public final class Instruments {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, StarbloomOrchestra.ID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, StarbloomOrchestra.ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES =
        DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, StarbloomOrchestra.ID);

    /** 演奏者离乐器中心多远以内才能弹、才能操作纸带。 */
    public static final double REACH = 8.0;

    private static <T extends Block> RegistryObject<Item> blockItem(String name, RegistryObject<T> block) {
        return OrchestraRegistries.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static final RegistryObject<GrandPianoBlock> GRAND_PIANO = OrchestraRegistries.BLOCKS.register(
        "starbloom_grand_piano", () -> new GrandPianoBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(2.5f).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<ConcertHarpBlock> CONCERT_HARP = OrchestraRegistries.BLOCKS.register(
        "hexen_concert_harp", () -> new ConcertHarpBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(1.5f).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK)));

    public static final RegistryObject<dev.starbloom.orchestra.music.organ.OrganConsoleBlock> ORGAN_CONSOLE = OrchestraRegistries.BLOCKS.register(
        "hexen_organ_console", () -> new dev.starbloom.orchestra.music.organ.OrganConsoleBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.5f).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Block> ORGAN_WIND_CHEST = OrchestraRegistries.BLOCKS.register(
        "organ_wind_chest", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.0f).sound(SoundType.WOOD)));
    public static final RegistryObject<dev.starbloom.orchestra.music.organ.OrganPipeBlock> ORGAN_TIN_PIPE = OrchestraRegistries.BLOCKS.register(
        "organ_tin_pipe", () -> new dev.starbloom.orchestra.music.organ.OrganPipeBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).sound(SoundType.COPPER).noOcclusion(), 3.5));
    public static final RegistryObject<dev.starbloom.orchestra.music.organ.OrganPipeBlock> ORGAN_WOOD_PIPE = OrchestraRegistries.BLOCKS.register(
        "organ_wood_pipe", () -> new dev.starbloom.orchestra.music.organ.OrganPipeBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(1.5f).sound(SoundType.WOOD).noOcclusion(), 4.0));
    public static final RegistryObject<dev.starbloom.orchestra.music.organ.OrganTrumpetBlock> ORGAN_TRUMPET_PIPE = OrchestraRegistries.BLOCKS.register(
        "organ_trumpet_pipe", () -> new dev.starbloom.orchestra.music.organ.OrganTrumpetBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(2.0f).sound(SoundType.COPPER).noOcclusion()));

    /** 第二批：钟琴、钢片琴（一格）、管钟（两格高）、大键琴（一宽两深）。 */
    public static final RegistryObject<SingleInstrumentBlock> GLOCKENSPIEL = OrchestraRegistries.BLOCKS.register(
        "hexen_glockenspiel", () -> new SingleInstrumentBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK),
            InstrumentKind.GLOCKENSPIEL, Block.box(1, 0, 3, 15, 12, 13)));
    public static final RegistryObject<SingleInstrumentBlock> CELESTA = OrchestraRegistries.BLOCKS.register(
        "hexen_celesta", () -> new SingleInstrumentBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.0f).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK),
            InstrumentKind.CELESTA, Block.box(0, 0, 2, 16, 15, 16)));
    public static final RegistryObject<TallInstrumentBlock> TUBULAR_BELLS = OrchestraRegistries.BLOCKS.register(
        "hexen_tubular_bells", () -> new TallInstrumentBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(2.0f).sound(SoundType.COPPER).noOcclusion().pushReaction(PushReaction.BLOCK),
            InstrumentKind.TUBULAR_BELLS, Block.box(1, 0, 5, 15, 16, 11)));
    public static final RegistryObject<LongInstrumentBlock> HARPSICHORD = OrchestraRegistries.BLOCKS.register(
        "hexen_harpsichord", () -> new LongInstrumentBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(2.5f).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK),
            InstrumentKind.HARPSICHORD, Block.box(1, 0, 0, 15, 15, 16), Block.box(2, 0, 0, 14, 15, 14)));

    public static final RegistryObject<Item> GRAND_PIANO_ITEM = blockItem("starbloom_grand_piano", GRAND_PIANO);
    public static final RegistryObject<Item> CONCERT_HARP_ITEM = blockItem("hexen_concert_harp", CONCERT_HARP);

    public static final RegistryObject<Item> ORGAN_CONSOLE_ITEM = blockItem("hexen_organ_console", ORGAN_CONSOLE);
    public static final RegistryObject<Item> ORGAN_WIND_CHEST_ITEM = blockItem("organ_wind_chest", ORGAN_WIND_CHEST);
    public static final RegistryObject<Item> ORGAN_TIN_PIPE_ITEM = blockItem("organ_tin_pipe", ORGAN_TIN_PIPE);
    public static final RegistryObject<Item> ORGAN_WOOD_PIPE_ITEM = blockItem("organ_wood_pipe", ORGAN_WOOD_PIPE);
    public static final RegistryObject<Item> ORGAN_TRUMPET_PIPE_ITEM = blockItem("organ_trumpet_pipe", ORGAN_TRUMPET_PIPE);

    public static final RegistryObject<Item> GLOCKENSPIEL_ITEM = blockItem("hexen_glockenspiel", GLOCKENSPIEL);
    public static final RegistryObject<Item> CELESTA_ITEM = blockItem("hexen_celesta", CELESTA);
    public static final RegistryObject<Item> TUBULAR_BELLS_ITEM = blockItem("hexen_tubular_bells", TUBULAR_BELLS);
    public static final RegistryObject<Item> HARPSICHORD_ITEM = blockItem("hexen_harpsichord", HARPSICHORD);

    /** 手持乐器：小提琴、长笛、小号（挂魔女之森王室旗）。 */
    public static final RegistryObject<HandheldInstrumentItem> VIOLIN = OrchestraRegistries.ITEMS.register("hexen_violin",
        () -> new HandheldInstrumentItem(InstrumentKind.VIOLIN, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<HandheldInstrumentItem> FLUTE = OrchestraRegistries.ITEMS.register("hexen_flute",
        () -> new HandheldInstrumentItem(InstrumentKind.FLUTE, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<HandheldInstrumentItem> TRUMPET = OrchestraRegistries.ITEMS.register("hexen_trumpet",
        () -> new HandheldInstrumentItem(InstrumentKind.TRUMPET, new Item.Properties().stacksTo(1)));

    /** 第三批：中提琴、双簧管、巴松（手持），大提琴、低音提琴（坐着拉，两格高），定音鼓（一格）。 */
    public static final RegistryObject<HandheldInstrumentItem> VIOLA = OrchestraRegistries.ITEMS.register("hexen_viola",
        () -> new HandheldInstrumentItem(InstrumentKind.VIOLA, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<HandheldInstrumentItem> OBOE = OrchestraRegistries.ITEMS.register("hexen_oboe",
        () -> new HandheldInstrumentItem(InstrumentKind.OBOE, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<HandheldInstrumentItem> BASSOON = OrchestraRegistries.ITEMS.register("hexen_bassoon",
        () -> new HandheldInstrumentItem(InstrumentKind.BASSOON, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<SeatedInstrumentBlock> CELLO = OrchestraRegistries.BLOCKS.register(
        "hexen_cello", () -> new SeatedInstrumentBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(1.5f).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK),
            InstrumentKind.CELLO, Block.box(3, 0, 1, 13, 16, 16)));
    public static final RegistryObject<SeatedInstrumentBlock> DOUBLE_BASS = OrchestraRegistries.BLOCKS.register(
        "hexen_double_bass", () -> new SeatedInstrumentBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(1.5f).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK),
            InstrumentKind.DOUBLE_BASS, Block.box(2, 0, 0, 14, 16, 10)));
    public static final RegistryObject<SingleInstrumentBlock> TIMPANI = OrchestraRegistries.BLOCKS.register(
        "hexen_timpani", () -> new SingleInstrumentBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.0f).sound(SoundType.COPPER).noOcclusion().pushReaction(PushReaction.BLOCK),
            InstrumentKind.TIMPANI, Block.box(0, 0, 0, 16, 14, 16)));
    public static final RegistryObject<Item> CELLO_ITEM = blockItem("hexen_cello", CELLO);
    public static final RegistryObject<Item> DOUBLE_BASS_ITEM = blockItem("hexen_double_bass", DOUBLE_BASS);
    public static final RegistryObject<Item> TIMPANI_ITEM = blockItem("hexen_timpani", TIMPANI);

    /** 第四批：无音高的打击乐（一格，站在前面的工位上敲；docs/ORCHESTRA.md 第 20 节）。 */
    public static final RegistryObject<SingleInstrumentBlock> BASS_DRUM = OrchestraRegistries.BLOCKS.register(
        "hexen_bass_drum", () -> new SingleInstrumentBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(1.5f).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK),
            InstrumentKind.BASS_DRUM, Block.box(4, 0, 1, 12, 16, 15)));
    public static final RegistryObject<SingleInstrumentBlock> SNARE_DRUM = OrchestraRegistries.BLOCKS.register(
        "hexen_snare_drum", () -> new SingleInstrumentBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(1.5f).sound(SoundType.COPPER).noOcclusion().pushReaction(PushReaction.BLOCK),
            InstrumentKind.SNARE_DRUM, Block.box(3, 0, 2, 13, 14, 11)));
    public static final RegistryObject<SingleInstrumentBlock> SUSPENDED_CYMBAL = OrchestraRegistries.BLOCKS.register(
        "hexen_suspended_cymbal", () -> new SingleInstrumentBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(1.5f).sound(SoundType.COPPER).noOcclusion().pushReaction(PushReaction.BLOCK),
            InstrumentKind.SUSPENDED_CYMBAL, Block.box(2, 0, 2, 14, 16, 13)));
    public static final RegistryObject<SingleInstrumentBlock> TRIANGLE = OrchestraRegistries.BLOCKS.register(
        "hexen_triangle", () -> new SingleInstrumentBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0f).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK),
            InstrumentKind.TRIANGLE, Block.box(4, 0, 5, 14, 20, 11)));
    public static final RegistryObject<Item> BASS_DRUM_ITEM = blockItem("hexen_bass_drum", BASS_DRUM);
    public static final RegistryObject<Item> SNARE_DRUM_ITEM = blockItem("hexen_snare_drum", SNARE_DRUM);
    public static final RegistryObject<Item> SUSPENDED_CYMBAL_ITEM = blockItem("hexen_suspended_cymbal", SUSPENDED_CYMBAL);
    public static final RegistryObject<Item> TRIANGLE_ITEM = blockItem("hexen_triangle", TRIANGLE);

    /** 第五批：和乐器（手持）——篠笛、神乐铃、尺八（docs/JAPANESE_INSTRUMENTS.md）。 */
    public static final RegistryObject<HandheldInstrumentItem> SHINOBUE = OrchestraRegistries.ITEMS.register("shinobue",
        () -> new HandheldInstrumentItem(InstrumentKind.SHINOBUE, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<HandheldInstrumentItem> KAGURA_SUZU = OrchestraRegistries.ITEMS.register("kagura_suzu",
        () -> new HandheldInstrumentItem(InstrumentKind.KAGURA_SUZU, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<HandheldInstrumentItem> SHAKUHACHI = OrchestraRegistries.ITEMS.register("shakuhachi",
        () -> new HandheldInstrumentItem(InstrumentKind.SHAKUHACHI, new Item.Properties().stacksTo(1)));

    /** 大提琴、低音提琴后面的座位（看不见、不存盘）。 */
    public static final RegistryObject<EntityType<InstrumentSeat>> SEAT =
        ENTITY_TYPES.register("instrument_seat", () -> EntityType.Builder.<InstrumentSeat>of(InstrumentSeat::new,
                MobCategory.MISC).sized(0.25f, 0.25f).noSummon().clientTrackingRange(10).updateInterval(20)
            .build(StarbloomOrchestra.id("instrument_seat").toString()));
    /**
     * 站着演奏的工位（低音提琴、钟琴、管钟、大鼓、小军鼓、吊镲、三角铁）：和 {@link #SEAT} 同一个类，只是另一种实体，
     * 别的模组按骑乘的实体分动作时（车万女仆的 GeckoLib 模型 {@code vehicle#...}，标签 data/starbloom_orchestra/tags/entity_types/）能分清坐着和站着。
     */
    public static final RegistryObject<EntityType<InstrumentSeat>> STANDING_POST =
        ENTITY_TYPES.register("standing_post", () -> EntityType.Builder.<InstrumentSeat>of(InstrumentSeat::new,
                MobCategory.MISC).sized(0.25f, 0.25f).noSummon().clientTrackingRange(10).updateInterval(20)
            .build(StarbloomOrchestra.id("standing_post").toString()));

    /** 乐谱架：放一件手持乐器和一卷纸带，就像方块乐器一样受红石控制。 */
    public static final RegistryObject<MusicStandBlock> MUSIC_STAND = OrchestraRegistries.BLOCKS.register(
        "hexen_music_stand", () -> new MusicStandBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(1.0f).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Item> MUSIC_STAND_ITEM = blockItem("hexen_music_stand", MUSIC_STAND);

    public static final RegistryObject<Item> BLANK_ROLL = OrchestraRegistries.ITEMS.register("blank_music_roll", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> MUSIC_ROLL_ITEM = OrchestraRegistries.ITEMS.register("music_roll", () -> new MusicRollItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<BlockEntityType<InstrumentBlockEntity>> INSTRUMENT_ENTITY =
        BLOCK_ENTITIES.register("instrument", () -> BlockEntityType.Builder.of(InstrumentBlockEntity::new, GRAND_PIANO.get(), CONCERT_HARP.get(), ORGAN_CONSOLE.get(),
            GLOCKENSPIEL.get(), CELESTA.get(), TUBULAR_BELLS.get(), HARPSICHORD.get(), MUSIC_STAND.get(),
            CELLO.get(), DOUBLE_BASS.get(), TIMPANI.get(), Benches.BENCH.get(), Benches.DUET.get(),
            BASS_DRUM.get(), SNARE_DRUM.get(), SUSPENDED_CYMBAL.get(), TRIANGLE.get()).build(null));

    public static final RegistryObject<RecipeSerializer<RollCopyRecipe>> ROLL_COPY =
        RECIPES.register("music_roll_copy", () -> RollCopyRecipe.SERIALIZER);

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
        ENTITY_TYPES.register(bus);
        RECIPES.register(bus);
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> RollStore.open(e.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            RollStore.close();
            uploads.clear();
            buckets.clear();
            uploadTimes.clear();
        });
        MinecraftForge.EVENT_BUS.addListener(Instruments::watch);
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            uploads.remove(e.getEntity().getUUID());
            buckets.remove(e.getEntity().getUUID());
        });
    }

    public static boolean isRoll(ItemStack stack) {
        return stack.is(BLANK_ROLL.get()) || stack.is(MUSIC_ROLL_ITEM.get());
    }

    public static boolean inReach(ServerPlayer player, InstrumentBlockEntity be) {
        return player.level() == be.getLevel() && player.distanceToSqr(be.soundCenter()) <= REACH * REACH;
    }

    private static @Nullable InstrumentBlockEntity reachable(ServerPlayer player, BlockPos pos) {
        if (!player.level().isLoaded(pos)) return null;
        InstrumentBlockEntity be = InstrumentBlock.entityAt(player.level(), pos);
        return be != null && inReach(player, be) ? be : null;
    }

    private static void tell(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable("message.starbloom_orchestra.instrument." + key, args), true);
    }

    // —— 实时演奏 ——

    /** 每个玩家的实时事件令牌桶：平均每秒 128 个，瞬间最多 64 个（一批的上限）。 */
    private static final Map<UUID, double[]> buckets = new HashMap<>();

    private static boolean take(ServerPlayer player, int n, long gameTime) {
        double[] b = buckets.computeIfAbsent(player.getUUID(), k -> new double[]{128, gameTime});
        b[0] = Math.min(128, b[0] + (gameTime - (long) b[1]) * 128 / 20.0);
        b[1] = gameTime;
        if (b[0] < n) return false;
        b[0] -= n;
        return true;
    }

    static void handleLive(MusicPayloads.LiveNotes p, ServerPlayer player) {
        InstrumentBlockEntity be = reachable(player, p.pos());
        if (be == null || !player.getUUID().equals(be.performer())) return;
        if (p.notes().isEmpty() || !take(player, p.notes().size(), player.level().getGameTime())) return;
        List<MusicPayloads.LiveNote> clean = new ArrayList<>(p.notes().size());
        for (MusicPayloads.LiveNote n : p.notes()) {
            if (n.pitch() > MusicPayloads.PEDAL || n.velocity() > 127) continue;
            clean.add(new MusicPayloads.LiveNote(Math.min(1000, n.offsetMs()), n.pitch(), n.velocity()));
        }
        if (clean.isEmpty()) return;
        Vec3 at = be.soundCenter();
        OrchestraNetwork.sendToPlayersNear((ServerLevel) player.level(), player, at.x, at.y, at.z, be.hearingRange(),
            new MusicPayloads.LiveNotesOut(be.getBlockPos(), player.getId(), false, clean));
    }

    /** 手持乐器的种类对应的物品。 */
    public static @Nullable InstrumentKind heldKind(ItemStack stack) {
        return stack.getItem() instanceof HandheldInstrumentItem item ? item.kind() : null;
    }

    /** 拿在手上的乐器弹的音：核对手上真拿着这种乐器，限速，转发给附近的人（声音跟着这个玩家）。 */
    static void handleHeld(MusicPayloads.HeldNotes p, ServerPlayer player) {
        if (p.kind() < 0 || p.kind() >= InstrumentKind.values().length) return;
        InstrumentKind kind = InstrumentKind.values()[p.kind()];
        if (!kind.handheld() || (heldKind(player.getMainHandItem()) != kind && heldKind(player.getOffhandItem()) != kind)) return;
        List<MusicPayloads.LiveNote> clean = new ArrayList<>(p.notes().size());
        if (!p.allOff()) {
            if (p.notes().isEmpty() || !take(player, p.notes().size(), player.level().getGameTime())) return;
            for (MusicPayloads.LiveNote n : p.notes()) {
                if (n.pitch() > 127 || n.velocity() > 127) continue;
                clean.add(new MusicPayloads.LiveNote(Math.min(1000, n.offsetMs()), n.pitch(), n.velocity()));
            }
            if (clean.isEmpty()) return;
        }
        OrchestraNetwork.sendToPlayersNear((ServerLevel) player.level(), player, player.getX(), player.getY(), player.getZ(),
            kind.broadcastRange(), new MusicPayloads.HeldNotesOut(player.getId(), kind.ordinal(), p.allOff(), clean));
    }

    /** 演奏者离开：让附近的人那里全部松开。 */
    static void release(InstrumentBlockEntity be, @Nullable ServerPlayer player) {
        be.setPerformer(null);
        if (be.getLevel() instanceof ServerLevel level) {
            Vec3 at = be.soundCenter();
            OrchestraNetwork.sendToPlayersNear(level, null, at.x, at.y, at.z, be.hearingRange(),
                new MusicPayloads.LiveNotesOut(be.getBlockPos(), player == null ? -1 : player.getId(), true, List.of()));
        }
    }

    // —— 操作 ——

    static void handleControl(MusicPayloads.Control p, ServerPlayer player) {
        InstrumentBlockEntity be = reachable(player, p.pos());
        if (be == null) return;
        switch (p.action()) {
            case MusicPayloads.Control.CLAIM -> {
                if (!be.playable()) {
                    tell(player, "need_instrument");
                    return;
                }
                UUID current = be.performer();
                if (current == null || current.equals(player.getUUID())) {
                    be.setPerformer(player.getUUID());
                } else {
                    ServerPlayer other = player.level().getServer().getPlayerList().getPlayer(current);
                    if (other == null || !inReach(other, be)) {
                        be.setPerformer(player.getUUID());
                    } else {
                        tell(player, "busy", other.getDisplayName());
                    }
                }
            }
            case MusicPayloads.Control.RELEASE -> {
                if (player.getUUID().equals(be.performer())) release(be, player);
            }
            case MusicPayloads.Control.PLAY -> {
                if (!be.play()) tell(player, "no_roll");
            }
            case MusicPayloads.Control.PAUSE -> be.pause();
            case MusicPayloads.Control.STOP -> be.stopPlayback();
            case MusicPayloads.Control.EJECT -> {
                ItemStack out = be.takeRoll();
                if (!out.isEmpty() && !player.getInventory().add(out)) player.drop(out, false);
            }
            case MusicPayloads.Control.PART_MASK -> be.setPartMask(p.arg());
            case MusicPayloads.Control.SIGNAL_MODE -> be.setStopWithSignal(p.arg() != 0);
            case MusicPayloads.Control.STOPS -> {
                if (be.kind() == InstrumentKind.PIPE_ORGAN) be.setStops(p.arg());
            }
            case MusicPayloads.Control.REVERB -> {
                if (be.kind() == InstrumentKind.PIPE_ORGAN) be.setReverbMode(p.arg());
            }
            default -> { }
        }
    }

    // —— 纸带下载 ——

    static void handleRequest(MusicPayloads.RollRequest p, ServerPlayer player) {
        // 和实时演奏共用令牌桶：一次请求算 16 个事件，避免被刷
        if (!take(player, 16, player.level().getGameTime())) return;
        byte[] data = RollStore.load(p.id());
        OrchestraNetwork.sendToPlayer(player, new MusicPayloads.RollData(p.id(), data == null ? new byte[0] : data));
    }

    // —— 纸带上传 ——

    private static final class Session {
        final int id, total, purpose;
        final BlockPos pos;
        final byte[][] chunks;
        int received, bytes;
        final long started;

        Session(int id, int total, int purpose, BlockPos pos, long started) {
            this.id = id;
            this.total = total;
            this.purpose = purpose;
            this.pos = pos;
            this.chunks = new byte[total][];
            this.started = started;
        }
    }

    private static final Map<UUID, Session> uploads = new HashMap<>();
    private static final Map<UUID, ArrayDeque<Long>> uploadTimes = new HashMap<>();

    static void handleUpload(MusicPayloads.Upload p, ServerPlayer player) {
        long now = player.level().getGameTime();
        Session s = uploads.get(player.getUUID());
        if (s == null || s.id != p.uploadId() || now - s.started > 20 * 30) {
            if (p.total() < 1 || p.total() > MusicPayloads.MAX_UPLOAD_CHUNKS || p.index() != 0) {
                uploads.remove(player.getUUID());
                return;
            }
            s = new Session(p.uploadId(), p.total(), p.purpose(), p.pos(), now);
            uploads.put(player.getUUID(), s);
        }
        if (p.index() < 0 || p.index() >= s.total || p.total() != s.total || s.chunks[p.index()] != null) {
            uploads.remove(player.getUUID());
            return;
        }
        s.bytes += p.chunk().length;
        if (s.bytes > MusicRoll.MAX_BYTES) {
            uploads.remove(player.getUUID());
            tell(player, "too_big");
            return;
        }
        s.chunks[p.index()] = p.chunk();
        if (++s.received < s.total) return;
        uploads.remove(player.getUUID());
        byte[] all = new byte[s.bytes];
        int at = 0;
        for (byte[] chunk : s.chunks) {
            System.arraycopy(chunk, 0, all, at, chunk.length);
            at += chunk.length;
        }
        finish(player, s.purpose, s.pos, all, now);
    }

    private static void finish(ServerPlayer player, int purpose, BlockPos pos, byte[] data, long now) {
        boolean op = player.hasPermissions(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS);
        if (!ServerConfig.INSTRUMENTS_ALLOW_UPLOAD.get() && !op) {
            tell(player, "upload_disabled");
            return;
        }
        ArrayDeque<Long> times = uploadTimes.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        while (!times.isEmpty() && now - times.peekFirst() > 20 * 60) times.pollFirst();
        if (!op && times.size() >= ServerConfig.INSTRUMENTS_UPLOADS_PER_MINUTE.get()) {
            tell(player, "upload_rate");
            return;
        }
        InstrumentBlockEntity be = reachable(player, pos);
        if (be == null) return;
        MusicRoll roll;
        byte[] encoded;
        try {
            roll = MusicRoll.decode(data);
            if (roll.author().isEmpty()) roll = roll.withMeta(roll.title(), player.getGameProfile().getName());
            encoded = roll.encode();
        } catch (MusicRoll.FormatException e) {
            tell(player, "bad_roll", e.getMessage());
            return;
        }
        long hash = MusicRoll.hash(encoded);
        if (purpose == MusicPayloads.Upload.TEMPORARY) {
            times.addLast(now);
            UUID id = RollStore.saveTemporary(encoded);
            be.playTemporary(id, hash, roll.durationMs());
            return;
        }
        if (!isRoll(be.roll())) {
            tell(player, "need_blank");
            return;
        }
        UUID id;
        try {
            id = RollStore.save(encoded);
        } catch (IOException e) {
            tell(player, "save_failed");
            return;
        }
        times.addLast(now);
        ItemStack punched = new ItemStack(MUSIC_ROLL_ITEM.get());
        RollInfo.set(punched, RollInfo.of(id, roll, hash));
        be.setRoll(punched);
        tell(player, "written", roll.title().isEmpty() ? "—" : roll.title());
    }

    // —— 后来的人 ——

    private static void watch(ChunkWatchEvent.Watch event) {
        for (BlockEntity be : event.getChunk().getBlockEntities().values()) {
            if (be instanceof InstrumentBlockEntity instrument && instrument.hasPlayback()) instrument.sendState(event.getPlayer());
        }
    }

    private Instruments() {}
}
