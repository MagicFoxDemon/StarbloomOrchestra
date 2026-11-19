# 验证记录

本仓库从 Logos 附属「炼金釜」拆出来之前的验证记录在附属仓库的
[VERIFICATION.md](https://github.com/wushuowei1145/Logos-Spellwrought-Expanse/blob/main/VERIFICATION.md)（乐器、乐团各节）。

## 和乐器：篠笛、神乐铃、尺八

2026-11-18：分支 `feat/japanese-bells-flute`，基于本仓库 main `6fabbac`。两件手持和乐器，做法见 [docs/JAPANESE_INSTRUMENTS.md](docs/JAPANESE_INSTRUMENTS.md)。
录音来自 fumikomidori 的 Otowabi Shinobue / Kagurasuzu（MIT，资源包 `NOTICE.txt` 附全文）；篠笛的 NCW 由新写的 `tools/ncw.py` 解码。
尺八只有合成的一层：维护者买的 ikoa 尺八采样（利用规约禁止再分发）只拿来量谐波和气声，另做了一个只给维护者自己用的录音包（仓库外）。

| 层级 | 结果 |
|---|---|
| 声音 | `tools/ncw.py` 解开篠笛的 17 个 NCW：块与块的接缝处跳变和块内一样（第 99 百分位之比 0.96–1.04），实测音高和文件名差 0.21 个半音以内。`tools/instrument_audit.py` 两层 41/41 干净（篠笛合成 2 音分、录音 8.3 音分；这次让它对无音高的打击乐只核对取区，以前大鼓、吊镲、三角铁都被当成有音高的量，报的是假错）。新循环（篠笛两层、神乐铃连摇两层）解码后绕回那一步都不大于附近 2400 个点里第 99 百分位的一步（最大 0.95 倍）。两件的采样重跑一遍逐字节不变（其他乐器这次没重新生成） |
| 尺八 | 量 ikoa 的 12 个长音：八度都是「文件名 + 12」（`measure_harmonic`），低音区二次泛音约 −8 dB、高音区约 −17 dB，谐波之间的气声约 −31 dB，揉音约 2 音分；合成后同样的量法：各次谐波照两张表、气声 −30.5 dB，`instrument_audit` 2.1 音分。个人用录音包：12 个区、循环接缝最大 0.92 倍；`--private-out` 指到仓库里会被拒绝 |
| 模型 | `tools/instrument_art.py` 全部重新生成：其他乐器的模型、贴图、数据逐字节不变，只多了两件的文件和谱架、琴凳状态的新值；篠笛 16 个元素、神乐铃 57 个、尺八 14 个（预算 70），没有共面闪烁；离线预览看过四个角度和物品栏图标 |
| 单元 | 82 个，0 失败，1 个跳过（管风琴试听）。`SampleMapTest` 改成按键判断哪些区循环（神乐铃只有连摇循环），资源包至少 20 件录音、两件都在；`OrchestraArrangerTest` 多了 77 → 尺八、78 → 篠笛、轨道名、三种笛子互为替身 |
| 世界测试 | 全部 35 个通过（`instrument_music_stand` 多核对三件在谱架上各有自己的样子，`creative_tabs_cover_items` 照常） |
| 实际客户端 | `handheld_play_ui`、`instrument_gallery_ui`、`music_stand_views` 3/3，19/19 项检查：篠笛、尺八各按三个键三路在响（尺八最低 D4，按 X、C、B）、神乐铃按 Z、Q 两路在响，跟着玩家；神乐铃按住连摇 7 秒一直在响、松手 0.7 秒内停下。看过截图：篠笛横在嘴边（朱漆笛头露在外面，管身大半被手挡住，同长笛）；尺八竖在嘴边、朝前下方斜出，两手在管身上；神乐铃举在身侧下巴高度、铃朝上，从正面看不挡脸（第一版挡脸，往外挪过一次）；打击板「一振 Z–M」「按住连摇 Q–P」；谱架上的三件、物品栏图标 |
| 未验证 | 真的按键试玩（测试用模拟按键）、音色只能人工试听（试玩包里有试听）；多人只靠现有的模拟一批音；车万女仆（GeckoLib 没有神乐铃的专门动作）；附属 NPC 当乐手（用本模组的 `HeldRig`，没在附属里跑过）；光影包 |

```text
uv run --no-project --with numpy --with scipy python tools/instrument_sources.py --cache <下载目录> --ffmpeg <ffmpeg> --only shinobue kagura_suzu
uv run --no-project --with numpy --with scipy python tools/instrument_samples.py --ffmpeg <ffmpeg> --only shinobue kagura_suzu
uv run --no-project --with numpy --with scipy python tools/instrument_audit.py --ffmpeg <ffmpeg>
uv run --no-project --with pillow --with numpy python tools/instrument_art.py
./gradlew test
./gradlew runGameTestServer
./gradlew runClient "-PldTest=handheld_play_ui,instrument_gallery_ui,music_stand_views" -PldTestHeadless -PldTestWindow=1280x720
```

## 思索（Ponder）：按住 W 看乐器怎么摆

2026-11-18：分支 `feat/ponder-scenes`，基于本仓库 main `b257282`。七个场景（琴凳归哪件乐器、乐谱架与手持乐器、大提琴与低音提琴、
钟琴管钟与鼓、定音鼓与双人琴凳、搭一台管风琴、指挥台）和三个标签，做法见 [docs/PONDER.md](docs/PONDER.md)。官方发布的 Ponder / catnip 1.0.52
在 26.1 上用不了（进不了世界、打不开思索界面），带的是 `tools/ponder_build.py` 从上游提交 `ef65d6ed` 编译的 1.0.52.1（第 4 节）。

| 层级 | 结果 |
|---|---|
| 编译 Ponder | `tools/ponder_build.py` 在本机从上游编译成功（2 分 49 秒），两个 jar 的版本号和清单里记的上游提交（干净的 `ef65d6ed`）核对过；版本号用 NeoForge 加载器的 Maven 比较核对过：1.0.52.1 高于 1.0.52、低于 1.0.53，依赖范围 `[1.0.52.1+mc26.1.2,)` 不含 1.0.52 |
| 单元 | 82 个，0 失败，1 个跳过（管风琴试听，要 `-PorganDemo`） |
| 世界测试 | 全部 35 个通过。新的 `ponder_layouts`：七份布置在世界里像玩家那样放一遍，核对场景讲的事——琴键前的琴凳关联钢琴、旁边的不关联，双人琴凳关联钢琴，竖琴琴后的琴凳关联竖琴；乐谱架对着坐着拉小提琴的乐手，架上放着乐器时不再管琴凳；提琴的人在琴后、和琴同向；钟琴、管钟、小军鼓的工位在前面一格且空着，大鼓的鼓手在西边；双人琴凳对着两面鼓，单人琴凳只对着前面那一面；管风琴认出 9 个风箱、13 格锡管、6 格木管、2 个号角管，叠起来的音管上下形状不同；指挥台两格宽。`-PponderExport` 两次导出的蓝图逐字节一致（指挥台记的世界坐标换成了蓝图坐标）。新的 `command_tree_syncs`：命令树里每个参数的类型都注册过——用官方 1.0.52 时它列出 catnip 的三个参数，换成自编译版后通过 |
| 实际客户端 | `ponder_scenes_ui` 1/1，43/43 项检查：Ponder 自己生成的 51 个文本键中英文都有、英文和场景代码一字不差；英文、中文各把七个场景经按住 W 的同一个入口（`ScreenOpener.transitionTo`）打开，场景自己往下放、关键帧数对得上；每个关键帧和刚打开时都截了图，看过：标题、标签图标、时间轴和按钮都在，乐器的朝向、描边的格子、文字框的位置都对，管风琴的音管在画面里 |
| 未验证 | 真的按住 W（测试用的是同一个入口，没有真按键）；思索首页的标签索引没截图；和机械动力一起装；局域网、专用服务器（官方 1.0.52 的命令树问题出在服务端，世界测试就是在服务端跑的）；CI 里编译 Ponder 那一步（开 PR 后看）；光影包 |

```text
uv run --no-project python tools/ponder_build.py
./gradlew test
./gradlew runGameTestServer
./gradlew runGameTestServer -PorchestraTests=starbloom_orchestra:ponder_layouts -PponderExport
./gradlew runClient -PldTest=ponder_scenes_ui -PldTestHeadless -PldTestWindow=1600x900
```

## GeckoLib 女仆用上乐器：端琴拉弓、乐器画在手上

2026-11-17：分支 `feat/gecko-maid-playing`，基于本仓库 main `ca57e94`。这是附属里没来得及提交的分支（附属的 `feat/gecko-maid-playing`，
基于附属 #269，2026-11-15 做完、维护者试过）原样搬过来：维护者当时的反馈是女仆大多能坐上琴凳，但 GeckoLib 模型包的女仆只是拿着乐器、
没有演奏的动作。改动见 [docs/ORCHESTRA.md](docs/ORCHESTRA.md) 第 16.3 节：五段 `hold_mainhand#starbloom_orchestra:maid_pose/...`
默认动作（物品标签 `data/starbloom_orchestra/tags/item/maid_pose/`），`AddMaidLayerEvent.Gecko` 加一层按手的挂点和头骨画乐器、琴弓、
琴槌（`MaidMusicians.GeckoLayer`），`HeldRig` 加琴弓压弦的点。搬的时候只换了包名和命名空间，补丁原样打上。

| 层级 | 结果 |
|---|---|
| 编译 | 带车万女仆的 jar（`-Ptlm_jar`）、不带都能编译；不带时单元测试 82 个照常全过 |
| 实际客户端 | 在附属的音乐厅里跑（附属依赖本模组的 jar，`-Ptlm`）：本地临时场景（**没有提交**，同当时的做法，免得测试要带女仆模组）让酒狐拿着小提琴、长笛、双簧管、巴松管、小号坐琴凳，两只拿着小提琴、小号站在地上；看过截图：小提琴在下巴下伸向托琴颈的左手、琴弓从右手搭到琴弦上，隔 5 刻右手和弓换了位置（坐着才拉弓）；巴松管从右胯斜过左肩、哨片在嘴边；站着拿的端在演奏的位置、不拉弓。回归附属的 `orchestra_maids_ui`：酒狐在钢片琴、大提琴、管钟前，大提琴的弓横过琴弦；**2/2 场景、4/4 检查** |
| 未验证 | 长笛那张机位从背后、太暗，看不清笛子；别的模型包（手位按酒狐调的）；模型包自己写了同名动作；光影包 |

## 从附属拆出：星辰花乐团 0.1.0

2026-11-17：从附属 main `f80f7d05`（#275，含打击乐组 #270）拆出。乐器的全部代码、资源、工具、文档和「音乐厅音源」资源包
搬到本仓库，命名空间 `logos_dimension` → `starbloom_orchestra`，包名 `dev.logos.dimension.(client.)music` →
`dev.starbloom.orchestra.(client.)music`。附属同时改成依赖本模组（附属的分支 `refactor/starbloom-orchestra-split`）。

| 层级 | 结果 |
|---|---|
| 搬运 | 107 个 Java 文件（含测试）、529 个资源文件、两种语言各 265 条翻译、9 个手势枚举扩展、7 个 mixin、工具与文档；`tools/instrument_art.py` 在本仓库重新生成全部乐器的方块状态、模型、贴图、配方、掉落，和搬过来的文件逐字节一致 |
| 改动 | 手写：主类、注册表、配置（`ClientConfig` 7 项、`ServerConfig` 2 项，文件名换成 `starbloom_orchestra-*.toml`）、创造物品栏「星辰花乐团」、旧 ID 别名（`LegacyIds`）、声部描边自己的渲染钩子（`PartOutlineMixin`，原来借附属的全屏特效 mixin）、乐器界面的羊皮纸贴图（原来直接引用 Logos 的 `spell_editor_parchment.png`，改成 `tools/parchment_art.py` 画的原创像素画）；世界测试里原来用附属精灵的地方换成村民；附属要用的 `HeldRig`、`VoiceBank` 等改成公开 |
| 单元 | 82 个，0 失败，1 个跳过（管风琴试听，要 `-PorganDemo`）；含搬过来的 `LangCoverageTest`（每个方块、物品两种语言都有名字）、`ItemModelDisplayTest`、`SampleMapTest`（核对资源包） |
| 世界测试 | 全部 33 个通过，含新的 `legacy_addon_ids_resolve`（旧 ID 的方块、物品、方块实体、实体，区块里的方块状态、物品栏里带数据组件的指挥棒都读成本模组的）、`creative_tabs_cover_items` |
| 实际客户端 | 搬过来的 22 个场景分三批全部通过：7/7（13 项检查）、7/7（21 项）、8/8（57 项）；看过截图：乐器界面的新羊皮纸、指挥选中声部的描边照常 |
| 和附属一起 | 附属（`refactor/starbloom-orchestra-split`）依赖本模组的 jar 编译通过、单元 354 个全过、世界测试 250 个过 249 个（`lingxiao_mana` 是附属里本来就时好时坏的凌霄测试：拆分前的代码重复 10 次失败 3 次，差的是一刻的自然回魔）；附属音乐厅里的场景：`orchestra_hall_ui` + `orchestra_player_mallets_ui` 2/2（11 项检查）、`orchestra_percussion_ui` + `orchestra_stand_player_ui` 2/2（4 项）、带车万女仆的 `orchestra_maids_ui` 1/1（3 项）；看过截图：精灵弹钢琴、神狐弹大键琴、女仆拉大提琴，手都在琴上 |
| 未验证 | 光影包；多人、专用服务器；拿真的老存档进游戏（旧 ID 只用世界测试核对过） |

```text
./gradlew test
./gradlew runGameTestServer
./gradlew runClient "-PldTest=instrument_gallery_ui,instrument_views,instrument_play_ui,roll_editor_ui,midi_import_ui,midi_keyboard_ui,concert_hall_pack_ui" -PldTestHeadless -PldTestWindow=1280x720
./gradlew runClient "-PldTest=mallet_instrument_views,mallet_instrument_play,music_stand_views,handheld_play_ui,organ_views,organ_reload_ui,organ_play_ui" -PldTestHeadless -PldTestWindow=1280x720
./gradlew runClient "-PldTest=orchestra_conduct_ui,orchestra_baton_ui,orchestra_metronome_ui,orchestra_seated_member_ui,orchestra_views,orchestra_handheld_ui,orchestra_seated_ui,ensemble_resync_ui" -PldTestHeadless -PldTestWindow=1280x720
```
