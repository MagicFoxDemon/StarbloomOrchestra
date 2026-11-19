"""和乐器组：篠笛、神乐铃、尺八（手持，各有物品 / 演奏 / held 三份模型）和谱架上搁着它们的变体（docs/JAPANESE_INSTRUMENTS.md）。

和魔女之森的乐器不同，这两件按日本的样子画：不用大理石漆、胡桃木和星辰花，用烟熏竹、黑籐卷、朱漆、黄铜铃和五色布。
坐标约定（HeldRig 的支点和手位落在这些点上，改了就得一起改 Java）：
- 篠笛沿 x 横放，管轴 y=8、z=8，x 1.5–14.5；歌口（吹孔）中心 (3.4, 8, 8)，七个指孔在管顶 x 6.2–12.0；
- 神乐铃竖放，柄轴 x=8、z=8：柄 y 0.6–6.8（握点 (8, 3.6, 8)），铃架 y 7–15.6（下七、中五、上三），
  五色布从柄底垂下，末端 (8, −4.4, 8)（非惯用手捏着的地方）；
- 尺八竖放，管轴 x=8、z=8，根在下（y 0.3）、歌口在上（SHAKUHACHI_MOUTH = (8, 15.6, 8)），指孔在 −Z 面（四个）和 +Z 面（一个），
  两手 (8, 4.6, 10.6) / (8, 9.4, 10.6)（同双簧管的约定，手在管身后面）。
"""
import math

import instrument_kit as kit
from instrument_kit import cube
from instrument_models.winds import bands, bar, prism, mirror, for_scale, unfight

PREVIEW = [
    "item/shinobue", "item/kagura_suzu", "item/shakuhachi", "item/shinobue_playing", "item/kagura_suzu_playing",
    "block/hexen_music_stand_shinobue", "block/hexen_music_stand_kagura_suzu", "block/hexen_music_stand_shakuhachi",
]

# —— 材质（只加新名字，不改已有的）——
kit.PALETTE.update({
    "bamboo": kit.ramp("#3e2711", "#5c3c19", "#7d5524", "#9c6f33", "#b98b47"),        # 烟熏竹（煤竹）
    "urushi": kit.ramp("#3f0c08", "#62160e", "#862215", "#a7311d", "#c4492b"),        # 朱漆
    "silk_green": kit.ramp("#0d3322", "#154c34", "#1f6747", "#2c825b", "#3c9e71"),
    "silk_yellow": kit.ramp("#6b4e0a", "#8f6c12", "#b48c1d", "#d2ab34", "#e9c856"),
    "silk_red": kit.ramp("#520c0f", "#781418", "#9c1e23", "#ba2d2f", "#d44541"),
    "silk_white": kit.ramp("#a19c92", "#bdb8ae", "#d3cfc6", "#e3e0d9", "#f3f1ec"),
    "silk_purple": kit.ramp("#26102f", "#391946", "#4d235f", "#62307a", "#7a4196"),
})
kit.MATERIALS.update({
    "bamboo": ("bamboo", kit.fine_wood, 2.5),
    "urushi": ("urushi", kit.lacquer, 2.2),
    "silk_green": ("silk_green", kit.fabric, 2.2),
    "silk_yellow": ("silk_yellow", kit.fabric, 2.2),
    "silk_red": ("silk_red", kit.fabric, 2.2),
    "silk_white": ("silk_white", kit.fabric, 2.2),
    "silk_purple": ("silk_purple", kit.fabric, 2.2),
})
kit.BEVEL.update({"urushi": 0.8, "silk_green": 0.3, "silk_yellow": 0.3, "silk_red": 0.3, "silk_white": 0.3, "silk_purple": 0.3})

SILK = ("silk_green", "silk_yellow", "silk_red", "silk_white", "silk_purple")   # 五色布：青、黄、赤、白、紫


# —— 篠笛 ——

S0, S1 = 1.5, 14.5
SHINOBUE_MOUTH = (3.4, 8, 8)
SHINOBUE_HOLES = (6.2, 7.1, 8.0, 8.9, 10.2, 11.1, 12.0)


def shinobue_elements(s):
    """煤竹一根（八边形），管口一头是朱漆的笛头，歌口和七个指孔在管顶；黑籐一圈圈卷在笛头后、两组指孔之间和管尾（画在贴图上）。"""
    f = lambda x: (x - S0) / (S1 - S0)
    rattan = [(f(x), "lacquer_black", 1.6) for x in (2.7, 3.0, 4.2, 4.5, 4.8, 9.55, 13.0, 13.35, 13.7, 14.05)]
    node = [(f(5.6), "bamboo", 3.6)]
    el = prism((8, 8), 0.55, S0, S1, "bamboo", s, axis="x", deco=bands("shinobue", rattan + node, along="u"), bevel=0,
               caps={"top": {"mat": "shadow", "tone": 1.6}, "bottom": None})
    # 笛头：朱漆，盖住管口那一头（篠笛的这一头是塞住的）
    el += prism((8, 8), 0.62, 1.1, 2.35, "urushi", s, axis="x", caps={"top": "urushi", "bottom": {"mat": "urushi", "tone": 3.0}})
    # 歌口与指孔：管顶上一点点高出的暗色小块（底面贴着管子，不画）
    mx = SHINOBUE_MOUTH[0]
    el.append(bar((mx - 0.3, 8.56, 7.75), (mx + 0.3, 8.62, 8.25), "shadow", s, drop=("down",)))
    for x in SHINOBUE_HOLES:
        el.append(bar((x - 0.2, 8.56, 7.8), (x + 0.2, 8.61, 8.2), "shadow", s, drop=("down",)))
    return kit.prune_hidden(el)


# —— 尺八 ——

SHAKUHACHI_MOUTH = (8, 15.6, 8)
P0, P1 = 2.2, 15.6


def shakuhachi_elements(s):
    """一尺八寸管：煤竹，下端是膨大的竹根，几道竹节和中间的接口画在贴图上；管口朝上开着，前沿镶一块牛角作歌口；
    正面四个指孔、背面一个。"""
    f = lambda y: (y - P0) / (P1 - P0)
    marks = [(f(y), "bamboo", 3.7) for y in (3.4, 6.3, 11.6, 14.2)] + [(f(8.9), "lacquer_black", 1.4), (f(9.05), "lacquer_black", 1.6)]
    el = prism((8, 8), 0.72, P0, P1, "bamboo", s, deco=bands("shakuhachi", marks), caps={"top": {"mat": "shadow", "tone": 1.6}, "bottom": None})
    # 竹根：粗一圈，底面是管子的出口
    root = bands("shakuhachi_root", [(0.35, "bamboo", 3.6), (0.7, "bamboo", 1.4)])
    el += prism((8, 8), 0.98, 0.3, P0 + 0.25, "bamboo", s, deco=root, caps={"top": "bamboo", "bottom": {"mat": "shadow", "tone": 1.4}}, seed=1)
    # 歌口的牛角：贴在管口前沿外面
    el.append(bar((7.62, 14.95, 7.1), (8.38, 15.58, 7.18), "ivory", s, tone=2.6))
    # 指孔：正面四个、背面一个（管身外面一点点高出的暗色小块）
    for y in (4.4, 5.6, 6.8, 8.0):
        el.append(bar((7.8, y - 0.22, 7.1), (8.2, y + 0.22, 7.18), "shadow", s))
    el.append(bar((7.8, 9.98, 8.82), (8.2, 10.42, 8.9), "shadow", s))
    return kit.prune_hidden(el)


# —— 神乐铃 ——

HANDLE = (0.6, 6.8)
TIERS = ((8.4, 2.9, 7), (11.2, 2.15, 5), (13.8, 1.4, 3))   # (铃心高度, 半径, 颗数)：下七、中五、上三
SUZU_GRIP = (8, 3.6, 8)
SUZU_TAIL = (8, -4.4, 8)


def bell(cx, cy, cz, ang, s, seed):
    """一颗铃：1.3 见方的黄铜块，朝外转过去；底面一道暗缝（铃口）。"""
    h = 0.65
    e = cube((cx - h, cy - h, cz - h), (cx + h, cy + h, cz + h), "brass", s, seed=seed,
             faces={"down": {"mat": "brass", "deco": kit.pixels([(0, 1), (1, 1)], "shadow", 0.6)}})
    e["rotation"] = kit.rot_euler(0, round(ang, 3), 0, (cx, cy, cz))
    return e


def ring(cy, r, n, s, seed):
    """挂铃的黄铜圈：n 边形的一圈细铜条（kit.wall），比铃心高 0.75。"""
    sides = max(n, 6)
    pts = [(8 + r * math.cos(2 * math.pi * k / sides), 8 + r * math.sin(2 * math.pi * k / sides)) for k in range(sides + 1)]
    return kit.wall(pts, cy + 0.72, cy + 0.92, 0.18, "brass", s, outside=-1, seed=seed)


def ribbons(s, length):
    """五色布：五条从柄底垂下的绸带，散成一把扇（宽 0.9、厚 0.08，彼此错开，不闪）。"""
    els = []
    top = HANDLE[0] + 0.05
    for i, mat in enumerate(SILK):
        a = (i - 2) * 0.42
        dx, dz = 0.55 * math.sin(a), 0.55 * math.cos(a) * (1 if i % 2 else -1)
        x, z = 8 + dx, 8 + dz * 0.5
        e = cube((x - 0.45, top - length - 0.12 * (i % 3), z - 0.04), (x + 0.45, top, z + 0.04), mat, s, grain="y", seed=i,
                 faces={"up": None})
        e["rotation"] = kit.rot_euler(round(math.degrees(a) * 0.35, 3), round(math.degrees(a) * 1.6, 3), round(-math.degrees(a) * 0.5, 3),
                                      (8, top, 8))
        els.append(e)
    return els


def suzu_elements(s, ribbon_len=5.0):
    el = []
    # 柄：朱漆八边形，两头黄铜箍
    el += prism((8, 8), 0.55, HANDLE[0], HANDLE[1], "urushi", s, caps={"top": None, "bottom": {"mat": "brass", "tone": 2.6}})
    el += prism((8, 8), 0.66, HANDLE[0] - 0.25, HANDLE[0] + 0.55, "brass", s, caps={"top": "brass", "bottom": "brass"}, seed=1)
    el += prism((8, 8), 0.66, HANDLE[1] - 0.5, HANDLE[1] + 0.3, "brass", s, caps={"top": "brass", "bottom": "brass"}, seed=2)
    # 铃架的芯：细黄铜杆，顶上一颗宝珠
    el += prism((8, 8), 0.26, HANDLE[1] + 0.2, 15.2, "brass", s, sides=4, caps={"top": None, "bottom": None}, seed=3)
    el.append(bar((7.55, 15.15, 7.55), (8.45, 16.0, 8.45), "gilt", s, tone=3.0))
    seed = 10
    for t, (cy, r, n) in enumerate(TIERS):
        el += ring(cy, r, n, s, seed=t)
        # 圈和芯之间的一根横撑（贴在圈下面，两头停在圈的内侧）
        el.append(bar((8 - r + 0.26, cy + 0.6, 7.9), (8 + r - 0.26, cy + 0.7, 8.1), "brass", s, tone=2.0, seed=t))
        for k in range(n):
            a = 2 * math.pi * (k + 0.5 * (t % 2)) / n
            cx, cz = 8 + r * math.cos(a), 8 + r * math.sin(a)
            el.append(bell(cx, cy, cz, -math.degrees(a), s, seed))
            seed += 1
    el += ribbons(s, ribbon_len)
    return kit.prune_hidden(el)


# —— 显示变换 ——

GUI_ROT = {"shinobue": (20, 200, -40), "kagura_suzu": (15, 200, 0), "shakuhachi": (20, 200, -40)}
FIXED_ROT = {"shinobue": (0, 180, -40), "kagura_suzu": (0, 180, 0), "shakuhachi": (0, 180, -40)}
FIRST_BASE = {"rotation": [0, 100, 15], "translation": [1, 2, 0], "scale": [0.5] * 3}
THIRD = {
    "shinobue": {"rotation": [0, 90, 55], "translation": [0, 2, 0.5], "scale": [0.55] * 3},
    "kagura_suzu": {"rotation": [0, 90, 0], "translation": [0, 3, 1], "scale": [0.5] * 3},
    "shakuhachi": {"rotation": [0, 90, -15], "translation": [0, 1.5, 1], "scale": [0.55] * 3},
}
FIRST_PLAYING = {
    "shinobue": {"rotation": [0, 170, 0], "translation": [-4, 6, -2], "scale": [0.55] * 3},
    "kagura_suzu": {"rotation": [0, 100, 10], "translation": [-2, 5, -1], "scale": [0.55] * 3},
    "shakuhachi": {"rotation": [70, 0, 0], "translation": [-7, 6, -2], "scale": [0.6] * 3},
}


def held_display(name, el, playing=False):
    gs, gt = kit.fit(el, GUI_ROT[name], 15.5, max_scale=1.6)
    rs, rt = kit.fit(el, (0, 0, 0), 8.0, depth=True)
    fs, ft = kit.fit(el, FIXED_ROT[name], 13.0)
    d = {"gui": {"rotation": list(GUI_ROT[name]), "translation": gt, "scale": [gs] * 3},
         "ground": {"rotation": [0, 0, 0], "translation": [rt[0], round(rt[1] + 2.0, 3), 0], "scale": [rs] * 3},
         "fixed": {"rotation": list(FIXED_ROT[name]), "translation": ft, "scale": [fs] * 3}}
    fp = FIRST_PLAYING[name] if playing else FIRST_BASE
    d["firstperson_righthand"] = fp
    d["firstperson_lefthand"] = mirror(fp)
    if not playing:
        d["thirdperson_righthand"] = THIRD[name]
        d["thirdperson_lefthand"] = mirror(THIRD[name])
    return d


# —— 谱架上的摆法 ——

def shinobue_on_stand(sh):
    """横搁在谱台下沿的托条上（同长笛）。"""
    return kit.shifted(shinobue_elements(sh), 0, 3.42, -1.3)


def suzu_on_stand(ss):
    """缩到 0.6 立在谱架左前方的小木座上，五色布短短地垂在座上（木座也画在神乐铃自己的贴图页上）。"""
    el = kit.shifted(kit.scaled(for_scale(lambda s: suzu_elements(s, ribbon_len=0.9), ss, 0.6), 0.6, (8, 0.6, 8)), -4.3, 0.15, -3.9)
    el = unfight(el)   # 铜圈一段段错开的 MIN_GAP 缩小后不够了，再推开一点
    base = prism((3.7, 4.1), 1.2, 0.0, 0.48, "walnut", ss, caps={"top": {"mat": "walnut_light", "tone": 2.4}, "bottom": None}, seed=4)
    return el + base


def shakuhachi_on_stand(sk):
    """竹根着地立在谱架右前方的小木座上（缩到 0.8），微微后仰靠着托条（同双簧管；木座画在尺八自己的贴图页上）。"""
    el = kit.shifted(kit.scaled(for_scale(shakuhachi_elements, sk, 0.8), 0.8, (8, 0.3, 8)), 4.2, 0.25, -3.6)
    el = kit.rigid(el, kit.euler(9, 0, 0), (12.2, 0.5, 4.4))
    base = prism((12.2, 4.4), 1.15, 0.0, 0.5, "walnut", sk, caps={"top": {"mat": "walnut_light", "tone": 2.4}, "bottom": None}, seed=3)
    return unfight(el) + base


def build(out):
    sh, ss, sk = out.sheet("shinobue"), out.sheet("kagura_suzu"), out.sheet("shakuhachi")
    inst = {"shinobue": (shinobue_elements(sh), sh), "kagura_suzu": (suzu_elements(ss), ss), "shakuhachi": (shakuhachi_elements(sk), sk)}
    for name, (el, sheet) in inst.items():
        for suffix, playing in (("", False), ("_playing", True), ("_held", False)):
            out.item_model(name + suffix, el, [sheet], "held", held_display(name, el, playing))
    # 谱架上的两件：两件乐器的贴图页每次都登记同样的面（单独跑这一组时排版也不变）；谱架本身的贴图页归管乐组，
    # 只在管乐组也在这次生成里时才写这两个谱架模型（谱架的面它都登记过，这里不会多出新的）
    on_stand = {"shinobue": (shinobue_on_stand(sh), sh), "kagura_suzu": (suzu_on_stand(ss), ss), "shakuhachi": (shakuhachi_on_stand(sk), sk)}
    if "hexen_music_stand" not in out.sheets:
        return
    from instrument_models import winds
    st = out.sheets["hexen_music_stand"]
    before = len(st.slots)
    stand = winds.music_stand_elements(st)
    assert len(st.slots) == before, "the music stand sheet belongs to the winds group"
    for holds, (el, sheet) in on_stand.items():
        out.block_model(f"hexen_music_stand_{holds}", stand + el, [st, sheet], "block")
