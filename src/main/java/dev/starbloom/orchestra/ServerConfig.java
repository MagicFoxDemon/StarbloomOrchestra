package dev.starbloom.orchestra;

import net.neoforged.neoforge.common.ModConfigSpec;

/** 服务端（每个存档）的设置，文件是存档里的 serverconfig/starbloom_orchestra-server.toml。 */
public final class ServerConfig {
    private ServerConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("Instruments: punched music rolls written by players (recorded, punched in the editor or imported from MIDI)").push("instruments");
    }

    public static final ModConfigSpec.BooleanValue INSTRUMENTS_ALLOW_UPLOAD = BUILDER
            .comment("Whether players may write music rolls. Off: only operators can record, punch or import rolls; playing existing rolls still works.",
                    "The server never reads MIDI files: clients convert them and upload the finished roll, which is checked again here.")
            .define("allowRollUpload", true);

    public static final ModConfigSpec.IntValue INSTRUMENTS_UPLOADS_PER_MINUTE = BUILDER
            .comment("How many rolls one player may write per minute (including temporary rolls played straight from a MIDI file).")
            .defineInRange("uploadsPerMinute", 4, 1, 60);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
