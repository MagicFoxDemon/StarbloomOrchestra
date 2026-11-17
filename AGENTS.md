# Starbloom Orchestra（星辰花乐团）

先阅读 README.md、VERIFICATION.md 和 [docs/ORCHESTRA.md](docs/ORCHESTRA.md)。本工程是一个独立的 NeoForge 模组（mod ID
`starbloom_orchestra`），不依赖 Logos；Logos 的附属「炼金釜」（`logos_dimension`）依赖它，用附属的 NPC 当乐手。

- 新改动在分支完成，一个 PR 只解决一个主题。提交标题用英文，说明用中文。
- 提交与 PR 不添加 AI 作者、共同作者或贡献者署名，使用维护者已有的 Git 身份。
- 美术素材使用代码绘制（`tools/instrument_models/`、`tools/instrument_art.py`、`tools/parchment_art.py`），不使用图像生成服务；
  代码注释与文档字符串使用中文。乐器的模型与贴图按 [docs/INSTRUMENT_ART.md](docs/INSTRUMENT_ART.md)：每面单独画、同向平行面
  不要完全重叠（会闪）、元素数在预算内、16 像素一格。
- 乐器的声音分两层（[docs/INSTRUMENT_SAMPLES.md](docs/INSTRUMENT_SAMPLES.md)）：模组本体只放 `tools/instrument_samples.py`
  合成的小体积采样，追求高保真的录音放在单独发布的「音乐厅音源」资源包（`resourcepacks/concert_hall`，`tools/instrument_sources.py`
  从固定提交下载、改编）。录音的来源和协议登记在 CREDITS.md 和资源包的 `NOTICE.txt`。低音乐器的合成音除了 K 计权，还要用
  A 计权、切掉 120 Hz 以下的响度和录音对一下。
- 管风琴按 [docs/PIPE_ORGAN.md](docs/PIPE_ORGAN.md)：音色在客户端现场合成、经 `OrganBufferMixin` 交给声音引擎。
- 服务端从不读 MIDI 或玩家给的路径，只收客户端解析好的纸带并重新校验（`ServerConfig` 的 `[instruments]`）。
- 乐团、工位、指挥按 [docs/ORCHESTRA.md](docs/ORCHESTRA.md)。演奏姿势的落点写在方块模型的像素里，原版人形（`MusicianPoses`）、
  车万女仆（`MaidMusicians`）共用一份；别的模组的骨架（例如附属的 `NpcMusician`）用 `MusicianPoses`、`HeldRig`、`StationPoses`
  的公开接口换算，改这些接口时要想到附属。
- 车万女仆是可选依赖：`MaidMusicians` 只在有女仆 jar（`-Ptlm_jar=<路径>` 或 `libs/touhoulittlemaid.jar`）时编译，发布用的 jar
  一定要带着它编译。
- 乐器原先在附属里，`LegacyIds` 给每个注册项加了 `logos_dimension:<名字>` 的别名（世界测试 `legacy_addon_ids_resolve`）；
  新加的方块、物品不用管这件事。
- 测试必须明确区分单元、世界和实际客户端检查；没测试过的光影包不能声称已验证。客户端场景（`client/music/*Views`，LDLib2 的
  `DEV_ONLY` 场景）只在开发环境里跑，游戏本身不需要 LDLib2。
- 不提交凭据、运行目录、存档、构建缓存、第三方模组 JAR 或光影包。
