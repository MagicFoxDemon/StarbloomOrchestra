package dev.starbloom.orchestra.client.ponder;

import static dev.starbloom.orchestra.ponder.PonderLayouts.*;

import dev.starbloom.orchestra.music.Benches;
import dev.starbloom.orchestra.music.InstrumentKind;
import dev.starbloom.orchestra.music.InstrumentPosts;
import dev.starbloom.orchestra.music.Instruments;
import dev.starbloom.orchestra.music.MusicStandBlock;
import dev.starbloom.orchestra.music.StarbloomDuetBenchBlock;
import dev.starbloom.orchestra.music.orchestra.Orchestra;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 思索场景的脚本（docs/PONDER.md）。坐标和方块都来自 {@code PonderLayouts}，场景里讲的事由世界测试 {@code ponder_layouts} 核对过。
 * 英文是默认文本，中文在 zh_cn.json（键 {@code starbloom_orchestra.ponder.<场景>.text_<序号>}，按 showText 的先后编号）。
 * 文字一句只讲一件事，不讲显而易见的。
 */
public final class OrchestraScenes {
    private OrchestraScenes() {}

    private static Selection at(SceneBuildingUtil util, BlockPos pos) {
        return util.select().position(pos);
    }

    /** 琴凳的归属：钢琴前、放错的一面、双人琴凳、竖琴后。 */
    public static void benches(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("benches", "Benches and the instruments they belong to");
        scene.configureBasePlate(0, 0, BENCHES.plate());
        scene.scaleSceneView(1.2f);
        scene.showBasePlate();
        scene.idle(10);

        Selection piano = util.select().fromTo(BENCHES_PIANO, BENCHES_PIANO_FAR);
        scene.world().showSection(piano, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80)
            .text("A grand piano, harpsichord, celesta or organ console is played sitting down, from the side its keys face")
            .pointAt(util.vector().blockSurface(BENCHES_PIANO, Direction.NORTH))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        scene.world().showSection(at(util, BENCHES_WRONG), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.RED, "wrong", at(util, BENCHES_WRONG), 70);
        scene.overlay().showText(70)
            .colored(PonderPalette.RED)
            .text("A bench beside or behind it is just a bench")
            .pointAt(util.vector().topOf(BENCHES_WRONG))
            .placeNearTarget();
        scene.idle(80);
        scene.world().hideSection(at(util, BENCHES_WRONG), Direction.UP);
        scene.idle(15);

        scene.world().showSection(at(util, BENCHES_AT_KEYS), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.GREEN, "linked", piano.copy().add(at(util, BENCHES_AT_KEYS)), 80);
        scene.overlay().showText(80)
            .colored(PonderPalette.GREEN)
            .text("In front of the keys and facing them, the bench belongs to the piano: whoever sits on it plays the piano")
            .pointAt(util.vector().topOf(BENCHES_AT_KEYS))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(BENCHES_AT_KEYS), Pointing.DOWN, 60).rightClick();
        scene.overlay().showText(70)
            .text("Right-click to sit down. To seat a mob, lead it on a leash and right-click the bench")
            .pointAt(util.vector().topOf(BENCHES_AT_KEYS))
            .placeNearTarget();
        scene.idle(80);
        scene.overlay().showControls(util.vector().topOf(BENCHES_AT_KEYS), Pointing.DOWN, 60).rightClick().whileSneaking();
        scene.overlay().showText(70)
            .text("Sneak and right-click with an empty hand to make a seated mob get up")
            .pointAt(util.vector().topOf(BENCHES_AT_KEYS))
            .placeNearTarget();
        scene.idle(80);

        // 换成双人琴凳：主块在东，另一格（坐的人右手边）在西
        var duet = Benches.DUET.get().defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        scene.world().setBlock(BENCHES_DUET, duet.setValue(StarbloomDuetBenchBlock.SIDE, false), false);
        scene.world().setBlock(BENCHES_AT_KEYS, duet.setValue(StarbloomDuetBenchBlock.SIDE, true), false);
        scene.world().showSection(at(util, BENCHES_DUET), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(80)
            .text("A grand piano is two wide: a duet bench in front of it seats its player in the middle")
            .pointAt(util.vector().topOf(BENCHES_DUET))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        scene.world().hideSection(piano.copy().add(at(util, BENCHES_AT_KEYS)).add(at(util, BENCHES_DUET)), Direction.UP);
        scene.idle(15);
        Selection harp = util.select().fromTo(BENCHES_HARP, BENCHES_HARP.above());
        scene.world().showSection(harp, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(at(util, BENCHES_BEHIND_HARP), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.GREEN, "harp", harp.copy().add(at(util, BENCHES_BEHIND_HARP)), 80);
        scene.overlay().showText(80)
            .colored(PonderPalette.GREEN)
            .text("The harp is the other way round: its bench goes behind it, facing the same way as the harp")
            .pointAt(util.vector().topOf(BENCHES_BEHIND_HARP))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);
        scene.markAsFinished();
    }

    /** 乐谱架和手持乐器：没对着乐器的琴凳演奏手里的东西，前面的空乐谱架就是它的谱台。 */
    public static void musicStand(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("music_stand", "Music stands and handheld instruments");
        scene.configureBasePlate(0, 0, MUSIC_STAND.plate());
        // 乐器是精细的小模型：5×5 的场景放大 1.4 倍、7×7 的 1.2 倍，看得清乐器的样子
        scene.scaleSceneView(1.4f);
        scene.showBasePlate();
        scene.idle(10);

        scene.world().showSection(at(util, STAND_BENCH), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(80)
            .text("A bench that faces no instrument plays whatever its sitter holds")
            .pointAt(util.vector().topOf(STAND_BENCH))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);
        scene.overlay().showControls(util.vector().topOf(STAND_BENCH), Pointing.DOWN, 60).rightClick()
            .withItem(new ItemStack(Instruments.VIOLIN.get()));
        scene.overlay().showText(80)
            .text("Right-click a seated mob with a violin, flute or another handheld instrument to hand it over; with an empty hand to take it back")
            .pointAt(util.vector().topOf(STAND_BENCH))
            .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(at(util, STAND), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70)
            .text("Put an empty music stand in front of it, its score facing the bench")
            .pointAt(util.vector().topOf(STAND))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(80);
        scene.overlay().showControls(util.vector().topOf(STAND), Pointing.DOWN, 60).rightClick();
        scene.overlay().showText(70)
            .text("Right-click the stand to open the seated player's instrument")
            .pointAt(util.vector().topOf(STAND))
            .placeNearTarget();
        scene.idle(80);
        scene.overlay().showControls(util.vector().topOf(STAND), Pointing.DOWN, 60).rightClick()
            .withItem(new ItemStack(Instruments.MUSIC_ROLL_ITEM.get()));
        scene.overlay().showText(70)
            .text("Right-click it with a punched roll and the player plays from the roll")
            .pointAt(util.vector().topOf(STAND))
            .placeNearTarget();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(STAND), Pointing.DOWN, 40).rightClick()
            .withItem(new ItemStack(Instruments.VIOLIN.get()));
        scene.idle(20);
        scene.world().modifyBlock(STAND, s -> s.setValue(MusicStandBlock.HOLDS, MusicStandBlock.Holds.VIOLIN), false);
        scene.idle(10);
        scene.overlay().showText(80)
            .text("Right-click it with a handheld instrument and the instrument rests there: the stand itself is then that instrument, and the bench is ignored")
            .pointAt(util.vector().topOf(STAND))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);
        scene.markAsFinished();
    }

    /** 大提琴、低音提琴：人在琴后，就在乐器这一格里（大提琴坐琴后的凳子，低音提琴站着）。 */
    public static void postsStrings(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("posts_strings", "Cello and double bass");
        scene.configureBasePlate(0, 0, POSTS_STRINGS.plate());
        scene.scaleSceneView(1.4f);
        scene.showBasePlate();
        scene.idle(10);

        scene.world().showSection(util.select().fromTo(STRINGS_CELLO, STRINGS_CELLO.above()), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(STRINGS_BASS, STRINGS_BASS.above()), Direction.DOWN);
        scene.idle(15);
        Vec3 stool = InstrumentPosts.position(STRINGS_CELLO, STRINGS_FACING, InstrumentKind.CELLO).add(0, 0.4, 0);
        scene.overlay().showText(80)
            .text("A cello is played from the stool behind it, a double bass standing behind it")
            .pointAt(stool)
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(STRINGS_CELLO.above()), Pointing.DOWN, 60).rightClick();
        scene.overlay().showText(70)
            .text("Right-click it with an empty hand to take the post and open the instrument")
            .pointAt(util.vector().topOf(STRINGS_CELLO.above()))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(80);
        scene.overlay().showControls(util.vector().topOf(STRINGS_BASS.above()), Pointing.DOWN, 60).rightClick()
            .withItem(new ItemStack(Items.LEAD));
        scene.overlay().showText(80)
            .text("Right-click it while leading a mob to put the mob there; sneak and right-click with an empty hand to send it away")
            .pointAt(util.vector().topOf(STRINGS_BASS.above()))
            .placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    /** 站着敲的：钟琴、管钟、小军鼓的人在前面，侧放的大鼓的人在侧面。 */
    public static void postsMallets(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("posts_mallets", "Glockenspiel, tubular bells and drums");
        scene.configureBasePlate(0, 0, POSTS_MALLETS.plate());
        scene.scaleSceneView(1.2f);
        scene.showBasePlate();
        scene.idle(10);

        scene.world().showSection(at(util, MALLETS_GLOCKENSPIEL), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(MALLETS_BELLS, MALLETS_BELLS.above()), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(at(util, MALLETS_SNARE), Direction.DOWN);
        scene.idle(15);
        Selection posts = at(util, MALLETS_GLOCKENSPIEL.north()).add(at(util, MALLETS_BELLS.north())).add(at(util, MALLETS_SNARE.north()));
        scene.overlay().showOutline(PonderPalette.BLUE, "posts", posts, 90);
        scene.overlay().showText(80)
            .text("These are played standing, from the side you were on when you placed them. Suspended cymbal and triangle too")
            // 指向最靠镜头右边的那一格，文字框才不会盖住另外两件
            .pointAt(util.vector().centerOf(MALLETS_GLOCKENSPIEL.north()))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        scene.world().showSection(at(util, MALLETS_BASS_DRUM), Direction.DOWN);
        scene.idle(10);
        BlockPos drummer = MALLETS_BASS_DRUM.west();
        scene.overlay().showOutline(PonderPalette.BLUE, "drummer", at(util, drummer), 80);
        scene.overlay().showText(80)
            .text("The bass drum stands on its side: its player stands beside one of its heads")
            .pointAt(util.vector().centerOf(drummer))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(MALLETS_GLOCKENSPIEL), Pointing.DOWN, 60).rightClick();
        scene.overlay().showText(80)
            .text("Right-click with an empty hand to step up to it. Mobs are led there on a leash, as at a cello")
            .pointAt(util.vector().topOf(MALLETS_GLOCKENSPIEL))
            .placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    /** 定音鼓：鼓后的双人琴凳对着两面鼓，单人琴凳对着一面。 */
    public static void timpani(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("timpani", "Timpani and the duet bench");
        scene.configureBasePlate(0, 0, TIMPANI.plate());
        scene.scaleSceneView(1.4f);
        scene.showBasePlate();
        scene.idle(10);

        Selection drums = at(util, TIMPANI_WEST).add(at(util, TIMPANI_EAST));
        scene.world().showSection(drums, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
            .text("Each kettledrum is one instrument in the orchestra")
            .pointAt(util.vector().topOf(TIMPANI_WEST))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(80);

        Selection bench = at(util, TIMPANI_BENCH).add(at(util, TIMPANI_BENCH_SIDE));
        scene.world().showSection(bench, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.GREEN, "pair", drums.copy().add(bench), 90);
        scene.overlay().showText(90)
            .colored(PonderPalette.GREEN)
            .text("A duet bench behind two drums, facing the same way, plays both: left hand on the left drum, right hand on the right")
            // 指向画面最右边那面鼓，文字框才不压住鼓和琴凳
            .pointAt(util.vector().topOf(TIMPANI_WEST))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);

        scene.world().setBlock(TIMPANI_BENCH_SIDE, Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(TIMPANI_BENCH, Benches.BENCH.get().defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), false);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.GREEN, "single", at(util, TIMPANI_EAST).add(at(util, TIMPANI_BENCH)), 80);
        scene.overlay().showText(80)
            .text("A single bench plays only the drum in front of it, with both hands")
            .pointAt(util.vector().topOf(TIMPANI_BENCH))
            .placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    /** 搭一台管风琴：演奏台、风箱、音管。 */
    public static void pipeOrgan(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("pipe_organ", "Building a pipe organ");
        scene.configureBasePlate(0, 0, PIPE_ORGAN.plate());
        // 音管有 7 格高：镜头往下挪一格，管顶才不出画面
        scene.setSceneOffsetY(-1);
        scene.showBasePlate();
        scene.idle(10);

        Selection console = util.select().fromTo(ORGAN_CONSOLE.west(), ORGAN_CONSOLE.east().above());
        scene.world().showSection(console, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(at(util, ORGAN_BENCH), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(80)
            .text("The organ console is three wide. Its bench goes in front of the middle")
            .pointAt(util.vector().topOf(ORGAN_BENCH))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        for (BlockPos chest : ORGAN_CHESTS) {
            scene.world().showSection(at(util, chest), Direction.DOWN);
            scene.idle(4);
        }
        scene.idle(6);
        scene.overlay().showText(80)
            .text("Wind chests go behind it, touching the console and each other")
            .pointAt(util.vector().topOf(ORGAN_CHESTS.get(1)))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);
        for (BlockPos chest : ORGAN_RAISED_CHESTS) {
            scene.world().showSection(at(util, chest), Direction.DOWN);
            scene.idle(4);
        }
        scene.idle(6);
        scene.overlay().showText(80)
            .text("They may turn, branch and climb a step at a time, up to 24 blocks from the console")
            .pointAt(util.vector().topOf(ORGAN_RAISED_CHESTS.get(1)))
            .placeNearTarget();
        scene.idle(90);

        int top = 0;
        for (int i = 0; i < ORGAN_TIN.size(); i++) top = Math.max(top, ORGAN_TIN.get(i).getY() + ORGAN_TIN_HEIGHT[i] - 1);
        for (int y = ORGAN_TIN.get(0).getY(); y <= top; y++) {
            scene.world().showSection(util.select().fromTo(ORGAN_TIN.get(0).getX(), y, ORGAN_TIN.get(0).getZ(),
                ORGAN_TIN.get(2).getX(), y, ORGAN_TIN.get(2).getZ()), Direction.DOWN);
            scene.idle(5);
        }
        scene.overlay().showText(80)
            .text("Pipes stand on wind chests. Stacked up, they join into one long pipe")
            .pointAt(util.vector().topOf(ORGAN_TIN.get(1).above(ORGAN_TIN_HEIGHT[1] - 1)))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        for (BlockPos wood : ORGAN_WOOD) scene.world().showSection(util.select().fromTo(wood, wood.above(2)), Direction.DOWN);
        scene.idle(8);
        for (BlockPos trumpet : ORGAN_TRUMPETS) scene.world().showSection(at(util, trumpet), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(80)
            .text("Wood pipes stack the same way. A trumpet pipe sticks out towards where you faced when you placed it")
            .pointAt(util.vector().blockSurface(ORGAN_TRUMPETS.get(0), Direction.NORTH))
            .placeNearTarget();
        scene.idle(90);

        scene.overlay().showText(90)
            .text("Each kind of pipe unlocks its stops. With no pipes at all, only the quiet 8' flute plays")
            // 讲的是整台琴，不指着哪一格：文字放在画面一侧，不挡住音管
            .independent()
            .attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(90)
            .text("The more pipes, the further it is heard: 16 blocks plus one per pipe block, up to 64. An organ counts at most 256 blocks")
            .independent();
        scene.idle(100);
        scene.overlay().showControls(util.vector().topOf(ORGAN_CONSOLE.above()), Pointing.DOWN, 60).rightClick();
        scene.overlay().showText(70)
            .text("Right-click the console with an empty hand: play it, set the stops, load a roll")
            .pointAt(util.vector().topOf(ORGAN_CONSOLE.above()))
            .placeNearTarget();
        scene.idle(80);
        scene.markAsFinished();
    }

    /** 指挥台：绑定指挥棒、点乐器加入、限制、指挥的操作。 */
    public static void podium(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("podium", "The conductor's podium");
        scene.configureBasePlate(0, 0, PODIUM_LAYOUT.plate());
        scene.showBasePlate();
        scene.idle(10);

        Selection podium = at(util, PODIUM).add(at(util, PODIUM.west()));
        scene.world().showSection(podium, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80)
            .text("To conduct, stand in the middle of the podium with a baton in your main hand")
            .pointAt(util.vector().topOf(PODIUM))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);
        ItemStack baton = new ItemStack(Orchestra.BATON.get());
        scene.overlay().showControls(util.vector().topOf(PODIUM), Pointing.DOWN, 60).rightClick().withItem(baton);
        scene.overlay().showText(70)
            .text("The first right-click with a baton ties it to this podium")
            .pointAt(util.vector().topOf(PODIUM))
            .placeNearTarget();
        scene.idle(80);

        Selection piano = util.select().fromTo(PODIUM_PIANO, PODIUM_PIANO.east().south());
        Selection cello = util.select().fromTo(PODIUM_CELLO, PODIUM_CELLO.above());
        Selection stand = at(util, PODIUM_STAND).add(at(util, PODIUM_STAND_BENCH));
        scene.world().showSection(piano.copy().add(at(util, PODIUM_PIANO_BENCH)), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(cello, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(stand, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showControls(util.vector().topOf(PODIUM_CELLO.above()), Pointing.DOWN, 60).rightClick().withItem(baton);
        scene.overlay().showOutline(PonderPalette.GREEN, "cello", cello, 70);
        scene.overlay().showText(70)
            .text("Right-click an instrument with the baton to add it to the orchestra; once more to take it out")
            .pointAt(util.vector().topOf(PODIUM_CELLO.above()))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(80);
        scene.overlay().showControls(util.vector().topOf(PODIUM_PIANO_BENCH), Pointing.DOWN, 60).rightClick().withItem(baton);
        scene.overlay().showOutline(PonderPalette.GREEN, "piano", piano.copy().add(at(util, PODIUM_PIANO_BENCH)), 70);
        scene.overlay().showText(70)
            .text("Clicking a bench that belongs to an instrument adds that instrument")
            .pointAt(util.vector().topOf(PODIUM_PIANO_BENCH))
            .placeNearTarget();
        scene.idle(80);
        scene.overlay().showControls(util.vector().topOf(PODIUM_STAND_BENCH), Pointing.DOWN, 60).rightClick().withItem(baton);
        scene.overlay().showOutline(PonderPalette.GREEN, "seat", stand, 70);
        scene.overlay().showText(70)
            .text("Any other bench joins as a seat: it plays whatever its musician holds")
            .pointAt(util.vector().topOf(PODIUM_STAND_BENCH))
            .placeNearTarget();
        scene.idle(80);

        scene.overlay().showText(80)
            .text("Up to 32 instruments within 32 blocks. Each instrument belongs to one orchestra at a time")
            // 后面三句讲的是整个乐团和指挥，不指着哪一格：文字放在画面一侧，不挡住乐器
            .independent()
            .attachKeyFrame();
        scene.idle(90);
        scene.overlay().showText(80)
            .text("In the podium's screen, Gather adds the free instruments up to 16 blocks in front of the podium")
            .independent();
        scene.idle(90);
        scene.overlay().showText(90)
            .text("On the podium: left-click to beat time, right-click an instrument to pick its part, scroll to change that part's volume")
            .independent()
            .attachKeyFrame();
        scene.idle(100);
        scene.markAsFinished();
    }
}
