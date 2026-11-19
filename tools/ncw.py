"""读 Native Instruments 的 NCW（Kontakt 的无损压缩采样）：篠笛的采样库只有这种格式（tools/instrument_sources.py 用）。

    import ncw
    rate, data = ncw.read(path)   # data: (声道, 采样点) 的 float64，满幅 ±1

格式（逐字节核对过 OtowabiShinobue 的 17 个文件：块与块之间连续、音高对得上文件名）：
- 文件头 120 字节：0–7 魔数，8 声道数（u16）、10 位深（u16）、12 采样率（u32）、16 采样点数（u32）、
  20 块偏移表的位置、24 块数据的起点、28 块数据的长度（都是 u32，小端）。
- 块偏移表：每块一个 u32，相对块数据起点；最后一个是结尾。
- 每块每个声道 512 个采样点：16 字节块头（魔数 0x3E9A0C16、i32 基值、i16 位数、u16 标志、4 字节 0），后面是按位紧排的整数：
  位数 > 0 是差分（第一个点 = 基值，之后每个点 = 前一个 + 差），< 0 是绝对值（|位数| 位），= 0 是原始位深的绝对值；
  标志第 0 位表示两个声道存的是中 / 侧（L = M + S，R = M − S）。
"""
import struct

import numpy as np

BLOCK_MAGIC = 0x3E9A0C16
BLOCK_SAMPLES = 512


def _unpack(raw: bytes, bits: int, count: int) -> np.ndarray:
    """count 个 bits 位宽、小端、按位紧排的有符号整数。"""
    arr = np.frombuffer(raw, dtype=np.uint8)
    b = np.unpackbits(arr, bitorder="little")[:bits * count].reshape(count, bits).astype(np.int64)
    vals = (b << np.arange(bits, dtype=np.int64)).sum(axis=1)
    return np.where(vals >= 1 << (bits - 1), vals - (1 << bits), vals)


def read(path) -> tuple[int, np.ndarray]:
    with open(path, "rb") as f:
        b = f.read()
    channels, depth, rate, frames, table, start, _size = struct.unpack_from("<HHIIIII", b, 8)
    count = (start - table) // 4
    offsets = struct.unpack_from(f"<{count}I", b, table)
    chans: list[list[np.ndarray]] = [[] for _ in range(channels)]
    mid_side = False
    for i in range(count - 1):
        o = start + offsets[i]
        for c in range(channels):
            magic, base, bits, flags, _ = struct.unpack_from("<IihHI", b, o)
            if magic != BLOCK_MAGIC:
                raise ValueError(f"{path}: block {i} has magic {magic:#x}")
            o += 16
            if bits > 0:
                n = bits * BLOCK_SAMPLES // 8
                d = _unpack(b[o:o + n], bits, BLOCK_SAMPLES)
                s = base + np.concatenate(([0], np.cumsum(d[:-1])))
            else:
                w = -bits if bits < 0 else depth
                n = w * BLOCK_SAMPLES // 8
                s = _unpack(b[o:o + n], w, BLOCK_SAMPLES)
            mid_side |= bool(flags & 1)
            chans[c].append(s)
            o += n
    data = np.array([np.concatenate(c)[:frames] for c in chans], dtype=np.float64) / float(1 << (depth - 1))
    if mid_side and channels == 2:
        m, s = data
        data = np.array([m + s, m - s])
    return rate, data
