package dev.starbloom.orchestra.client.ponder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import com.mojang.logging.LogUtils;
import dev.starbloom.orchestra.StarbloomOrchestra;
import dev.starbloom.orchestra.music.Instruments;
import dev.starbloom.orchestra.music.orchestra.Orchestra;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import org.slf4j.Logger;

/**
 * 思索场景的验收（docs/PONDER.md）：Ponder 自己生成的文本键（{@code provideLang}）在 en_us、zh_cn 里都有，英文和场景代码一字不差；
 * 再按英文、中文各把七个场景打开一遍，在每个关键帧截图。带 {@code -PponderLangExport} 时先把场景代码里的英文写进 en_us.json。
 */
@LDLRegisterClient(name = "ponder_scenes_ui", group = "ponder", registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public class PonderViews implements UIScenario {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = StarbloomOrchestra.ID + ".ponder.";

    /** 场景、打开它用的物品（只有这一个场景的物品，打开就是它）、关键帧数（attachKeyFrame 的次数）。 */
    private record Shot(String scene, Supplier<? extends ItemLike> item, int keyframes) {}

    private static final List<Shot> SHOTS = List.of(
        new Shot("benches", Instruments.GRAND_PIANO_ITEM, 4),
        new Shot("music_stand", Instruments.MUSIC_STAND_ITEM, 3),
        new Shot("posts_strings", Instruments.CELLO_ITEM, 2),
        new Shot("posts_mallets", Instruments.GLOCKENSPIEL_ITEM, 2),
        new Shot("timpani", Instruments.TIMPANI_ITEM, 2),
        new Shot("pipe_organ", Instruments.ORGAN_CONSOLE_ITEM, 4),
        new Shot("podium", Orchestra.PODIUM_ITEM, 4));
    private static final List<String> TAGS = List.of("musicians", "pipe_organ", "orchestra");
    /** 打开场景时它放到了第几刻（核对场景自己会往下放）。 */
    private static int openedAt;

    @Override
    public void configure(ScenarioOptions o) {
        o.tags("ponder").defaultTimeoutMs(180000).scenarioTimeoutMs(900000);
    }

    private static PonderUI ui() {
        return Minecraft.getInstance().screen instanceof PonderUI ui ? ui : null;
    }

    @Override
    public void define(ScenarioBuilder s) {
        s = s.setGameMode(GameType.CREATIVE)
            .check("every Ponder text has English and Chinese", ctx -> langProblems().isEmpty());
        s = scenes(s, "en");
        s = language(s, "zh_cn", "琴凳归哪件乐器");
        s = scenes(s, "zh");
        language(s, "en_us", "Benches and the instruments they belong to");
    }

    private static ScenarioBuilder language(ScenarioBuilder s, String code, String benchesHeader) {
        return s.closeScreen()
            .step("switch the language to " + code, ctx -> {
                var mc = Minecraft.getInstance();
                mc.options.languageCode = code;
                mc.getLanguageManager().setSelected(code);
                mc.reloadResourcePacks();
            })
            .waitUntil(code + " is loaded", ctx -> Minecraft.getInstance().getOverlay() == null
                && I18n.get(PREFIX + "benches.header").equals(benchesHeader))
            .ticks(20);
    }

    private static ScenarioBuilder scenes(ScenarioBuilder s, String lang) {
        for (Shot shot : SHOTS) {
            // 同按住 W 一样经 ScreenOpener.transitionTo 打开：直接 setScreen 的话界面的过渡值停在 0，整个界面放大 1.5 倍，标题和按钮在屏幕外
            s = s.closeScreen()
                .step("hold W on " + shot.scene() + " (" + lang + ")", ctx -> ScreenOpener.transitionTo(PonderUI.of(new ItemStack(shot.item().get()))))
                .waitUntil(shot.scene() + " is on screen", ctx -> ui() != null)
                .ticks(30)
                .check(shot.scene() + " opens", ctx -> ui() != null && ui().getActiveScene().getId().equals(StarbloomOrchestra.id(shot.scene())))
                .check(shot.scene() + " has " + shot.keyframes() + " key frames", ctx -> {
                    int n = ui().getActiveScene().getKeyframeCount();
                    if (n != shot.keyframes()) LOGGER.error("{} has {} key frames", shot.scene(), n);
                    return n == shot.keyframes();
                })
                // 不拖时间轴，场景自己往下放（界面每刻都在走），截一张刚打开时的整个界面
                .step("note where " + shot.scene() + " is", ctx -> openedAt = ui().getActiveScene().getCurrentTime())
                .ticks(40)
                .screenshot("ponder_" + lang + "_" + shot.scene() + "_open")
                .check(shot.scene() + " plays by itself", ctx -> {
                    int now = ui().getActiveScene().getCurrentTime();
                    LOGGER.info("{} played from {} to {} in 40 ticks", shot.scene(), openedAt, now);
                    return now > openedAt;
                });
            for (int k = 0; k < shot.keyframes(); k++) {
                int index = k;
                s = s.step("seek " + shot.scene() + " key frame " + (k + 1), ctx -> ui().seekToTime(ui().getActiveScene().getKeyframeTime(index) + 25))
                    .ticks(20)
                    .screenshot("ponder_" + lang + "_" + shot.scene() + "_" + (k + 1));
            }
            s = s.step("seek " + shot.scene() + " end", ctx -> ui().seekToTime(ui().getActiveScene().getTotalTime() - 2))
                .ticks(15)
                .screenshot("ponder_" + lang + "_" + shot.scene() + "_end");
        }
        return s;
    }

    // —— 文本 ——

    /** Ponder 生成的键：英文同 en_us.json，中文要有、不是空的、不是照抄英文；语言文件里也不能留着场景里已经没有的键。 */
    static List<String> langProblems() {
        Map<String, String> ponder = new LinkedHashMap<>();
        PonderIndex.getLangAccess().provideLang(StarbloomOrchestra.ID, ponder::put);
        // provideLang 又注册了一遍全部场景（不先清空），每个场景会出现两次：重新载入一遍
        PonderIndex.reload();
        String export = System.getProperty("starbloom_orchestra.ponder_lang_export", "");
        Path dir = export.isEmpty() ? null : Path.of(export);
        if (dir != null) exportEnglish(dir.resolve("en_us.json"), ponder);
        JsonObject en = lang(dir, "en_us"), zh = lang(dir, "zh_cn");
        List<String> problems = new ArrayList<>();
        if (ponder.isEmpty()) problems.add("Ponder provided no text for " + StarbloomOrchestra.ID);
        ponder.forEach((key, english) -> {
            if (!en.has(key) || !en.get(key).getAsString().equals(english)) problems.add("en_us " + key + " should be: " + english);
            if (!zh.has(key) || zh.get(key).getAsString().isBlank() || zh.get(key).getAsString().equals(english)) problems.add("zh_cn has no " + key);
        });
        for (JsonObject lang : List.of(en, zh)) {
            for (String key : lang.keySet()) if (key.startsWith(PREFIX) && !ponder.containsKey(key)) problems.add("no scene uses " + key);
        }
        LOGGER.info("Ponder text: {} keys, {} problems", ponder.size(), problems.size());
        problems.forEach(LOGGER::error);
        return problems;
    }

    private static JsonObject lang(Path dir, String locale) {
        try (Reader r = dir != null ? Files.newBufferedReader(dir.resolve(locale + ".json"), StandardCharsets.UTF_8)
                : new InputStreamReader(PonderViews.class.getResourceAsStream("/assets/" + StarbloomOrchestra.ID + "/lang/" + locale + ".json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 场景的顺序、每个场景里 header 在前、text_N 按数字排；最后是标签。 */
    private static List<String> ordered(Set<String> keys) {
        Set<String> out = new LinkedHashSet<>();
        for (Shot shot : SHOTS) {
            String scene = PREFIX + shot.scene() + ".";
            if (keys.contains(scene + "header")) out.add(scene + "header");
            for (int i = 1; keys.contains(scene + "text_" + i); i++) out.add(scene + "text_" + i);
        }
        for (String tag : TAGS) {
            String key = PREFIX + "tag." + tag;
            if (keys.contains(key)) out.add(key);
            if (keys.contains(key + ".description")) out.add(key + ".description");
        }
        out.addAll(new TreeSet<>(keys));
        return new ArrayList<>(out);
    }

    /** 把 en_us.json 里本模组的思索键换成场景代码里的英文：别的行一个字节都不动，思索键放在最后。 */
    private static void exportEnglish(Path file, Map<String, String> ponder) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<String> body = new ArrayList<>();
            for (String line : lines.subList(1, lines.size() - 1)) {
                if (line.stripLeading().startsWith("\"" + PREFIX)) continue;
                body.add(line.endsWith(",") ? line.substring(0, line.length() - 1) : line);
            }
            Gson gson = new GsonBuilder().disableHtmlEscaping().create();
            for (String key : ordered(ponder.keySet())) body.add("  " + gson.toJson(key) + ": " + gson.toJson(ponder.get(key)));
            Files.writeString(file, "{\n" + String.join(",\n", body) + "\n}\n", StandardCharsets.UTF_8);
            LOGGER.info("Wrote {} Ponder keys into {}", ponder.size(), file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
