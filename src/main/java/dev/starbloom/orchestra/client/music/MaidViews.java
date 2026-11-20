package dev.starbloom.orchestra.client.music;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import dev.starbloom.orchestra.TestTravel;
import dev.starbloom.orchestra.music.BenchSeat;
import dev.starbloom.orchestra.music.Benches;
import dev.starbloom.orchestra.music.HandheldInstrumentItem;
import dev.starbloom.orchestra.music.InstrumentBlock;
import dev.starbloom.orchestra.music.InstrumentBlockEntity;
import dev.starbloom.orchestra.music.InstrumentKind;
import dev.starbloom.orchestra.music.InstrumentPosts;
import dev.starbloom.orchestra.music.InstrumentSeat;
import dev.starbloom.orchestra.music.Instruments;
import dev.starbloom.orchestra.music.MusicPayloads;
import dev.starbloom.orchestra.music.MusicRoll;
import dev.starbloom.orchestra.music.RollInfo;
import dev.starbloom.orchestra.music.RollStore;
import dev.starbloom.orchestra.music.StarbloomBenchBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * 车万女仆当乐手（docs/ORCHESTRA.md 第 16 节）的客户端场景 {@code maid_musicians_ui}。同 {@link MaidMusicians}，只在有女仆模组的 jar 时编译
 * （build.gradle 里一起排除），只在装了女仆模组时登记（{@code modID}），所以平常的测试不用带女仆模组。
 * <p>
 * 平地上一排女仆一种模型：女仆模组默认的基岩版模型（灵梦）、自带的 GeckoLib 酒狐，装了「车万女仆：咒语」（女仆模组的附属）时
 * 再加一排它自带的 GeckoLib 模型。每排同样的九个位置：坐在大钢琴前关联的琴凳上、大提琴的工位上（坐着拉弓）、钟琴前的工位上（站着拿琴槌），
 * 这三只手里拿着铁剑；拿着小提琴（另一只手里也有铁剑）、长笛、小号坐在没关联的琴凳上；拿着小提琴、小号站在地上；只拿着铁剑站着（对照：不演奏，剑照常画）。
 * 钢琴、大提琴、钟琴放着纸带，琴凳上的三只各按住一个音（同别人手上的乐器传来的音），这样都在演奏。
 * 每只女仆从固定的机位（按她所在的位置算）拍正面偏左和左侧各一张，每排再拍两张全景。
 * 检查：都坐上了、站到了工位上，{@link MusicianPoses#of} 认出的演奏方式；画女仆的时候（{@link RenderLivingEvent.Post} 里、去掉这一帧之前）
 * 演奏的女仆手里原本的东西不画（{@link MusicianPoses#hidesHandItems}），对照那只照常画；两种渲染器画手持物品的层换成了外壳、画乐器的层在；
 * 基岩版摆过姿势，GeckoLib 的动画文件里有本模组的默认动作。
 */
@LDLRegisterClient(name = "maid_musicians_ui", group = "instrument", registry = UIScenario.REGISTRY,
    environment = RegistrationEnvironment.DEV_ONLY, modID = "touhou_little_maid")
public class MaidViews implements UIScenario {
    private static final int GROUND = 240;
    private static final BlockPos ORIGIN = new BlockPos(260, GROUND, 150);

    /** 女仆在哪：琴凳（PIANO 前面有钢琴、BENCH 没关联）、工位，还是站在地上。 */
    private enum Station { PIANO, CELLO, GLOCKENSPIEL, BENCH, STANDING }

    /**
     * 一排里的一个位置。x 是女仆那一格在 {@link #ORIGIN} 里的偏移（方块，女仆都面朝南）；main、off 是两只手里的东西；
     * mode 是 {@link MusicianPoses#of} 该认出的演奏方式（站在地上时为 null）；pitch 是琴凳上按住的那个音。
     */
    private record Spot(String name, int x, Station station, Supplier<? extends Item> main, @Nullable Supplier<? extends Item> off,
                        MusicianPoses.@Nullable Mode mode, int pitch) {}

    private static final List<Spot> SPOTS = List.of(
        new Spot("piano", -16, Station.PIANO, () -> Items.IRON_SWORD, null, MusicianPoses.Mode.KEYS, 0),
        new Spot("cello", -11, Station.CELLO, () -> Items.IRON_SWORD, null, MusicianPoses.Mode.BOWED, 0),
        new Spot("glockenspiel", -7, Station.GLOCKENSPIEL, () -> Items.IRON_SWORD, null, MusicianPoses.Mode.MALLETS, 0),
        new Spot("bench_violin", -3, Station.BENCH, Instruments.VIOLIN, () -> Items.IRON_SWORD, MusicianPoses.Mode.HANDHELD, 67),
        new Spot("bench_flute", 1, Station.BENCH, Instruments.FLUTE, null, MusicianPoses.Mode.HANDHELD, 72),
        new Spot("bench_trumpet", 5, Station.BENCH, Instruments.TRUMPET, null, MusicianPoses.Mode.HANDHELD, 67),
        new Spot("standing_violin", 9, Station.STANDING, Instruments.VIOLIN, null, null, 0),
        new Spot("standing_trumpet", 13, Station.STANDING, Instruments.TRUMPET, null, null, 0),
        new Spot("idle_sword", 17, Station.STANDING, () -> Items.IRON_SWORD, null, null, 0));

    /** 一排女仆，同一种模型：z 是这一排在 {@link #ORIGIN} 里的偏移；model 为 null 时是女仆模组的默认模型（基岩版）。 */
    private record Row(String name, int z, @Nullable String model, boolean gecko) {}

    private static List<Row> rows() {
        List<Row> rows = new ArrayList<>(List.of(new Row("bedrock", -10, null, false), new Row("winefox", 0, "geckolib:winefox", true)));
        // 装了「车万女仆：咒语」时多一排它模型包里的 GeckoLib 模型（这个附属只有 1.21.1 版）
        if (ModList.get().isLoaded("touhou_little_maid_spell")) {
            rows.add(new Row("stellar_witch", 10, "touhou_little_maid_spell:stellar_witch", true));
        }
        return rows;
    }

    /** 女仆的实体 id，按「排/位置」。 */
    private static final Map<String, Integer> MAIDS = new LinkedHashMap<>();
    private static int camera = -1;

    /** 画女仆时看到的（{@link #seen}）：这一帧手里原本的东西画不画、哪种渲染器、两层换好没有、怎么演奏。 */
    private record Seen(boolean hides, @Nullable Boolean gecko, boolean layers, MusicianPoses.@Nullable Mode mode, boolean held) {}

    private static final Map<Integer, Seen> SEEN = new ConcurrentHashMap<>();
    private static boolean listening;
    private static int posedBefore, waits;
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    private static String key(Row row, Spot spot) {
        return row.name() + "/" + spot.name();
    }

    @Override
    public void configure(ScenarioOptions o) {
        o.tags("instrument", "maid").defaultTimeoutMs(60000).scenarioTimeoutMs(400000);
    }

    private static void place(ServerLevel level, BlockPos pos, Block block, Direction facing) {
        BlockState state = block.defaultBlockState().setValue(InstrumentBlock.FACING, facing);
        level.setBlockAndUpdate(pos, state);
        state.getBlock().setPlacedBy(level, pos, state, null, ItemStack.EMPTY);
    }

    /**
     * 打一卷纸带：notes(i) 给第 i 拍的几个音（音高、时长成对），每隔 step 毫秒一拍，共 length 毫秒。同一种乐器各排共用一卷：
     * 客户端第一次见到一卷纸带时向服务端要，服务端按令牌桶限着（一次算 16 个事件、桶满 128），一下子要九卷时最后一卷被拒，
     * 客户端也不再要，那件乐器在这个客户端上就一直不响。
     */
    private static ItemStack punch(int step, int length, IntFunction<int[]> notes) {
        List<MusicRoll.Event> events = new ArrayList<>();
        for (int i = 0; i * step < length; i++) {
            int[] n = notes.apply(i);
            for (int k = 0; k + 1 < n.length; k += 2) events.add(MusicRoll.Event.note(i * step, 0, n[k], 96, n[k + 1]));
        }
        try {
            MusicRoll roll = new MusicRoll("女仆乐手", "测试", 12000, 4, 4, List.of(new MusicRoll.Part("乐器", 0)), events);
            byte[] encoded = roll.encode();
            UUID id = RollStore.save(encoded);
            ItemStack punched = new ItemStack(Instruments.MUSIC_ROLL_ITEM.get());
            punched.set(Instruments.MUSIC_ROLL.get(), RollInfo.of(id, roll, MusicRoll.hash(encoded)));
            return punched;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 三种方块乐器各一卷：钢琴左手 C3、G3，右手 E5、G5、C6；大提琴每两秒一个长音；钟琴低音（右手）、高音（左手）轮流。 */
    private record Rolls(ItemStack piano, ItemStack cello, ItemStack glockenspiel) {
        static Rolls punch() {
            return new Rolls(
                MaidViews.punch(500, 240_000, i -> new int[]{i % 2 == 0 ? 48 : 55, 450, new int[]{76, 79, 84, 79}[i % 4], 450}),
                MaidViews.punch(2000, 240_000, i -> new int[]{new int[]{48, 55, 52}[i % 3], 1950}),
                MaidViews.punch(300, 240_000, i -> new int[]{i % 2 == 0 ? 84 + (i / 2) % 3 * 2 : 98 + (i / 2) % 3 * 2, 250}));
        }
    }

    private static void play(ServerLevel level, BlockPos pos, ItemStack roll) {
        if (level.getBlockEntity(pos) instanceof InstrumentBlockEntity be) {
            be.setRoll(roll.copy());
            be.play();
        }
    }

    /** 方块乐器的主块（放纸带的那一格）；琴凳、站在地上的为 null。 */
    private static @Nullable BlockPos instrument(Row row, Spot spot) {
        BlockPos at = ORIGIN.offset(spot.x(), 1, row.z());
        return switch (spot.station()) {
            case PIANO, GLOCKENSPIEL -> at.south();
            case CELLO -> at;
            default -> null;
        };
    }

    private static void stageRow(ServerLevel level, Row row, Rolls rolls) {
        for (Spot spot : SPOTS) {
            BlockPos at = ORIGIN.offset(spot.x(), 1, row.z());
            Mob maid = MaidMusicians.testMaid(level, row.model());
            maid.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            maid.setYHeadRot(0);
            maid.setYBodyRot(0);
            maid.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(spot.main().get()));
            if (spot.off() != null) maid.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(spot.off().get()));
            maid.setPersistenceRequired();
            switch (spot.station()) {
                // 钢琴琴键朝北：琴凳在前排北面、面朝南就关联
                case PIANO -> {
                    place(level, at.south(), Instruments.GRAND_PIANO.get(), Direction.NORTH);
                    place(level, at, Benches.BENCH.get(), Direction.SOUTH);
                    level.addFreshEntity(maid);
                    StarbloomBenchBlock.sit(level, at, maid);
                    play(level, at.south(), rolls.piano());
                }
                // 大提琴朝南：人坐在琴后（北边），和琴朝同一个方向
                case CELLO -> {
                    place(level, at, Instruments.CELLO.get(), Direction.SOUTH);
                    level.addFreshEntity(maid);
                    InstrumentPosts.take(level, at, maid);
                    play(level, at, rolls.cello());
                }
                // 钟琴朝北：人站在它北面一格、面朝南；低音右手、高音左手轮流敲
                case GLOCKENSPIEL -> {
                    place(level, at.south(), Instruments.GLOCKENSPIEL.get(), Direction.NORTH);
                    level.addFreshEntity(maid);
                    InstrumentPosts.take(level, at.south(), maid);
                    play(level, at.south(), rolls.glockenspiel());
                }
                case BENCH -> {
                    place(level, at, Benches.BENCH.get(), Direction.SOUTH);
                    level.addFreshEntity(maid);
                    StarbloomBenchBlock.sit(level, at, maid);
                }
                // 站在地上：不让女仆模组的 AI 走开、东张西望
                case STANDING -> {
                    maid.setNoAi(true);
                    level.addFreshEntity(maid);
                }
            }
            MAIDS.put(key(row, spot), maid.getId());
        }
    }

    private static @Nullable Entity client(Row row, Spot spot) {
        Integer id = MAIDS.get(key(row, spot));
        var level = Minecraft.getInstance().level;
        return id == null || level == null ? null : level.getEntity(id);
    }

    /** 这只女仆坐在、站在她该在的地方（客户端看到的）。 */
    private static boolean inPlace(Row row, Spot spot) {
        Entity maid = client(row, spot);
        if (maid == null) return false;
        Entity ride = maid.getVehicle();
        return switch (spot.station()) {
            case PIANO, BENCH -> ride instanceof BenchSeat;
            case CELLO -> ride instanceof InstrumentSeat seat && seat.getType() == Instruments.SEAT.get() && seat.kind() == InstrumentKind.CELLO;
            case GLOCKENSPIEL -> ride instanceof InstrumentSeat seat && seat.getType() == Instruments.STANDING_POST.get()
                && seat.kind() == InstrumentKind.GLOCKENSPIEL;
            case STANDING -> ride == null;
        };
    }

    /**
     * 画一个实体之后、{@link MusicianPoses} 和 {@link MaidMusicians} 去掉这一帧之前（排在最前）：是场景里的女仆就记下这一帧看到的。
     * 只有在镜头里的才画，所以拍完所有机位再查。
     */
    private static void seen(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!MAIDS.containsValue(entity.getId())) return;
        MusicianPoses.Drawing d = MusicianPoses.drawing(entity);
        SEEN.put(entity.getId(), new Seen(MusicianPoses.hidesHandItems(entity), MaidMusicians.drawnAsGecko(entity),
            MaidMusicians.layersReplaced(event.getRenderer()), d == null || d.musician == null ? null : d.musician.mode(), d != null && d.held != null));
    }

    /** 从隐形盔甲架的眼睛里看这只女仆：机位按她的位置（服务端）偏 dx、dy、dz（女仆面朝南：+Z 是她前面，+X 是她左手边），看向她身上 lookY 高处。 */
    private static ScenarioBuilder view(ScenarioBuilder s, String shot, String maid, double dx, double dy, double dz, double lookY) {
        return aim(s, shot, ctx -> {
            Entity who = ctx.server().overworld().getEntity(MAIDS.get(maid));
            Vec3 at = who == null ? Vec3.ZERO : who.position();
            return new Vec3[]{at.add(dx, dy, dz), at.add(0, lookY, 0)};
        });
    }

    private static ScenarioBuilder aim(ScenarioBuilder s, String shot, java.util.function.Function<com.lowdragmc.lowdraglib2.uitest.ServerContext, Vec3[]> where) {
        return s.server("camera for " + shot, ctx -> {
                Entity cam = ctx.server().overworld().getEntity(camera);
                Vec3[] w = where.apply(ctx);
                Vec3 eye = w[0], look = w[1].subtract(eye);
                float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90;
                float pitch = (float) -(Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG);
                cam.moveTo(eye.x, eye.y - cam.getEyeHeight(), eye.z, yaw, pitch);
                cam.setYHeadRot(yaw);
            })
            .ticks(5)
            .step("look from the camera", ctx -> {
                var mc = Minecraft.getInstance();
                mc.options.hideGui = true;
                mc.setCameraEntity(mc.level.getEntity(camera));
            })
            .ticks(2)
            .screenshot(shot);
    }

    @Override
    public void define(ScenarioBuilder s) {
        List<Row> rows = rows();
        ScenarioBuilder b = s.setGameMode(GameType.CREATIVE)
            .runCommand("difficulty peaceful")
            .runCommand("time set noon")
            .step("camera settings", ctx -> {
                var mc = Minecraft.getInstance();
                mc.options.hideGui = true;
                mc.options.renderDistance().set(8);
                mc.options.fov().set(70);
                mc.options.broadcastOptions();
                if (!listening) {
                    NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, RenderLivingEvent.Post.class, MaidViews::seen);
                    listening = true;
                }
            })
            .server("stage the maids", ctx -> {
                ServerLevel level = ctx.server().overworld();
                for (int x = -20; x <= 21; x++) {
                    for (int z = -13; z <= 15; z++) {
                        BlockPos p = ORIGIN.offset(x, 0, z);
                        for (int y = 1; y <= 5; y++) {
                            level.setBlock(p.above(y), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                        }
                        level.setBlock(p, ((x + z & 1) == 0 ? Blocks.POLISHED_DIORITE : Blocks.SMOOTH_QUARTZ).defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                AABB area = new AABB(ORIGIN).inflate(24);
                level.getEntitiesOfClass(Entity.class, area, e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(Entity::discard);
                MAIDS.clear();
                Rolls rolls = Rolls.punch();
                for (Row row : rows) stageRow(level, row, rolls);
                ArmorStand cam = EntityType.ARMOR_STAND.create(level);
                cam.setInvisible(true);
                cam.setNoGravity(true);
                cam.setPos(ORIGIN.getX() + 0.5, GROUND + 1, ORIGIN.getZ() + 20);
                level.addFreshEntity(cam);
                camera = cam.getId();
                ctx.player().getInventory().clearContent();
                // 玩家站在后面远处（各个机位都拍不到他），区块和实体照常送来
                TestTravel.teleport(ctx.player(), level, new Vec3(ORIGIN.getX() + 0.5, GROUND + 1, ORIGIN.getZ() + 40), 180f, 10f);
            })
            .waitUntil("the maids arrive and take their places", ctx -> {
                var mc = Minecraft.getInstance();
                if (mc.level == null || mc.level.getEntity(camera) == null) return false;
                for (Row row : rows) for (Spot spot : SPOTS) if (!inPlace(row, spot)) return false;
                return true;
            })
            .waitUntil("the piano, cello and glockenspiel play their rolls", ctx -> {
                boolean all = true;
                StringBuilder silent = new StringBuilder();
                for (Row row : rows) {
                    for (Spot spot : SPOTS) {
                        BlockPos pos = instrument(row, spot);
                        if (pos == null || VoiceBank.recentlyPlayed(pos)) continue;
                        all = false;
                        MusicClient.Track t = MusicClient.track(pos);
                        silent.append(' ').append(key(row, spot)).append(t == null ? "(no track)"
                            : "(roll " + (t.roll != null) + ", playing " + t.playing + ", kind " + MusicClient.kindAt(pos) + ")");
                    }
                }
                if (!all && ++waits % 40 == 0) LOG.info("still silent:{}", silent);
                return all;
            })
            .step("the bench players hold a note each", ctx -> {
                for (Row row : rows) {
                    for (Spot spot : SPOTS) {
                        if (spot.station() != Station.BENCH || !(spot.main().get() instanceof HandheldInstrumentItem item)) continue;
                        MusicClient.heardHeld(new MusicPayloads.HeldNotesOut(MAIDS.get(key(row, spot)), item.kind().ordinal(), false,
                            List.of(new MusicPayloads.LiveNote(0, spot.pitch(), 90))));
                    }
                }
            })
            .ticks(20)
            .check("every maid sits on her bench, is at her post or stands", ctx -> {
                boolean ok = true;
                for (Row row : rows) {
                    for (Spot spot : SPOTS) {
                        if (inPlace(row, spot)) continue;
                        Entity maid = client(row, spot);
                        LOG.info("maid {} is not in place: {} riding {}", key(row, spot), maid, maid == null ? null : maid.getVehicle());
                        ok = false;
                    }
                }
                return ok;
            })
            .check("MusicianPoses sees how each maid plays", ctx -> {
                for (Row row : rows) {
                    for (Spot spot : SPOTS) {
                        if (!(client(row, spot) instanceof LivingEntity maid)) return false;
                        MusicianPoses.Musician m = MusicianPoses.of(maid);
                        if ((m == null ? null : m.mode()) != spot.mode()) return false;
                    }
                }
                return true;
            })
            .step("forget what was drawn so far", ctx -> {
                SEEN.clear();
                posedBefore = MaidMusicians.posed;
            });
        for (Row row : rows) {
            b = aim(b, "maid_" + row.name() + "_left", ctx -> new Vec3[]{
                Vec3.atLowerCornerOf(ORIGIN).add(-8.5, 4.5, row.z() + 7.5), Vec3.atLowerCornerOf(ORIGIN).add(-8.5, 1.5, row.z())});
            b = aim(b, "maid_" + row.name() + "_right", ctx -> new Vec3[]{
                Vec3.atLowerCornerOf(ORIGIN).add(9.5, 4.5, row.z() + 7.5), Vec3.atLowerCornerOf(ORIGIN).add(9.5, 1.5, row.z())});
            for (Spot spot : SPOTS) {
                String k = key(row, spot), shot = "maid_" + row.name() + "_" + spot.name();
                boolean standing = spot.station() == Station.STANDING || spot.station() == Station.GLOCKENSPIEL;
                double lookY = standing ? 1.1 : 0.9;
                if (spot.station() == Station.PIANO) {
                    // 正前方是钢琴（琴盖挡着）：机位在她左手边的高处，越过琴身前沿看她的手和琴键
                    b = view(b, shot + "_front", k, 2.6, 2.4, 1.2, lookY);
                } else {
                    b = view(b, shot + "_front", k, 1.4, 1.4, 2.3, lookY);
                }
                b = view(b, shot + "_side", k, 2.3, standing ? 1.3 : 1.1, 0.2, lookY);
            }
        }
        b.step("back to my eyes", ctx -> {
                var mc = Minecraft.getInstance();
                mc.setCameraEntity(mc.player);
            })
            .check("the bedrock maids were posed while drawn", ctx -> MaidMusicians.posed > posedBefore)
            .check("every maid was drawn by the renderer of her model, with the held-item layer wrapped and ours added", ctx -> {
                for (Row row : rows) {
                    for (Spot spot : SPOTS) {
                        Seen seen = SEEN.get(MAIDS.get(key(row, spot)));
                        if (seen == null || seen.gecko() == null || seen.gecko() != row.gecko() || !seen.layers()) return false;
                    }
                }
                return true;
            })
            .check("playing maids hide what is in their hands, the idle one does not", ctx -> {
                boolean ok = true;
                for (Row row : rows) {
                    for (Spot spot : SPOTS) {
                        Seen seen = SEEN.get(MAIDS.get(key(row, spot)));
                        // GeckoLib 女仆站着拿手持乐器时也端着它（默认动作），乐器由我们这层画；基岩版站着拿就是女仆模组自己画手里的东西
                        boolean carried = row.gecko() && spot.main().get() instanceof HandheldInstrumentItem;
                        boolean hides = spot.mode() != null || spot.station() == Station.STANDING && carried;
                        // 弹钢琴两只手都在琴键上，没有要另外画的东西
                        boolean held = hides && spot.mode() != MusicianPoses.Mode.KEYS;
                        LOG.info("maid {}: seen {}, expected hides {} held {}", key(row, spot), seen, hides, held);
                        if (seen == null || seen.hides() != hides || seen.mode() != spot.mode() || seen.held() != held) ok = false;
                    }
                }
                return ok;
            })
            .check("GeckoLib maids have the musician clips in their animation file", ctx -> {
                for (Row row : rows) {
                    if (!row.gecko()) continue;
                    for (Spot spot : SPOTS) {
                        if (!(client(row, spot) instanceof LivingEntity maid) || !MaidMusicians.hasClips(maid)) return false;
                    }
                }
                return true;
            })
            .check("GeckoLib maids play the clip of what they ride: bench, cello seat or standing post", ctx -> {
                boolean ok = true;
                for (Row row : rows) {
                    if (!row.gecko()) continue;
                    for (Spot spot : SPOTS) {
                        String expected = switch (spot.station()) {
                            case PIANO, BENCH -> "vehicle#starbloom_orchestra:bench_seat";
                            case CELLO -> "vehicle#starbloom_orchestra:instrument_seat";
                            case GLOCKENSPIEL -> "vehicle#starbloom_orchestra:standing_post";
                            case STANDING -> "";
                        };
                        String clip = client(row, spot) instanceof LivingEntity maid ? MaidMusicians.vehicleClip(maid) : null;
                        if (!expected.equals(clip)) {
                            LOG.info("maid {} rides into clip '{}', expected '{}'", key(row, spot), clip, expected);
                            ok = false;
                        }
                    }
                }
                return ok;
            })
            .teardown("stop the notes, own eyes", ctx -> {
                for (Map.Entry<String, Integer> e : MAIDS.entrySet()) {
                    MusicClient.heardHeld(new MusicPayloads.HeldNotesOut(e.getValue(), InstrumentKind.VIOLIN.ordinal(), true, List.of()));
                }
                var mc = Minecraft.getInstance();
                mc.setScreen(null);
                if (mc.player != null) mc.setCameraEntity(mc.player);
                mc.options.hideGui = false;
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            })
            .teardownServer("stop the rolls", ctx -> {
                ServerLevel level = ctx.server().overworld();
                for (Row row : rows) {
                    for (Spot spot : SPOTS) {
                        BlockPos pos = instrument(row, spot);
                        if (pos != null && level.getBlockEntity(pos) instanceof InstrumentBlockEntity be) be.stopPlayback();
                    }
                }
            });
    }
}
