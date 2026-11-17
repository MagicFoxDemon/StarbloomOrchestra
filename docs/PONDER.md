# 思索：按住 W 看乐器怎么摆

鼠标停在物品上按住 **W**，打开「思索」（[Ponder](https://github.com/Creators-of-Create/Ponder)，机械动力的作者拆出来的图文讲解库）：
一块小场景里的方块一件件落下来，配着文字讲琴凳归哪件乐器、乐手站在哪、管风琴怎么搭、指挥台怎么用。也可以在思索界面里点标签，看一类物品。

Ponder 和它要的 catnip（都是 MIT）打包在本模组的 jar 里（jar-in-jar，同机械动力的做法），玩家不用另装；整合包里别的模组也带着时，
NeoForge 只加载版本最高的那一份。官方发布的版本在 26.1 上用不了，带的是从上游源码编译的（第 4 节）。Ponder 的 POM 里写了 Flywheel，但它的 `mods.toml` 不要求、本模组也不用，所以不传递依赖。

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
| `client/ponder/PonderViews` | 客户端检查 `ponder_scenes_ui`（DEV_ONLY）：Ponder 自己生成的文本键（`provideLang`）在 en_us、zh_cn 里都有、英文和场景代码一字不差、没有多出来的旧键；再按英文、中文各把七个场景打开一遍，在每个关键帧截图 |
| `gametest/CommandTreeGameTests` | 世界测试 `command_tree_syncs`：服务端的命令树能发给玩家（第 4 节） |
| `tools/ponder_build.py` | 从上游固定的提交编译 Ponder 和 catnip，发布到 `libs/ponder-maven`（第 4 节） |

## 3. 改场景、加场景

1. 布置写进 `PonderLayouts`（加进 `ALL`），讲的事写进 `PonderLayoutGameTests.check`；脚本写进 `OrchestraScenes`，在 `OrchestraPonderPlugin` 里挂到物品上。
2. 生成蓝图，同时核对：

   ```text
   ./gradlew runGameTestServer -PorchestraTests=starbloom_orchestra:ponder_layouts -PponderExport
   ```

3. 文字：英文写在脚本里（`.text("…")`，`scene.title` 是标题），键是 `starbloom_orchestra.ponder.<场景>.header`、`.text_<序号>`，
   **序号按 showText 的先后从 1 数**——中间插一句，后面的序号都要往后挪，zh_cn.json 里也一样。英文由客户端检查写进 en_us.json，中文手写：

   ```text
   ./gradlew runClient -PldTest=ponder_scenes_ui -PponderLangExport -PldTestHeadless -PldTestWindow=1600x900
   ```

   不带 `-PponderLangExport` 就只核对。新场景要在 `PonderViews.SHOTS` 里加一行（打开它的物品、关键帧数）。
4. 看截图（`build/ldlib2-uitest/`）：文字框有没有挡住要看的方块、描边对不对、中文有没有截断。

写法：

- 一句只讲一件事，不讲显而易见的（「右键坐下」可以，「琴凳是用来坐的」不要）；数值和玩法以 [ORCHESTRA.md](ORCHESTRA.md)、[PIPE_ORGAN.md](PIPE_ORGAN.md) 为准，
  改了玩法要回来改场景，世界测试会先发现对不上的那部分。
- 思索界面一开始的镜头从 x、z 都最小的那个角往里看（看得见北面和西面），所以乐器尽量让演奏的那一面朝北或朝西。
- 要讲「把方块换成另一种」时用 `scene.world().setBlock` / `modifyBlock`，世界测试里做同样的替换再核对（双人琴凳、单人琴凳、乐谱架上放乐器都是这样）。

## 4. 自己从上游编译的 Ponder

官方发布的 Ponder / catnip 1.0.52+mc26.1.2（2026-10-15，26.1 唯一的版本）在 26.1 上用不了：

- catnip 的 `/catnip config` 用了三种命令参数（`ModIdArgument`、`ConfigIdArgument`、`ConfigPathArgument`），注册它们的那段被注释掉了。
  没注册的参数发不出命令树，**玩家进任何世界都会以「Invalid player data」断开**；
- Ponder 在 `FMLClientSetupEvent` 才注册自己的客户端配置和画中画渲染器，这时 NeoForge 已经加载过客户端配置、渲染器也注册过了，
  按住 W 打开思索界面就抛「Config value 'editingMode' accessed too early」；
- 还有世界高度不对、虚拟世界的类没拷全等问题。

上游 mc26.1/dev 分支 2026-11-13、14 把这些都修了，但还没发布。所以本模组带的是用 `tools/ponder_build.py` 从上游固定的提交
（`ef65d6ed`，「Fix heights again」）编译的 **1.0.52.1+mc26.1.2**：源码没改，版本号比官方 1.0.52 高（整合包里别的模组带着 1.0.52 时
NeoForge 用这一份）、比以后的 1.0.53 低（官方新版一出就用官方的）；本模组的依赖写成 `[1.0.52.1+mc26.1.2,)`，坏的 1.0.52 不会被选上。

- 编译：`uv run --no-project python tools/ponder_build.py`（要 git、JDK 25、网络）→ `libs/ponder-maven/`（`libs/` 不进 git）。
  脚本核对 jar 里的版本号和清单里记的上游提交（`Git-Hash`），打出 SHA-256。CI 先跑它（脚本不变就用缓存）。
- 官方发了修好 26.1 的版本以后：`gradle.properties` 的 `ponder_version` 换成官方版本，`neoforge.mods.toml` 的版本下限跟着改，
  删掉 `build.gradle` 里的 `libs/ponder-maven`、`tools/ponder_build.py` 和 CI 里那一步。
- 世界测试 `command_tree_syncs` 核对服务端命令树里每个参数的类型都注册过（用官方 1.0.52 时它列出上面三个参数）。

还有一个坑上游没修：26.1 的物品数据组件进了世界才绑定，Ponder 却在模组加载完时就注册标签，那时 `TagBuilder.item(...)` 建 ItemStack 会崩。
所以启动时先注册不带图标的标签，进世界时（`ClientPlayerNetworkEvent.LoggingIn`）重新载入一次 Ponder，补上图标（`OrchestraPonder`）。
场景本身没有这个问题：只在打开思索界面时才编译，那时已经在世界里。

## 5. 没验证的

- 和机械动力一起装（两边都带 Ponder，按版本取高的那一份；机械动力自己的场景照常）；光影包下的思索界面。
- 场景里不画乐手：坐着、站着的人只用文字和位置指出来。
