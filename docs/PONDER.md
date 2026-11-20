# 思索：按住 W 看乐器怎么摆

鼠标停在物品上按住 **W**，打开「思索」（[Ponder](https://github.com/Creators-of-Create/Ponder)，机械动力的作者拆出来的图文讲解库）：
一块小场景里的方块一件件落下来，配着文字讲琴凳归哪件乐器、乐手站在哪、管风琴怎么搭、指挥台怎么用。也可以在思索界面里点标签，看一类物品。

Ponder（MIT）打包在本模组的 jar 里（jar-in-jar，同机械动力的做法），玩家不用另装；整合包里别的模组也带着时，
由打包版本与整合包模组共同决定加载的 Ponder 版本。这条分支（Minecraft 1.20.1 / Forge）带的是官方发布的 Ponder-Forge-1.20.1 版本，它要的 catnip 就在 Ponder 的 jar 里（第 4 节）。
它要求的 Flywheel（MIT，1.20.1 Forge 为 `flywheel-forge-1.20.1:1.0.6`）也通过 `jarJar` 一起打包；本模组的代码不用它，只在运行时带上。

## 1. 场景

| 场景 | 按住 W 打开它的物品 | 讲什么 |
|---|---|---|
| `benches` 琴凳归哪件乐器 | 琴凳、双人琴凳、三角钢琴、大键琴、钢片琴、竖琴 | 琴键前、面朝琴键的琴凳归这件乐器，旁边或后面的不算；怎么坐、怎么请生物坐、让它起来；三角钢琴前的双人琴凳；竖琴的琴凳在琴后 |
| `music_stand` 乐谱架与手持乐器 | 乐谱架、琴凳、六件手持乐器 | 没关联的琴凳演奏手里的乐器；把乐器交给坐着的生物；对着琴凳的空乐谱架打开乐手的乐器、接纸带；架上放着乐器时架子就是乐器 |
| `posts_strings` 大提琴与低音提琴 | 大提琴、低音提琴 | 人在琴后（大提琴坐琴后的凳子，低音提琴站着）；空手右键上去、拴绳牵生物上去、潜行让它离开 |
| `posts_mallets` 钟琴、管钟与鼓 | 钟琴、管钟、小军鼓、吊镲、三角铁、大鼓 | 站在放置时你所在的那一面敲；大鼓侧放、人在侧面 |
| `timpani` 定音鼓与双人琴凳 | 定音鼓、双人琴凳、琴凳 | 每面鼓是乐团里的一件乐器；鼓后的双人琴凳一人打两面，单人琴凳打一面 |
| `pipe_organ` 搭一台管风琴 | 演奏台、风箱、三种音管 | 3 宽的演奏台和正中的琴凳；风箱挨着演奏台、彼此相连，可以拐弯、往上接；音管立在风箱上、叠成长管；号角管的朝向；音栓、可闻距离、上限 |
| `podium` 指挥台 | 指挥台、指挥棒 | 站在正中指挥；绑定指挥棒；点乐器、点琴凳加入；上限；召集附近；台上的左键、右键、滚轮 |

标签（思索界面首页）：**乐手的位置**（琴凳、乐谱架和有工位的乐器）、**管风琴**、**乐团与指挥**。

## 2. 代码在哪

| 文件 | 做什么 |
|---|---|
| `ponder/PonderLayouts`（两端共用） | 每个场景的布置：地板和场景里会出现的全部方块，坐标是蓝图里的（地板在 y = 0）。多格乐器只放主块，其余几格由 `setPlacedBy` 补上（`Placer.place`） |
| `gametest/PonderLayoutGameTests` | 世界测试 `ponder_layouts`：把每份布置在世界里像玩家那样放一遍，核对场景里讲的事是真的（琴凳关联了钢琴、放错的不关联、双人琴凳对着两面鼓、管风琴认出了 9 个风箱和每一格音管、乐手的位置……）；带 `-PponderExport` 时把放好的这一块存成 `assets/starbloom_orchestra/ponder/<场景>.nbt` |
| `client/ponder/OrchestraScenes` | 场景脚本：方块怎么出现、文字、描边、鼠标提示。坐标都用 `PonderLayouts` 里的常量 |
| `client/ponder/OrchestraPonderPlugin`、`OrchestraPonder` | 注册场景和标签；主类在客户端、装着 Ponder 时调 `OrchestraPonder.init()` |
| `gametest/CommandTreeGameTests` | 世界测试 `command_tree_syncs`：服务端的命令树能发给玩家（第 4 节） |

## 3. 改场景、加场景

1. 布置写进 `PonderLayouts`（加进 `ALL`），讲的事写进 `PonderLayoutGameTests.check`；脚本写进 `OrchestraScenes`，在 `OrchestraPonderPlugin` 里挂到物品上。
2. 生成蓝图，同时核对：

   ```text
   ./gradlew runGameTestServer -PorchestraTests=starbloom_orchestra:ponder_layouts -PponderExport
   ```

   蓝图是 1.20.1 的格式（`DataVersion` 3465）。从 `main`（26.1）或 `mc/1.21.1` 移植场景时不拷那边的 `.nbt`，在这条分支上用这条命令重新导出。

3. 文字：英文写在脚本里（`.text("…")`，`scene.title` 是标题），键是 `starbloom_orchestra.ponder.<场景>.header`、`.text_<序号>`，
   **序号按 showText 的先后从 1 数**——中间插一句，后面的序号都要往后挪，zh_cn.json 里也一样。本分支（`mc/1.20.1`）因 LDLib2 1.20.1 不包含原 `uitest` API，已排除 `PonderViews` 与 `-PldTest=ponder_scenes_ui -PponderLangExport`，英文 `en_us.json` 与中文 `zh_cn.json` 键需手动同步维护（`LangCoverageTest` 会检查语言键完整一致）。
4. 实机检查：增删场景或调整文本后，启动客户端查看思索界面里的文本框位置、描边和中英文显示是否正常。

写法：

- 一句只讲一件事，不讲显而易见的（「右键坐下」可以，「琴凳是用来坐的」不要）；数值和玩法以 [ORCHESTRA.md](ORCHESTRA.md)、[PIPE_ORGAN.md](PIPE_ORGAN.md) 为准，
  改了玩法要回来改场景，世界测试会先发现对不上的那部分。
- 思索界面一开始的镜头从 x、z 都最小的那个角往里看（看得见北面和西面），所以乐器尽量让演奏的那一面朝北或朝西。
- 要讲「把方块换成另一种」时用 `scene.world().setBlock` / `modifyBlock`，世界测试里做同样的替换再核对（双人琴凳、单人琴凳、乐谱架上放乐器都是这样）。

## 4. 用的 Ponder 版本

这条分支带的是官方发布的 **Ponder-Forge-1.20.1 1.0.92**（`maven.createmod.net`，`gradle.properties` 的 `ponder_version=1.0.92`），
`META-INF/mods.toml` 中对 `ponder` 的依赖下限写成 `[1.0.92,)`。catnip 在 Ponder 的 jar 里，不另带；Ponder 要求的 Flywheel 带的是
**flywheel-forge-1.20.1 1.0.6**（`flywheel_version=1.0.6`），通过 `build.gradle` 的 `jarJar` 一并打包。这里没有 `tools/ponder_build.py`、`libs/ponder-maven`，CI 也不编译 Ponder。

- `main`（26.1）上官方的 1.0.52 用不了（catnip 的命令参数没注册，玩家进世界就断开；Ponder 的客户端配置注册得太晚，按住 W 就报错），
  那边用 `tools/ponder_build.py` 从上游固定的提交编译，见 `main` 的这份文档。
- 世界测试 `command_tree_syncs` 照样核对服务端命令树里每个参数的类型都注册过（26.1 的官方 catnip 就坏在这里）。
- 标签的图标：1.20.1 上在客户端创建标签时可直接使用物品构建 `ItemStack` 作为图标（`OrchestraPonderPlugin`），不需要 `main`（26.1）那种进世界后重新载入的变通。
- 换 Ponder 版本：`gradle.properties` 的 `ponder_version`（它要的 Flywheel 版本变了时连 `flywheel_version`）和 `src/main/resources/META-INF/mods.toml`
  的版本下限一起改。

## 5. 没验证的

- 和机械动力一起装（两边都带 Ponder，按版本取高的那一份；机械动力自己的场景照常）；光影包下的思索界面。
- 一起打包的 Flywheel 和 Sodium（Embeddium）、光影包同装。
- 场景里不画乐手：坐着、站着的人只用文字和位置指出来。
