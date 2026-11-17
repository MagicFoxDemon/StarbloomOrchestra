package dev.starbloom.orchestra;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * 星辰花乐团：方块乐器、手持乐器、打孔纸带与 MIDI、乐团与指挥（docs/ORCHESTRA.md）。独立的模组，不依赖 Logos；
 * Logos 的附属「炼金釜」依赖它，用附属里的精灵、神狐、灵猫当乐手（附属的 NpcMusician）。
 *
 * <p>这些乐器原先在附属里（ID {@code logos_dimension:*}），老存档里的方块、物品、实体由 {@link LegacyIds} 换到本模组的 ID。
 */
@Mod(StarbloomOrchestra.ID)
public final class StarbloomOrchestra {
    public static final String ID = "starbloom_orchestra";

    public StarbloomOrchestra(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        dev.starbloom.orchestra.music.Instruments.register(bus);
        dev.starbloom.orchestra.music.orchestra.Orchestra.register(bus);
        dev.starbloom.orchestra.music.Benches.register(bus);
        OrchestraRegistries.BLOCKS.register(bus);
        OrchestraRegistries.ITEMS.register(bus);
        OrchestraTabs.register(bus);
        LegacyIds.register(bus);
        dev.starbloom.orchestra.gametest.InstrumentGameTests.register(bus);
        dev.starbloom.orchestra.gametest.OrchestraGameTests.register(bus);
        dev.starbloom.orchestra.gametest.BenchGameTests.register(bus);
        dev.starbloom.orchestra.gametest.OrganGameTests.register(bus);
        dev.starbloom.orchestra.gametest.CreativeTabGameTests.register(bus);
        dev.starbloom.orchestra.gametest.LegacyIdGameTests.register(bus);
        // 车万女仆（可选）：女仆的演奏姿势只在装了女仆模组的客户端注册。那个类只在有女仆模组的 jar 时才编译进来，
        // 所以按名字找；它要接的 DefaultGeckoAnimationEvent 很早就发，女仆模组的文档要求在模组初始化时手动注册
        if (FMLEnvironment.getDist().isClient() && ModList.get().isLoaded("touhou_little_maid")) {
            try {
                Class.forName("dev.starbloom.orchestra.client.music.MaidMusicians").getMethod("init", IEventBus.class).invoke(null, bus);
            } catch (ReflectiveOperationException e) {
                com.mojang.logging.LogUtils.getLogger().warn("Touhou Little Maid musician poses not available", e);
            }
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ID, path);
    }
}
