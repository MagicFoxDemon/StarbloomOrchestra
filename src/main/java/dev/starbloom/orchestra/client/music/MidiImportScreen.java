package dev.starbloom.orchestra.client.music;

import dev.starbloom.orchestra.music.InstrumentBlockEntity;
import dev.starbloom.orchestra.music.Instruments;
import dev.starbloom.orchestra.music.MidiReader;
import dev.starbloom.orchestra.music.MusicPayloads;
import dev.starbloom.orchestra.music.MusicRoll;
import dev.starbloom.orchestra.music.RollInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 导入 MIDI：列出 &lt;游戏目录&gt;/logos_music/ 里的文件（见 {@link MidiLibrary}），选一个在后台线程解析，
 * 预览声部、时长、音符数与提示（打击乐略过、超限截断），然后「刻成纸带」（消耗乐器里的空白纸带）
 * 或「直接播放」（作为临时纸带上传，停止后服务端删除）。
 */
public class MidiImportScreen extends Screen {
    private static final int W = 400, H = 226, PAD = 28, ROW = 12, LIST_W = 150, VISIBLE = 10;

    private final BlockPos pos;
    private final Screen parent;
    private List<MidiLibrary.Entry> files = List.of();
    private @Nullable String error;
    private int scroll;
    private @Nullable String selected;
    private boolean parsing;
    private MidiReader.@Nullable Result result;
    private int keepMask = 0xFFFF;
    private int x0, y0;

    public MidiImportScreen(BlockPos pos, Screen parent) {
        super(Component.translatable("screen.starbloom_orchestra.instrument.import"));
        this.pos = pos.immutable();
        this.parent = parent;
        refresh();
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.starbloom_orchestra.instrument." + key, args);
    }

    private void refresh() {
        try {
            files = MidiLibrary.list();
            error = null;
        } catch (IOException e) {
            files = List.of();
            error = tr("folder_error").getString();
        }
        scroll = Math.max(0, Math.min(scroll, files.size() - VISIBLE));
    }

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
        int x = x0 + PAD, by = y0 + H - PAD - 16;
        addRenderableWidget(Button.builder(tr("open_folder"), b -> {
            try {
                Util.getPlatform().openFile(MidiLibrary.ensureFolder().toFile());
            } catch (IOException e) {
                error = tr("folder_error").getString();
            }
        }).bounds(x, by, 72, 16).build());
        addRenderableWidget(Button.builder(tr("refresh"), b -> refresh()).bounds(x + 76, by, 72, 16).build());
        int rx = x0 + PAD + LIST_W + 12;
        Button write = addRenderableWidget(Button.builder(tr("write_roll"), b -> send(MusicPayloads.Upload.WRITE)).bounds(rx, by, 70, 16).build());
        Button play = addRenderableWidget(Button.builder(tr("play_direct"), b -> send(MusicPayloads.Upload.TEMPORARY)).bounds(rx + 72, by, 70, 16).build());
        addRenderableWidget(Button.builder(tr("back"), b -> onClose()).bounds(x0 + W - PAD - 40, by, 40, 16).build());
        write.active = result != null;
        play.active = result != null;
        if (result != null) {
            var parts = result.roll().parts();
            for (int i = 0; i < parts.size(); i++) {
                int bit = 1 << i;
                String shown = ((keepMask & bit) != 0 ? "✔ " : "  ") + parts.get(i).name();
                if (font.width(shown) > 84) shown = font.plainSubstrByWidth(shown, 80) + "…";
                addRenderableWidget(Button.builder(Component.literal(shown), b -> {
                    keepMask ^= bit;
                    rebuildWidgets();
                }).bounds(rx + (i % 2) * 88, y0 + 92 + (i / 2) * 13, 86, 12).build());
            }
        }
    }

    boolean parsed() {
        return result != null;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 选中一个文件并在后台解析（场景测试也直接调用）。 */
    void pick(String name) {
        selected = name;
        result = null;
        error = null;
        parsing = true;
        keepMask = 0xFFFF;
        rebuildWidgets();
        CompletableFuture.supplyAsync(() -> {
            try {
                return MidiReader.read(MidiLibrary.read(name), MidiLibrary.title(name));
            } catch (MusicRoll.FormatException e) {
                throw new IllegalStateException(e.getMessage());
            } catch (IOException e) {
                throw new IllegalStateException(tr("read_error").getString());
            }
        }, Util.backgroundExecutor()).whenComplete((r, t) -> Minecraft.getInstance().execute(() -> {
            if (!name.equals(selected)) return;
            parsing = false;
            if (t != null) {
                Throwable cause = t.getCause() != null ? t.getCause() : t;
                error = cause.getMessage();
            } else {
                result = r;
            }
            rebuildWidgets();
        }));
    }

    /** 只保留勾选的声部，声部编号重新排。 */
    private @Nullable MusicRoll chosen() {
        if (result == null) return null;
        MusicRoll roll = result.roll();
        int[] remap = new int[roll.parts().size()];
        List<MusicRoll.Part> parts = new ArrayList<>();
        for (int i = 0; i < remap.length; i++) {
            if ((keepMask & (1 << i)) != 0) {
                remap[i] = parts.size();
                parts.add(roll.parts().get(i));
            } else {
                remap[i] = -1;
            }
        }
        if (parts.isEmpty()) return null;
        List<MusicRoll.Event> events = new ArrayList<>();
        for (MusicRoll.Event e : roll.events()) {
            int p = remap[e.part()];
            if (p >= 0) events.add(new MusicRoll.Event(e.timeMs(), e.type(), p, e.pitch(), e.velocity(), e.durationMs()));
        }
        return new MusicRoll(roll.title(), roll.author(), roll.bpmX100(), roll.beatsPerBar(), roll.beatUnit(), parts, events, roll.tempos());
    }

    private void send(int purpose) {
        MusicRoll roll = chosen();
        if (roll == null) {
            error = tr("no_parts").getString();
            return;
        }
        if (purpose == MusicPayloads.Upload.WRITE) {
            var level = Minecraft.getInstance().level;
            if (level == null || !(level.getBlockEntity(pos) instanceof InstrumentBlockEntity be) || !Instruments.isRoll(be.roll())) {
                error = tr("need_blank").getString();
                return;
            }
        }
        try {
            MusicClient.upload(pos, purpose, roll.encode());
            onClose();
        } catch (MusicRoll.FormatException e) {
            error = e.getMessage();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int lx = x0 + PAD, ly = y0 + 40;
        if (button == 0 && mouseX >= lx && mouseX < lx + LIST_W && mouseY >= ly && mouseY < ly + VISIBLE * ROW) {
            int i = scroll + (int) ((mouseY - ly) / ROW);
            if (i < files.size() && !files.get(i).tooBig()) pick(files.get(i).name());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, files.size() - VISIBLE), scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Screen.render 先画原版背景（压暗）再画控件；羊皮纸和列表要夹在两者之间，所以拆开来调用
        renderBackground(g);
        Parchment.draw(g, x0, y0, W, H);
        int lx = x0 + PAD, ly = y0 + 40;
        g.drawString(font, title, lx, y0 + 24, Parchment.INK_TITLE, false);
        g.drawString(font, tr("folder_hint", MidiLibrary.FOLDER), lx + 80, y0 + 24, Parchment.INK_HINT, false);
        g.fill(lx, ly, lx + LIST_W, ly + VISIBLE * ROW, 0x30FFFFFF);
        g.renderOutline(lx - 1, ly - 1, LIST_W + 2, VISIBLE * ROW + 2, Parchment.INK);
        if (files.isEmpty()) {
            g.drawString(font, tr("no_files"), lx + 4, ly + 4, Parchment.INK_HINT, false);
        }
        for (int i = 0; i < VISIBLE && scroll + i < files.size(); i++) {
            MidiLibrary.Entry f = files.get(scroll + i);
            int y = ly + i * ROW;
            if (f.name().equals(selected)) g.fill(lx, y, lx + LIST_W, y + ROW, 0x60E0B84A);
            String name = font.plainSubstrByWidth(f.name(), LIST_W - 6);
            g.drawString(font, Component.literal(name), lx + 3, y + 2, f.tooBig() ? 0xFFA08070 : Parchment.INK, false);
        }
        int rx = x0 + PAD + LIST_W + 12, ry = y0 + 40;
        if (parsing) {
            g.drawString(font, tr("parsing"), rx, ry, Parchment.INK_HINT, false);
        } else if (result != null) {
            MusicRoll roll = result.roll();
            g.drawString(font, Component.literal(font.plainSubstrByWidth(roll.title(), W - PAD - (rx - x0))), rx, ry, Parchment.INK_TITLE, false);
            g.drawString(font, tr("midi_info", RollInfo.clock(roll.durationMs()), roll.noteCount(), roll.parts().size()), rx, ry + 12, Parchment.INK, false);
            int y = ry + 24;
            for (String note : result.notes()) {
                for (var line : font.split(Component.literal(note), W - PAD - (rx - x0))) {
                    g.drawString(font, line, rx, y, 0xFF8A4A1A, false);
                    y += 10;
                }
            }
            g.drawString(font, tr("parts"), rx, y0 + 92 - 11, Parchment.INK_HINT, false);
        } else {
            g.drawString(font, tr("pick_file"), rx, ry, Parchment.INK_HINT, false);
        }
        if (error != null) {
            for (var line : font.split(Component.literal(error), W - PAD * 2)) {
                g.drawString(font, line, lx, y0 + H - PAD - 30, 0xFF8A2A1A, false);
                break;
            }
        }
        for (Renderable r : renderables) r.render(g, mouseX, mouseY, partialTick);
    }
}
