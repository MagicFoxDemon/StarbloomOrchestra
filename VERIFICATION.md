# 验证记录

本仓库从 Logos 附属「炼金釜」拆出来之前的验证记录在附属仓库的
[VERIFICATION.md](https://github.com/wushuowei1145/Logos-Spellwrought-Expanse/blob/main/VERIFICATION.md)（乐器、乐团各节）。

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
