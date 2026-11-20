# Starbloom Orchestra（星辰花乐团）

先阅读 README.md、VERIFICATION.md 和 [docs/ORCHESTRA.md](docs/ORCHESTRA.md)。本工程是一个独立的 Forge 模组（mod ID
`starbloom_orchestra`），不依赖 Logos；Logos 的附属「炼金釜」（`logos_dimension`）目前在 26.1 上，不移植到 1.20.1。

**这条分支 `mc/1.20.1` 是 Minecraft 1.20.1 / Forge 47.4.0 的一线**（Java 17，Parchment 2023.09.03 映射，通过
`net.neoforged.moddev.legacyforge` 2.0.148 构建）；`mc/1.21.1` 是 1.21.1 / NeoForge 一线，`main` 是 26.1.2 一线。各条线各自发布，不互相合并：

- 只关 1.20.1 的改动从 `mc/1.20.1` 开分支，PR 发到 `mc/1.20.1`。
- `main` 或 `mc/1.21.1` 上的改动要带过来时，从 `mc/1.20.1` 开分支、单独发 PR 到 `mc/1.20.1`（一个 PR 移植一个主题），按改动的意思在 1.20.1
  Forge 47.4.0 的接口上重写，不要把其他线整个合并进来；生成的文件（模型、蓝图、结构模板）在这条分支上重新生成，不直接拷高版本的二进制/JSON。两边都该有的修正，
  对应分支也发一份。
- 移植时要换写法的地方（相比 26.1 / 1.21.1）：
  - 渲染：没有渲染状态。演奏姿势按实体算：`RenderLivingEvent.Pre` 里记下这一帧的 `MusicianPoses.Drawing`，模型 setupAnim 之后
    由 `MusicianModelMixin` 摆；手里原本的东西由 `MusicianHandItemMixin` 不画；拿在手上演奏的乐器、琴弓、琴槌是单独加载的模型（`HeldModel`）；
    指挥棒滑轮切声部与转向拦截另走 `BatonTurnMixin`。
  - 模型：没有 `assets/<命名空间>/items/` 物品模型定义；手持乐器演奏时靠物品属性 `starbloom_orchestra:playing` 和 `overrides` 换模型；
    1.20.1 原版读不了的多轴元素转动走本模组的几何加载器 `starbloom_orchestra:rotated_cuboids`，`tools/instrument_art.py` 写文件时处理
    （[docs/INSTRUMENT_ART.md](docs/INSTRUMENT_ART.md) 第 9 节）。
  - 发往客户端的包：`OrchestraNetwork` 使用 Forge `SimpleChannel`；公共代码转调 `ClientPayloadHandlers`，客户端在 `MusicClient.ModBus` 里登记真正的处理器。
  - 世界测试：1.20.1 Forge GameTest（10 参数 `TestFunction`），经 `gametest/GameTestFunctions` 注册、挑选（`-PorchestraTests`）、写 JUnit 报告；结构模板由 `tools/gametest_structures.py` 生成（`DataVersion` 3465）。
  - 「思索」：官方发布的 `Ponder-Forge-1.20.1`（1.0.92，含 catnip）和 `flywheel-forge-1.20.1`（1.0.6）（没有 `tools/ponder_build.py`），见 [docs/PONDER.md](docs/PONDER.md) 第 4 节。

规矩（两条线一样）：

- 新改动在分支完成，一个 PR 只解决一个主题。提交标题用英文，说明用中文。
- 提交与 PR 不添加 AI 作者、共同作者或贡献者署名，使用维护者已有的 Git 身份。
- 美术素材使用代码绘制（`tools/instrument_models/`、`tools/instrument_art.py`、`tools/parchment_art.py`），不使用图像生成服务；
  代码注释与文档字符串使用中文。乐器的模型与贴图按 [docs/INSTRUMENT_ART.md](docs/INSTRUMENT_ART.md)：每面单独画、同向平行面
  不要完全重叠（会闪）、元素数在预算内、16 像素一格。
- 乐器的声音分两层（[docs/INSTRUMENT_SAMPLES.md](docs/INSTRUMENT_SAMPLES.md)）：模组本体只放 `tools/instrument_samples.py`
  合成的小体积采样，追求高保真的录音放在单独发布的「音乐厅音源」资源包（`resourcepacks/concert_hall`，`tools/instrument_sources.py`
  从固定提交下载、改编）。录音的来源和协议登记在 CREDITS.md 和资源包的 `NOTICE.txt`。低音乐器的合成音除了 K 计权，还要用
  A 计权、切掉 120 Hz 以下的响度和录音对一下。
- 和乐器（篠笛、神乐铃……）按 [docs/JAPANESE_INSTRUMENTS.md](docs/JAPANESE_INSTRUMENTS.md)：按日本的样子画，不套魔女之森的配色；
  买来的商用采样库先看授权能不能再分发，不能就只做合成层。
- 管风琴按 [docs/PIPE_ORGAN.md](docs/PIPE_ORGAN.md)：音色在客户端现场合成、经 `OrganBufferMixin` 交给声音引擎。
- 服务端从不读 MIDI 或玩家给的路径，只收客户端解析好的纸带并重新校验（`ServerConfig` 的 `[instruments]`）。
- 乐团、工位、指挥按 [docs/ORCHESTRA.md](docs/ORCHESTRA.md)。演奏姿势的落点写在方块模型的像素里，原版人形（`MusicianPoses`）、
  车万女仆（`MaidMusicians`）共用一份；别的模组的骨架（例如附属的 `NpcMusician`）用 `MusicianPoses`、`HeldRig`、`StationPoses`
  的公开接口换算，改这些接口时要想到附属。这条分支上这些接口收实体、不收渲染状态（`MusicianPoses.afterSetupAnim(model, entity)`），
  要画的乐器、琴槌是 `HeldModel`（`HandheldPoses.Held`、`StationPoses.tool`）；`HeldRig` 两条线一样。
- 车万女仆是可选依赖：`MaidMusicians` 只在有女仆 jar（`-Ptlm_jar=<路径>` 或 `libs/touhoulittlemaid-1.20.1.jar`）时编译，发布用的 jar
  一定要带着它编译。这条分支要 1.20.1 Forge 版的女仆 jar（对着 `1.5.3-forge+mc1.20.1` 编译，并在服务端世界测试加载通过）：1.5.3 没有
  `AddMaidLayerEvent`，图层经女仆模组的扩展接口（`@LittleMaidExtension`）加上，演奏时女仆自己的手持物品层换成会跳过的包装，
  GeckoLib 女仆通过 `GeckoMaidEntityCapabilityProvider.CAP` 检查与 `RenderLivingEvent.Post` 补画手持层；
  另外用到了女仆模组 `api` 包以外的类（GeckoLib 动作、模型加载），女仆模组升级后要重新核对（docs/ORCHESTRA.md 第 16 节）。
- 1.20.1 不移植 Logos 附属，不包含 `LegacyIds` 与世界测试 `legacy_addon_ids_resolve`。
- 「思索」（按住 W）的场景按 [docs/PONDER.md](docs/PONDER.md)：布置写在 `ponder/PonderLayouts`，世界测试 `ponder_layouts` 核对场景里讲的事、
  导出蓝图；改了琴凳、工位、乐谱架、管风琴、指挥台的玩法，要回去改对应的场景和文字。
- 测试必须明确区分单元、服务端世界测试和实际客户端检查；没测试过的光影包或实际客户端渲染不能声称已验证。LDLib2 1.20.1 社区版不完整支持原客户端 UI 场景测试用的 `uitest` API，本分支已排除 `*Views.java`、`gradle/ldlib2-uitest.gradle` 和 `TestTravel.java`，游戏本身不需要 LDLib2。在 26.1 或 1.21.1 上验证过的不能当成 1.20.1 也验证过。
- 不提交凭据、运行目录、存档、构建缓存、第三方模组 JAR 或光影包。
