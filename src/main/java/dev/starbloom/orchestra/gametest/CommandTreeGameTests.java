package dev.starbloom.orchestra.gametest;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 服务端的命令树能发给玩家：每个参数的类型都注册过。没注册的发不出命令树，玩家一进世界就断开——26.1 上官方发布的 catnip 1.0.52 的
 * {@code /catnip config} 就是这样（docs/PONDER.md 第 4 节）。1.21.1 带的是官方发布的 Ponder（catnip 在它的 jar 里），照样核对。
 */
public final class CommandTreeGameTests {
    private static final String NAME = "command_tree_syncs";

    private CommandTreeGameTests() {}

    public static void register(IEventBus bus) {
        GameTestFunctions.register(bus, CommandTreeGameTests.class);
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return GameTestFunctions.batch("command_tree_default", 0, 20, 0, Map.of(NAME, CommandTreeGameTests::commandTree));
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
