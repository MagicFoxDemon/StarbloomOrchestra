package dev.starbloom.orchestra.gametest;

import dev.starbloom.orchestra.StarbloomOrchestra;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.minecraft.gametest.framework.LogTestReporter;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.gametest.framework.TestReporter;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * 1.21.1 的世界测试框架：测试是 {@link TestFunction}，由各测试类里带 {@code @GameTestGenerator} 的方法交给
 * {@code RegisterGameTestsEvent}，每个测试要一份结构模板（{@code data/starbloom_orchestra/structure/gametest/empty_<边长>.nbt}，
 * {@code tools/gametest_structures.py} 生成）。
 *
 * <p>测试名就是原来的名字（{@code bench_links_to_instruments} 这样，不加类名前缀），一个测试类一批（同 26.1 一个环境一批）。
 * 26.1 的测试用 1×1×1 的空结构加 padding 格空出来的地方；1.21.1 没有 padding，测试框架还在结构外面围一圈屏障，所以模板做成
 * 同样大小的一块空气（边长 2×padding+1），只是测试的原点在模板的西北下角、不在正中。下面没有地板（26.1 的测试也悬在空中），
 * 1.21.1 清场时在模板下面第二格起铺石头。
 *
 * <p>1.21.1 的 {@code GameTestServer} 没有 {@code --tests}、{@code --report}、{@code --repeatCount}，这里读系统属性代替
 * （build.gradle 的 gameTestServer 按 -PorchestraTests、-PorchestraTestRepeat 设置）：
 * <ul>
 *   <li>{@value #TESTS}：要跑的测试，逗号分开，{@code starbloom_orchestra:instrument_*} 这样的通配；不设时全跑。</li>
 *   <li>{@value #REPEAT}：每个选中的测试复制几份放进同一批（查偶发失败用），复制的叫 {@code <名字>.2}、{@code <名字>.3}……</li>
 *   <li>{@value #REPORT}：JUnit 样式的结果写到哪个文件（日志里照常打印失败）。</li>
 * </ul>
 */
final class GameTestFunctions {
    static final String TESTS = "starbloom_orchestra.tests";
    static final String REPEAT = "starbloom_orchestra.test_repeat";
    static final String REPORT = "starbloom_orchestra.test_report";
    private static boolean reporterInstalled;
    /** 登记了几个测试类（每个类正好一个 {@code @GameTestGenerator}、调一次 {@link #batch}）。 */
    private static int holders;
    /** 已经调过几次 {@link #batch}、一共选中了几个测试：全部调完一个都没选中时同 26.1 以 -1 退出。 */
    private static int batches, matched;

    private GameTestFunctions() {}

    /** 世界测试开着时（开发环境的 gameTestServer）把测试类交给测试框架。 */
    static synchronized void register(IEventBus bus, Class<?> holder) {
        holders++;
        bus.addListener((RegisterGameTestsEvent event) -> {
            installReporter();
            event.register(holder);
        });
    }

    /**
     * 一批测试：{@code batch} 是 26.1 的环境名，{@code padding} 是 26.1 的 padding（决定模板大小），
     * {@code maxTicks}、{@code setupTicks} 同 26.1 的 TestData。都是必须通过的、不转向、只跑一次。
     */
    static synchronized Collection<TestFunction> batch(String batch, int padding, int maxTicks, long setupTicks, Map<String, Consumer<GameTestHelper>> tests) {
        String structure = StarbloomOrchestra.ID + ":gametest/empty_" + (2 * padding + 1);
        String batchName = StarbloomOrchestra.ID + ":" + batch;
        List<Pattern> selected = selection();
        int repeat = Math.max(1, Integer.getInteger(REPEAT, 1));
        List<TestFunction> out = new ArrayList<>();
        tests.forEach((name, test) -> {
            if (!selected.isEmpty() && selected.stream().noneMatch(p -> p.matcher(StarbloomOrchestra.ID + ":" + name).matches())) return;
            for (int i = 1; i <= repeat; i++) {
                out.add(new TestFunction(batchName, i == 1 ? name : name + "." + i, structure, Rotation.NONE, maxTicks, setupTicks,
                    true, false, 1, 1, false, test));
            }
        });
        matched += out.size();
        // 26.1 的 GameTestServer 在 --tests 一个都没选中时打警告、以 -1 退出；1.21.1 的会在启动时抛「No test functions were
        // given!」、正常退出，Gradle 当成成功。所以最后一批也没选中任何测试时在这里照 26.1 退出
        if (++batches == holders && matched == 0 && !selected.isEmpty()) {
            org.slf4j.LoggerFactory.getLogger(GameTestFunctions.class).warn("Test selection matcher ({}) found no tests", System.getProperty(TESTS));
            System.exit(-1);
        }
        return out;
    }

    /** {@value #TESTS} 的通配换成正则；没写命名空间的按本模组的算。 */
    private static List<Pattern> selection() {
        List<Pattern> out = new ArrayList<>();
        for (String glob : System.getProperty(TESTS, "").split(",")) {
            glob = glob.strip();
            if (glob.isEmpty()) continue;
            if (glob.indexOf(':') < 0) glob = StarbloomOrchestra.ID + ":" + glob;
            StringBuilder regex = new StringBuilder();
            for (char c : glob.toCharArray()) {
                if (c == '*') regex.append(".*");
                else if (c == '?') regex.append('.');
                else regex.append(Pattern.quote(String.valueOf(c)));
            }
            out.add(Pattern.compile(regex.toString()));
        }
        return out;
    }

    /** 设了 {@value #REPORT} 时，结果除了照常写进日志，还写成 JUnit 样式的 XML（GameTestServer 跑完时 {@code GlobalTestReporter.finish()}）。 */
    private static synchronized void installReporter() {
        String path = System.getProperty(REPORT, "");
        if (reporterInstalled || path.isEmpty()) return;
        reporterInstalled = true;
        File file = new File(path);
        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        try {
            TestReporter log = new LogTestReporter();
            TestReporter junit = new JUnitLikeTestReporter(file);
            GlobalTestReporter.replaceWith(new TestReporter() {
                @Override
                public void onTestFailed(GameTestInfo info) {
                    log.onTestFailed(info);
                    junit.onTestFailed(info);
                }

                @Override
                public void onTestSuccess(GameTestInfo info) {
                    log.onTestSuccess(info);
                    junit.onTestSuccess(info);
                }

                @Override
                public void finish() {
                    log.finish();
                    junit.finish();
                }
            });
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("cannot write the game test report to " + path, e);
        }
    }
}
