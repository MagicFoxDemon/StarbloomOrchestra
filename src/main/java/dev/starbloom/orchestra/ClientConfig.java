package dev.starbloom.orchestra;

import net.minecraftforge.common.ForgeConfigSpec;

/** 只影响本机的设置，文件是 config/starbloom_orchestra-client.toml。 */
public final class ClientConfig {
    private ClientConfig() {}

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue INSTRUMENT_VOICES = BUILDER
            .comment("Notes one instrument may sound at once. When full, the oldest released note is cut first.")
            .defineInRange("instrumentVoices", 32, 4, 64);

    public static final ForgeConfigSpec.IntValue INSTRUMENT_TOTAL_VOICES = BUILDER
            .comment("Notes all instruments together may sound at once; the game has about 247 sound channels for everything.")
            .defineInRange("instrumentTotalVoices", 96, 8, 160);

    public static final ForgeConfigSpec.ConfigValue<String> INSTRUMENT_KEYS = BUILDER
            .comment("Keys of the play screen, low to high, one semitone each, starting at C. Letters, digits and , . / ; ' [ ] - = are allowed.",
                    "Default: two piano-style rows, Z S X D C V G B H N J M (lower octave) then Q 2 W 3 E R 5 T 6 Y 7 U I 9 O 0 P.")
            .define("instrumentKeys", "ZSXDCVGBHNJMQ2W3ER5T6Y7UI9O0P");

    public static final ForgeConfigSpec.ConfigValue<String> MIDI_DEVICE = BUILDER
            .comment("Name of the MIDI keyboard to connect to when an instrument screen opens. Empty: none.",
                    "Pick it in game: instrument screen, Play page, MIDI keyboard.")
            .define("midiDevice", "");

    public static final ForgeConfigSpec.IntValue MIDI_CHANNEL = BUILDER
            .comment("MIDI channel to listen to: 0 = all channels, 1-16 = only that one (useful for split keyboards).")
            .defineInRange("midiChannel", 0, 0, 16);

    public static final ForgeConfigSpec.IntValue MIDI_TRANSPOSE = BUILDER
            .comment("Semitones added to every note from the MIDI keyboard.")
            .defineInRange("midiTranspose", 0, -36, 36);

    public static final ForgeConfigSpec.IntValue MIDI_VELOCITY_CURVE = BUILDER
            .comment("How the MIDI keyboard's key velocity maps to loudness: 0 = light (soft touches still sound), 1 = normal, 2 = heavy.")
            .defineInRange("midiVelocityCurve", 1, 0, 2);

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
