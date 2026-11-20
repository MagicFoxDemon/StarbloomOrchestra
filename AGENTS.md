# Starbloom Orchestra（星辰花乐团）

先阅读 README.md、VERIFICATION.md 和 [docs/ORCHESTRA.md](docs/ORCHESTRA.md)。本工程是一个独立的 NeoForge 模组（mod ID
`starbloom_orchestra`），不依赖 Logos；Logos 的附属「炼金釜」（`logos_dimension`）依赖它，用附属的 NPC 当乐手。

**这条分支 `mc/1.21.1` 是 Minecraft 1.21.1 / NeoForge 21.1 的一线**（Java 21，Parchment 映射）；`main` 是 26.1.2 的一线，
附属也跟着 26.1。两条线各自发布，不互相合并：

- 只关 1.21.1 的改动从 `mc/1.21.1` 开分支，PR 发到 `mc/1.21.1`。
- `main` 上的改动要带过来时，从 `mc/1.21.1` 开分支、单独发 PR 到 `mc/1.21.1`（一个 PR 移植一个主题），按改动的意思在 1.21.1
  的接口上重写，不要把 `main` 整个合并进来；生成的文件（模型、蓝图、结构模板）在这条分支上重新生成，不拷 `main` 的。两边都该有的修正，
  另一条线也发一份。
- 移植时要换写法的地方：
  - 渲染：没有渲染状态。演奏姿势按实体算：`RenderLivingEvent.Pre` 里记下这一帧的 `MusicianPoses.Drawing`，模型 setupAnim 之后
    由 `MusicianModelMixin` 摆；手里原本的东西由 `MusicianHandItemMixin` 不画；拿在手上演奏的乐器、琴弓、琴槌是单独加载的模型（`HeldModel`）。
  - 模型：没有 `assets/<命名空间>/items/` 物品模型定义；手持乐器演奏时靠物品属性 `starbloom_orchestra:playing` 和 `overrides` 换模型；
    1.21.1 原版读不了的元素转动走本模组的几何加载器 `starbloom_orchestra:rotated_cuboids`，`tools/instrument_kit.py` 写文件时自动换
    （[docs/INSTRUMENT_ART.md](docs/INSTRUMENT_ART.md) 第 9 节）。
  - 发往客户端的包：公共代码注册时交 `ClientPayloadHandlers.dispatch`，客户端在 `MusicClient.ModBus` 里登记真正的处理器。
  - 世界测试：1.21.1 的测试框架，经 `gametest/GameTestFunctions` 注册、挑选、写报告；结构模板由 `tools/gametest_structures.py` 生成。
  - 「思索」：官方发布的 Ponder（没有 `tools/ponder_build.py`），见 [docs/PONDER.md](docs/PONDER.md) 第 4 节。

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
- 车万女仆是可选依赖：`MaidMusicians` 只在有女仆 jar（`-Ptlm_jar=<路径>` 或 `libs/touhoulittlemaid.jar`）时编译，发布用的 jar
  一定要带着它编译。这条分支要 1.21.1 NeoForge 版的女仆 jar（对着 1.5.3-neoforge+mc1.21.1 编译、试过）：1.5.3 没有
  `AddMaidLayerEvent`，图层经女仆模组的扩展接口（`@LittleMaidExtension`）加上，演奏时女仆自己的手持物品层换成会跳过的包装；
  另外用到了女仆模组 `api` 包以外的类（GeckoLib 动作、模型加载），女仆模组升级后要重新核对（docs/ORCHESTRA.md 第 16 节）。
- 乐器原先在附属里，`LegacyIds` 给每个注册项加了 `logos_dimension:<名字>` 的别名（世界测试 `legacy_addon_ids_resolve`）；
  新加的方块、物品不用管这件事。
- 「思索」（按住 W）的场景按 [docs/PONDER.md](docs/PONDER.md)：布置写在 `ponder/PonderLayouts`，世界测试 `ponder_layouts` 核对场景里讲的事、
  导出蓝图；改了琴凳、工位、乐谱架、管风琴、指挥台的玩法，要回去改对应的场景和文字。
- 测试必须明确区分单元、世界和实际客户端检查；没测试过的光影包不能声称已验证。客户端场景（`client/music/*Views`、
  `client/ponder/PonderViews`，LDLib2 的 `DEV_ONLY` 场景）只在开发环境里跑，游戏本身不需要 LDLib2。在 26.1 上验证过的不能当成
  1.21.1 也验证过。
- 不提交凭据、运行目录、存档、构建缓存、第三方模组 JAR 或光影包。
