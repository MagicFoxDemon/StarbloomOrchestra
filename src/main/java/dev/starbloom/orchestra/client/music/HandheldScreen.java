package dev.starbloom.orchestra.client.music;

import dev.starbloom.orchestra.music.InstrumentKind;
import dev.starbloom.orchestra.music.Instruments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

/**
 * 拿在手上的乐器的演奏条：贴在屏幕底部的一条羊皮纸，上面是屏幕键盘，世界照常显示（不模糊、不暂停），
 * 自己的演奏姿势别人也看得到。键位同乐器界面的演奏页（{@link KeyLayout}），←→ 换八度、↑↓ 改力度；
 * 电子琴也能弹（{@link MidiKeyboard}）。乐器离手或关掉演奏条时全部松开，附近的人那里也一起松开。
 * <p>
 * 无音高的手持乐器（神乐铃）没有键盘，每个键一块大的打击板：下面两排字母都是第一块（一振），最上一排字母和数字是第二块
 * （连摇，按住一直摇）；两只手轮流按同一排的不同字母就是连着几下。
 */
public class HandheldScreen extends Screen implements MidiKeyboard.Target {
    private static final int W = 400, H = 104;
    private static final int KEY_LEFT = 263, KEY_RIGHT = 262, KEY_UP = 265, KEY_DOWN = 264;

    private final InstrumentKind kind;
    private final InteractionHand hand;
    private int base = 60;
    private int velocity = 96;
    private final Map<Integer, Integer> held = new HashMap<>();
    private final Set<Integer> midiHeld = new HashSet<>();
    private int mousePitch = -1;
    private int x0, y0;
    /** 无音高的乐器：打击板代替键盘。 */
    private final boolean pads;
    /** 打击板的键位：下两排 → 第一块，最上一排和数字 → 第二块。 */
    private static final String[] PAD_ROWS = {"ZXCVBNM,./ASDFGHJKL;'", "QWERTYUIOP1234567890"};

    public HandheldScreen(InstrumentKind kind, InteractionHand hand) {
        super(Component.translatable(kind.nameKey()));
        this.kind = kind;
        this.hand = hand;
        // 键盘从和音域重合最多的那个 C 开始（以前固定从 C4，巴松管的低音一半弹不到）
        this.base = KeyLayout.defaultBase(kind, KeyLayout.chromatic().length(), 60);
        this.pads = kind.unpitched();
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.starbloom_orchestra.instrument." + key, args);
    }

    InstrumentKind kind() {
        return kind;
    }

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = height - H - 6;
        MidiKeyboard.setTarget(this);
    }

    @Override
    public void added() {
        super.added();
        MidiKeyboard.autoConnect();
    }

    @Override
    public void removed() {
        MidiKeyboard.clearTarget(this);
        releaseAll();
        MusicClient.localHeldOff(kind);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 不画模糊的背景：演奏时看得到世界和别的乐手。 */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
    }

    @Override
    public void tick() {
        var me = Minecraft.getInstance().player;
        // 乐器离手（被换掉、丢掉）就收起
        if (me == null || Instruments.heldKind(me.getItemInHand(hand)) != kind) onClose();
    }

    // —— 弹奏 ——

    private void note(int pitch, int vel, long nanos) {
        MusicClient.localHeld(kind, pitch, vel, nanos);
    }

    private void shiftOctave(int d) {
        releaseAll();
        base = KeyLayout.clampBase(kind, base + 12 * d, KeyLayout.chromatic().length());
    }

    /** 不在这件乐器音域里的键不出声（不按八度折叠，界面上画暗）；无音高的乐器只认自己的键。 */
    private boolean playable(int pitch) {
        return kind.sounds(pitch);
    }

    /** 打击板模式下这个键码按的是哪个鼓键，没有映射时 -1。 */
    private int padPitch(int key) {
        int[] keys = kind.keys();
        for (int i = 0; i < PAD_ROWS.length && i < keys.length; i++) {
            if (PAD_ROWS[i].indexOf((char) key) >= 0) return keys[i];
        }
        return -1;
    }

    private void releaseAll() {
        long now = System.nanoTime();
        for (int p : new ArrayList<>(held.values())) note(p, 0, now);
        held.clear();
        for (int p : new ArrayList<>(midiHeld)) note(p, 0, now);
        midiHeld.clear();
        if (mousePitch >= 0) note(mousePitch, 0, now);
        mousePitch = -1;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) return super.keyPressed(event);
        int key = event.key();
        switch (key) {
            case KEY_LEFT -> { if (!pads) shiftOctave(-1); }
            case KEY_RIGHT -> { if (!pads) shiftOctave(1); }
            case KEY_UP -> velocity = Math.min(127, velocity + 16);
            case KEY_DOWN -> velocity = Math.max(16, velocity - 16);
            default -> {
                int off = KeyLayout.chromaticOffset(KeyLayout.chromatic(), key);
                int pitch = pads ? padPitch(key) : off >= 0 ? base + off : -1;
                if (pitch >= 0 && !held.containsKey(key) && playable(pitch)) {
                    held.put(key, pitch);
                    note(pitch, velocity, System.nanoTime());
                }
            }
        }
        setFocused(null);
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        Integer p = held.remove(event.key());
        if (p != null) note(p, 0, System.nanoTime());
        return true;
    }

    @Override
    public void midiNote(int pitch, int vel, long nanos) {
        if (!playable(pitch)) return;
        if (!midiHeld.add(pitch)) note(pitch, 0, nanos);
        note(pitch, vel, nanos);
    }

    @Override
    public void midiOff(int pitch, long nanos) {
        if (midiHeld.remove(pitch)) note(pitch, 0, nanos);
    }

    @Override
    public void midiPedal(boolean down, long nanos) {
        // 手持乐器没有踏板
    }

    @Override
    public void midiAllOff() {
        long now = System.nanoTime();
        for (int p : new ArrayList<>(midiHeld)) note(p, 0, now);
        midiHeld.clear();
    }

    @Override
    public void midiProgram(int program) {
    }

    /** 测试用：键盘和电子琴上正按着几个音。 */
    int holding() {
        return held.size() + midiHeld.size() + (mousePitch >= 0 ? 1 : 0);
    }

    // —— 屏幕键盘 ——

    private record KeyRect(int pitch, int x, int y, int w, int h, boolean black, String label) {}

    private List<KeyRect> keyboard() {
        List<KeyRect> keys = new ArrayList<>();
        if (pads) {
            int[] drum = kind.keys();
            int left = x0 + 20, top = y0 + 30, gap = 10, w = (W - 40 - gap * (drum.length - 1)) / drum.length;
            for (int i = 0; i < drum.length; i++) {
                String row = i < PAD_ROWS.length ? PAD_ROWS[i] : "";
                String label = row.isEmpty() ? "" : row.charAt(0) + "–" + row.charAt(i == 0 ? 6 : 9);
                keys.add(new KeyRect(drum[i], left + i * (w + gap), top, w, 44, false, label));
            }
            return keys;
        }
        String layout = KeyLayout.chromatic();
        int left = x0 + 20, top = y0 + 30, area = W - 40, height = 44;
        int whites = 0;
        for (int i = 0; i < layout.length(); i++) if (!KeyLayout.isBlack(base + i)) whites++;
        int kw = Math.min(22, area / Math.max(1, whites));
        int wi = 0;
        List<KeyRect> blacks = new ArrayList<>();
        for (int i = 0; i < layout.length(); i++) {
            int p = base + i;
            String label = KeyLayout.label(layout.charAt(i));
            if (KeyLayout.isBlack(p)) {
                int bw = kw * 3 / 5;
                blacks.add(new KeyRect(p, left + wi * kw - bw / 2, top, bw, height * 3 / 5, true, label));
            } else {
                keys.add(new KeyRect(p, left + wi * kw, top, kw - 1, height, false, label));
                wi++;
            }
        }
        keys.addAll(blacks);
        return keys;
    }

    private boolean sounding(int pitch) {
        return held.containsValue(pitch) || midiHeld.contains(pitch) || mousePitch == pitch;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() == 0) {
            List<KeyRect> keys = keyboard();
            for (int i = keys.size() - 1; i >= 0; i--) {
                KeyRect k = keys.get(i);
                if (e.x() >= k.x && e.x() < k.x + k.w && e.y() >= k.y && e.y() < k.y + k.h) {
                    if (!playable(k.pitch)) return true;
                    mousePitch = k.pitch;
                    note(k.pitch, velocity, System.nanoTime());
                    return true;
                }
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (mousePitch >= 0) {
            note(mousePitch, 0, System.nanoTime());
            mousePitch = -1;
        }
        return super.mouseReleased(e);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Parchment.draw(g, x0, y0, W, H, 20);
        g.text(font, title, x0 + 20, y0 + 16, Parchment.INK_TITLE, false);
        Component status = pads ? tr("held_status_pads", velocity) : tr("held_status", KeyLayout.noteName(base), velocity,
            Component.translatable(KeyLayout.noteName(kind.low()) + "–" + KeyLayout.noteName(kind.high())));
        int statusX = x0 + 20 + font.width(title) + 12;
        g.text(font, status, statusX, y0 + 16, Parchment.INK, false);
        // 音源写在右上角；名字、状态太长挤不下时挪到底下提示行的右边，那里也挤不下就不写
        Component source = SampleMaps.sourceLabel(kind);
        Component hint = tr(pads ? "held_hint_pads" : "held_hint");
        if (source != null) {
            int sx = x0 + W - 20 - font.width(source);
            if (sx >= statusX + font.width(status) + 8) g.text(font, source, sx, y0 + 16, Parchment.INK_HINT, false);
            else if (sx >= x0 + 20 + font.width(hint) + 8) g.text(font, source, sx, y0 + H - 23, Parchment.INK_HINT, false);
        }
        for (KeyRect k : keyboard()) {
            boolean on = sounding(k.pitch);
            if (pads) {
                g.fill(k.x, k.y, k.x + k.w, k.y + k.h, on ? 0xFFF4D27A : 0xFFF6F0E2);
                g.outline(k.x, k.y, k.w, k.h, 0xFF8C7A60);
                Component name = Component.translatable("screen.starbloom_orchestra.instrument.pad." + kind.id() + "." + k.pitch);
                g.text(font, name, k.x + (k.w - font.width(name)) / 2, k.y + 12, Parchment.INK_TITLE, false);
                g.text(font, Component.literal(k.label), k.x + (k.w - font.width(k.label)) / 2, k.y + k.h - 12, Parchment.INK_HINT, false);
                continue;
            }
            boolean out = k.pitch < kind.low() || k.pitch > kind.high();
            int fill = k.black ? (on ? Parchment.GOLD : out ? 0xFF5A4A40 : 0xFF241812)
                : (on ? 0xFFF4D27A : out ? 0xFFD8CCB4 : 0xFFF6F0E2);
            g.fill(k.x, k.y, k.x + k.w, k.y + k.h, fill);
            g.outline(k.x, k.y, k.w, k.h, k.black ? 0xFF120A06 : 0xFF8C7A60);
            int lx = k.x + (k.w - font.width(k.label)) / 2;
            g.text(font, Component.literal(k.label), lx, k.y + k.h - 10, k.black ? 0xFFE8DCC0 : Parchment.INK_HINT, false);
        }
        g.text(font, hint, x0 + 20, y0 + H - 23, Parchment.INK_HINT, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }
}
