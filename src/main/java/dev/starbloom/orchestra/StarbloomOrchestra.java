package dev.starbloom.orchestra;

import dev.starbloom.orchestra.music.OrchestraNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * 星辰花乐团：方块乐器、手持乐器、打孔纸带与 MIDI、乐团与指挥（docs/ORCHESTRA.md）。独立的模组。
 */
@Mod(StarbloomOrchestra.ID)
public final class StarbloomOrchestra {
    public static final String ID = "starbloom_orchestra";

    public StarbloomOrchestra() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        OrchestraNetwork.register();
        dev.starbloom.orchestra.music.Instruments.register(bus);
        dev.starbloom.orchestra.music.orchestra.Orchestra.register(bus);
        dev.starbloom.orchestra.music.Benches.register(bus);
        OrchestraRegistries.BLOCKS.register(bus);
        OrchestraRegistries.ITEMS.register(bus);
        OrchestraTabs.register(bus);
        dev.starbloom.orchestra.gametest.InstrumentGameTests.register(bus);
        dev.starbloom.orchestra.gametest.OrchestraGameTests.register(bus);
        dev.starbloom.orchestra.gametest.BenchGameTests.register(bus);
        dev.starbloom.orchestra.gametest.OrganGameTests.register(bus);
        dev.starbloom.orchestra.gametest.CreativeTabGameTests.register(bus);
        dev.starbloom.orchestra.gametest.PonderLayoutGameTests.register(bus);
        dev.starbloom.orchestra.gametest.CommandTreeGameTests.register(bus);
        // 思索（按住 W，Ponder 库打包在本模组的 jar 里）：场景只在客户端注册
        if (FMLEnvironment.dist.isClient() && ModList.get().isLoaded("ponder")) {
            dev.starbloom.orchestra.client.ponder.OrchestraPonder.init();
        }
        // 车万女仆（可选）：女仆的演奏姿势只在装了女仆模组的客户端注册。那个类只在有女仆模组的 jar 时才编译进来，
        // 所以按名字找；它要接的 DefaultGeckoAnimationEvent 很早就发，女仆模组的文档要求在模组初始化时手动注册
        if (FMLEnvironment.dist.isClient() && ModList.get().isLoaded("touhou_little_maid")) {
            try {
                Class.forName("dev.starbloom.orchestra.client.music.MaidMusicians").getMethod("init", IEventBus.class).invoke(null, bus);
            } catch (ReflectiveOperationException e) {
                com.mojang.logging.LogUtils.getLogger().warn("Touhou Little Maid musician poses not available", e);
            }
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(ID, path);
    }
}
