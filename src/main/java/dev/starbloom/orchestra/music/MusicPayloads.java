package dev.starbloom.orchestra.music;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

/**
 * 乐器的网络包。实时演奏传音符（每 tick 一批，带批内毫秒偏移）；纸带播放只传「从哪一刻开始放哪卷纸带」，
 * 音符由各客户端按本地时钟自己放。见 docs/HEXENWALD_INSTRUMENTS.md 第 5 节。
 */
public final class MusicPayloads {
    /** 一批里最多的事件数。 */
    public static final int MAX_BATCH = 64;
    /** 上传纸带时每块的字节数：serverbound 自定义包上限 32767 字节，留出包头。 */
    public static final int UPLOAD_CHUNK = 30_000;
    public static final int MAX_UPLOAD_CHUNKS = (MusicRoll.MAX_BYTES + UPLOAD_CHUNK - 1) / UPLOAD_CHUNK;
    /** pitch 取这个值表示延音踏板（velocity &gt; 0 踩下）。 */
    public static final int PEDAL = 128;

    private MusicPayloads() {}

    /** 一个实时事件：offsetMs 是相对本批开始的毫秒；velocity 0 表示松开。 */
    public record LiveNote(int offsetMs, int pitch, int velocity) {
        static void write(FriendlyByteBuf b, LiveNote n) {
            b.writeShort(n.offsetMs);
            b.writeByte(n.pitch);
            b.writeByte(n.velocity);
        }

        static LiveNote read(FriendlyByteBuf b) {
            return new LiveNote(Math.max(0, b.readShort()), b.readUnsignedByte(), b.readUnsignedByte());
        }
    }

    private static void writeNotes(FriendlyByteBuf b, List<LiveNote> notes) {
        b.writeVarInt(notes.size());
        for (LiveNote n : notes) LiveNote.write(b, n);
    }

    private static List<LiveNote> readNotes(FriendlyByteBuf b) {
        int n = b.readVarInt();
        if (n < 0 || n > MAX_BATCH) throw new IllegalArgumentException("too many notes: " + n);
        List<LiveNote> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(LiveNote.read(b));
        return list;
    }

    /** 演奏者 → 服务端：这一 tick 弹下和松开的音。 */
    public record LiveNotes(BlockPos pos, List<LiveNote> notes) {
        public void encode(FriendlyByteBuf b) {
            b.writeBlockPos(pos);
            writeNotes(b, notes);
        }

        public static LiveNotes decode(FriendlyByteBuf b) {
            return new LiveNotes(b.readBlockPos(), readNotes(b));
        }
    }

    /** 服务端 → 附近的听众：转发演奏者的一批音；allOff 表示演奏者离开，全部松开。 */
    public record LiveNotesOut(BlockPos pos, int performer, boolean allOff, List<LiveNote> notes) {
        public void encode(FriendlyByteBuf b) {
            b.writeBlockPos(pos);
            b.writeVarInt(performer);
            b.writeBoolean(allOff);
            writeNotes(b, notes);
        }

        public static LiveNotesOut decode(FriendlyByteBuf b) {
            return new LiveNotesOut(b.readBlockPos(), b.readVarInt(), b.readBoolean(), readNotes(b));
        }
    }

    /**
     * 拿在手上的乐器 → 服务端：这一 tick 弹的音；allOff 表示收起乐器（关掉演奏条）。
     * kind 是 {@link InstrumentKind} 的序号，服务端核对玩家手上确实拿着这种乐器。
     */
    public record HeldNotes(int kind, boolean allOff, List<LiveNote> notes) {
        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(kind);
            b.writeBoolean(allOff);
            writeNotes(b, notes);
        }

        public static HeldNotes decode(FriendlyByteBuf b) {
            return new HeldNotes(b.readVarInt(), b.readBoolean(), readNotes(b));
        }
    }

    /** 服务端 → 附近的听众：某个玩家手上乐器弹的一批音（声音跟着这个实体走）。 */
    public record HeldNotesOut(int entity, int kind, boolean allOff, List<LiveNote> notes) {
        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(entity);
            b.writeVarInt(kind);
            b.writeBoolean(allOff);
            writeNotes(b, notes);
        }

        public static HeldNotesOut decode(FriendlyByteBuf b) {
            return new HeldNotesOut(b.readVarInt(), b.readVarInt(), b.readBoolean(), readNotes(b));
        }
    }

    /** 演奏界面上的操作。 */
    public record Control(BlockPos pos, int action, int arg) {
        public static final int CLAIM = 0, RELEASE = 1, PLAY = 2, PAUSE = 3, STOP = 4, EJECT = 5, PART_MASK = 6, SIGNAL_MODE = 7;
        /** 管风琴：arg 是音栓掩码、混响档位。 */
        public static final int STOPS = 8, REVERB = 9;

        public void encode(FriendlyByteBuf b) {
            b.writeBlockPos(pos);
            b.writeVarInt(action);
            b.writeVarInt(arg);
        }

        public static Control decode(FriendlyByteBuf b) {
            return new Control(b.readBlockPos(), b.readVarInt(), b.readVarInt());
        }
    }

    /**
     * 服务端 → 追踪这个区块的玩家：纸带播放状态。roll 为空表示停止；positionMs 是此刻的播放位置。
     * 开始、暂停、停止时各发一次，播放中每 5 秒发一次心跳。
     */
    public record Playback(BlockPos pos, @Nullable UUID roll, long hash, int positionMs, boolean playing, int partMask) {
        public void encode(FriendlyByteBuf b) {
            b.writeBlockPos(pos);
            b.writeBoolean(roll != null);
            if (roll != null) b.writeUUID(roll);
            b.writeLong(hash);
            b.writeVarInt(positionMs);
            b.writeBoolean(playing);
            b.writeVarInt(partMask);
        }

        public static Playback decode(FriendlyByteBuf b) {
            return new Playback(b.readBlockPos(), b.readBoolean() ? b.readUUID() : null, b.readLong(), b.readVarInt(),
                b.readBoolean(), b.readVarInt());
        }
    }

    /** 客户端 → 服务端：要一卷纸带的内容。 */
    public record RollRequest(UUID id) {
        public void encode(FriendlyByteBuf b) {
            b.writeUUID(id);
        }

        public static RollRequest decode(FriendlyByteBuf b) {
            return new RollRequest(b.readUUID());
        }
    }

    /** 服务端 → 客户端：纸带内容（找不到时为空数组）。 */
    public record RollData(UUID id, byte[] data) {
        public void encode(FriendlyByteBuf b) {
            b.writeUUID(id);
            b.writeByteArray(data);
        }

        public static RollData decode(FriendlyByteBuf b) {
            return new RollData(b.readUUID(), b.readByteArray(MusicRoll.MAX_BYTES));
        }
    }

    /**
     * 客户端 → 服务端：上传一卷纸带（录制、打孔编辑器、MIDI 导入）的一块。全部到齐后服务端重新校验。
     * purpose：{@link #WRITE} 刻进乐器里的纸带，{@link #TEMPORARY} 只作临时纸带直接播放。
     */
    public record Upload(int uploadId, int index, int total, int purpose, BlockPos pos, byte[] chunk) {
        public static final int WRITE = 0, TEMPORARY = 1;

        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(uploadId);
            b.writeVarInt(index);
            b.writeVarInt(total);
            b.writeVarInt(purpose);
            b.writeBlockPos(pos);
            b.writeByteArray(chunk);
        }

        public static Upload decode(FriendlyByteBuf b) {
            return new Upload(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBlockPos(),
                b.readByteArray(UPLOAD_CHUNK));
        }
    }
}
