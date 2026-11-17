"""乐器界面的羊皮纸背景（client/music/Parchment）：像素画，原生 200×112，放大 8 倍存成 1600×896。

胡桃木木框（一条条木板、木纹、内侧一道阴影）、四角黄铜包角（L 形，中间一朵小星辰花）、纸面奶油色带一点斑驳、
靠框的一圈纸边微焦。九宫格的角是原生 16 像素（放大后 128），和 Parchment.SRC_X / SRC_Y 对应。

    uv run --no-project --with pillow --with numpy python tools/parchment_art.py
"""
from pathlib import Path

import numpy as np
from PIL import Image

W, H, SCALE, CORNER = 200, 112, 8, 16
FRAME = 7
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/starbloom_orchestra/textures/gui/parchment.png"

PAPER = np.array([240, 222, 176])
PAPER_DARK = np.array([234, 213, 164])
PAPER_LIGHT = np.array([245, 230, 192])
SCORCH = [np.array(c) for c in ((116, 66, 34), (164, 104, 58), (204, 156, 102))]
WOOD = [np.array(c) for c in ((58, 30, 18), (84, 44, 24), (106, 58, 32), (126, 72, 40))]
SHADOW = np.array([44, 22, 14])
BRASS = [np.array(c) for c in ((118, 82, 22), (176, 128, 38), (222, 172, 64), (250, 222, 132))]


def smooth_noise(rng, h, w, cell):
    """格子上的随机数双线性插值：低频的斑驳。"""
    gh, gw = h // cell + 2, w // cell + 2
    g = rng.random((gh, gw))
    y = np.arange(h) / cell
    x = np.arange(w) / cell
    y0, x0 = y.astype(int), x.astype(int)
    fy, fx = (y - y0)[:, None], (x - x0)[None, :]
    a = g[y0][:, x0]
    b = g[y0][:, x0 + 1]
    c = g[y0 + 1][:, x0]
    d = g[y0 + 1][:, x0 + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def main():
    rng = np.random.default_rng(1746)
    img = np.zeros((H, W, 3))

    # 纸面：两层斑驳 + 稀疏的浅色纤维点
    n = 0.6 * smooth_noise(rng, H, W, 14) + 0.4 * smooth_noise(rng, H, W, 5)
    img[:] = PAPER
    img[n < 0.3] = PAPER_DARK
    img[n > 0.8] = PAPER_LIGHT
    specks = rng.random((H, W)) < 0.012
    img[specks] = PAPER_LIGHT

    # 纸边微焦：离木框越近越深，边缘起伏不齐
    yy, xx = np.mgrid[0:H, 0:W]
    edge = np.minimum.reduce([xx - FRAME, W - 1 - FRAME - xx, yy - FRAME, H - 1 - FRAME - yy]).astype(float)
    wobble = 2.6 * smooth_noise(rng, H, W, 4) + 1.0 * rng.random((H, W))
    depth = edge - wobble
    for level, color in zip((1.2, 2.2, 3.4), SCORCH):
        img[(depth < level) & (edge >= 0)] = color

    # 木框：横边按列分木板、竖边按行分，每块木板一个底色、横向木纹
    frame = (xx < FRAME) | (xx >= W - FRAME) | (yy < FRAME) | (yy >= H - FRAME)
    grain = rng.random((H, W))
    plank_len = 23
    for y in range(H):
        for x in range(W):
            if not frame[y, x]:
                continue
            horizontal = (y < FRAME or y >= H - FRAME) and FRAME <= x < W - FRAME
            pos = x if horizontal else y
            plank = pos // plank_len
            base = 1 + (plank * 7 + (0 if horizontal else 3)) % 3
            along = (y if horizontal else x) % FRAME
            tone = base
            if grain[y if horizontal else 0, x if not horizontal else 0] < 0.0:
                tone = base
            if (along + plank) % 4 == 0 and grain[y, x] < 0.55:
                tone = max(0, base - 1)
            if pos % plank_len == 0:
                tone = 0                      # 木板之间的缝
            img[y, x] = WOOD[tone]
    # 框外沿一圈最暗，框内沿一道阴影压在纸上
    img[[0, H - 1], :] = WOOD[0]
    img[:, [0, W - 1]] = WOOD[0]
    inner = (edge == 0)
    img[inner] = SHADOW

    # 四角黄铜包角：L 形两臂 + 中间一朵四瓣小星辰花和铆钉
    corner = np.zeros((CORNER, CORNER), dtype=int)          # 0 不画 1 暗边 2 主色 3 亮 4 高光
    arm = 10
    for y in range(CORNER):
        for x in range(CORNER):
            if (y < 5 and x < arm + 3) or (x < 5 and y < arm + 3):
                corner[y, x] = 2
    for y in range(CORNER):
        for x in range(CORNER):
            if corner[y, x] == 2:
                neighbours = [corner[y + dy, x + dx] if 0 <= y + dy < CORNER and 0 <= x + dx < CORNER else 0
                              for dy, dx in ((1, 0), (0, 1))]
                if 0 in neighbours:
                    corner[y, x] = 1
    for y, x in ((1, 1), (1, 2), (2, 1), (1, 3), (3, 1)):
        corner[y, x] = 4
    for y in range(1, 4):
        for x in range(4, arm + 1):
            if corner[y, x] == 2 and y == 1:
                corner[y, x] = 3
    for x in range(1, 4):
        for y in range(4, arm + 1):
            if corner[y, x] == 2 and x == 1:
                corner[y, x] = 3
    # 星辰花：中心一点高光，上下左右四瓣
    for y, x, c in ((2, 2, 4), (1, 2, 3), (3, 2, 3), (2, 1, 3), (2, 3, 3)):
        corner[y, x] = c
    corner[2, 2] = 4
    for flip_y in (False, True):
        for flip_x in (False, True):
            c = corner[::-1] if flip_y else corner
            c = c[:, ::-1] if flip_x else c
            y0 = H - CORNER if flip_y else 0
            x0 = W - CORNER if flip_x else 0
            for y in range(CORNER):
                for x in range(CORNER):
                    if c[y, x]:
                        img[y0 + y, x0 + x] = BRASS[c[y, x] - 1]

    out = Image.fromarray(img.astype(np.uint8), "RGB").resize((W * SCALE, H * SCALE), Image.NEAREST)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    out.save(OUT, optimize=True)
    print(f"wrote {OUT} {out.size}")


if __name__ == "__main__":
    main()
