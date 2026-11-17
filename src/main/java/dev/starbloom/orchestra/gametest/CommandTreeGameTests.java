package dev.starbloom.orchestra.gametest;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import dev.starbloom.orchestra.StarbloomOrchestra;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 服务端的命令树能发给玩家：每个参数的类型都注册过。没注册的发不出命令树，玩家一进世界就断开——官方发布的 catnip 1.0.52 的
 * {@code /catnip config} 就是这样（docs/PONDER.md 第 4 节；本模组带的从上游编译的版本已经修好）。
 */
public final class CommandTreeGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, StarbloomOrchestra.ID);
    private static final String NAME = "command_tree_syncs";

    private CommandTreeGameTests() {}

    public static void register(IEventBus bus) {
        FUNCTIONS.register(NAME, () -> CommandTreeGameTests::commandTree);
        FUNCTIONS.register(bus);
        bus.addListener((RegisterGameTestsEvent e) -> {
            var env = e.registerEnvironment(StarbloomOrchestra.id("command_tree_default"), new TestEnvironmentDefinition.AllOf());
            e.registerTest(StarbloomOrchestra.id(NAME), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, StarbloomOrchestra.id(NAME)),
                new TestData<>(env, Identifier.withDefaultNamespace("empty"), 20, 0, true, Rotation.NONE, false, 1, 1, false, 0)));
        });
    }

    private static void commandTree(GameTestHelper h) {
        List<String> unregistered = new ArrayList<>();
        walk(h.getLevel().getServer().getCommands().getDispatcher().getRoot(), Collections.newSetFromMap(new IdentityHashMap<>()), "", unregistered);
        h.assertTrue(unregistered.isEmpty(), "command arguments without a registered type: " + unregistered);
        h.succeed();
    }

    private static void walk(CommandNode<?> node, Set<CommandNode<?>> seen, String path, List<String> unregistered) {
        if (!seen.add(node)) return;
        String here = path + " " + node.getName();
        if (node instanceof ArgumentCommandNode<?, ?> argument && !ArgumentTypeInfos.isClassRecognized(argument.getType().getClass())) {
            unregistered.add(here.strip() + " (" + argument.getType().getClass().getName() + ")");
        }
        for (CommandNode<?> child : node.getChildren()) walk(child, seen, here, unregistered);
        if (node.getRedirect() != null) walk(node.getRedirect(), seen, here, unregistered);
    }
}
