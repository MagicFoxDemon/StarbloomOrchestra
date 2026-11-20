"""世界测试用的结构模板（gametest/GameTestFunctions）：一块全是空气的立方体，存成 1.20.1 的结构 NBT。

26.1 的世界测试用原版 1×1×1 的空结构加 padding 格空出来的地方；1.20.1 没有 padding，测试框架在结构外面围一圈屏障，
所以按 26.1 里的 padding 做边长 2×padding+1 的空气块（测试原点在西北下角）。只用标准库，gzip 的时间戳固定为 0，
每次生成的文件逐字节一致。

    uv run --no-project python tools/gametest_structures.py
"""
import gzip
import struct
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/data/starbloom_orchestra/structures/gametest"
DATA_VERSION = 3465  # 1.20.1
# 测试类用到的 padding：命令树 0；创造物品栏、旧 ID 2；琴凳、乐器、乐团 6；管风琴 12；思索布置 14
PADDINGS = (0, 2, 6, 12, 14)

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def name(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def int_list(values):
    return struct.pack(">bi", TAG_INT, len(values)) + b"".join(struct.pack(">i", v) for v in values)


def compound(entries):
    """entries：(类型, 名字, 已编码的值) 的列表，按给的顺序写。"""
    return b"".join(struct.pack(">b", t) + name(n) + v for t, n, v in entries) + struct.pack(">b", TAG_END)


def compound_list(items):
    return struct.pack(">bi", TAG_COMPOUND if items else TAG_END, len(items)) + b"".join(items)


def structure(edge):
    blocks = [
        compound([(TAG_LIST, "pos", int_list([x, y, z])), (TAG_INT, "state", struct.pack(">i", 0))])
        for y in range(edge) for x in range(edge) for z in range(edge)
    ]
    palette = [compound([(TAG_STRING, "Name", name("minecraft:air"))])]
    root = compound([
        (TAG_LIST, "size", int_list([edge, edge, edge])),
        (TAG_LIST, "entities", compound_list([])),
        (TAG_LIST, "blocks", compound_list(blocks)),
        (TAG_LIST, "palette", compound_list(palette)),
        (TAG_INT, "DataVersion", struct.pack(">i", DATA_VERSION)),
    ])
    return struct.pack(">b", TAG_COMPOUND) + name("") + root


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for padding in PADDINGS:
        edge = 2 * padding + 1
        path = OUT / f"empty_{edge}.nbt"
        path.write_bytes(gzip.compress(structure(edge), compresslevel=9, mtime=0))
        print(path.relative_to(OUT.parents[6]), edge, "x", edge, "x", edge)


if __name__ == "__main__":
    main()
