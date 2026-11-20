# 星辰花乐团 Starbloom Orchestra

> **这是 Minecraft 1.20.1 的分支 `mc/1.20.1`。** 主线 `main` 是 Minecraft 26.1.2、`mc/1.21.1` 是 Minecraft 1.21.1 / NeoForge；
> 这条分支把同一个模组移植到 Minecraft 1.20.1 / Forge 47.4.0，打出的 jar 和资源包只给 1.20.1 用。

Minecraft 1.20.1 / Forge 的乐器模组：可以演奏的三角钢琴、竖琴、管风琴、大键琴、钢片琴、钟琴、管钟、大提琴、低音提琴、
定音鼓、大鼓、小军鼓、吊镲、三角铁，手持的小提琴、中提琴、长笛、双簧管、巴松管、小号，和乐器篠笛、尺八、神乐铃；用电脑键盘、MIDI 键盘、打孔纸带或
导入的 MIDI 文件演奏。玩家、生物和车万女仆可以坐上琴凳、站上工位，拿指挥棒指挥整个乐团。

Playable instruments for Minecraft 1.20.1 (Forge 47.4.0): grand piano, harp, pipe organ, harpsichord, celesta, glockenspiel,
tubular bells, cello, double bass, timpani, orchestral percussion, handheld strings, winds and trumpet, and the Japanese
shinobue, shakuhachi and kagura suzu. Play them with the
computer keyboard, a MIDI keyboard, punched music rolls or imported MIDI files; seat players, mobs and Touhou Little Maid maids
at benches and posts and conduct the orchestra with a baton. This is the 1.20.1 branch; `main` targets Minecraft 26.1.2 and `mc/1.21.1` targets Minecraft 1.21.1.

## 安装

把 `starbloom_orchestra-<版本>.jar` 放进 `mods`（Minecraft 1.20.1，Forge 47.2 或更新，已在 47.4.0 验证）。不需要别的前置模组：「思索」用的
Ponder（`1.0.92`）和它要求的 Flywheel（`1.0.6`）都打包在 jar 里。

- **音乐厅音源**（可选）：模组本体只带自己合成的乐器音；想要录音，把 `starbloom_orchestra-concert_hall-<版本>.zip` 放进
  `resourcepacks` 并在「选项 → 资源包」里启用。录音的来源和协议见 [CREDITS.md](CREDITS.md)。这条分支打出的资源包是 1.20.1 的格式（`pack_format: 15`）。
- **车万女仆**（可选）：装了[车万女仆](https://github.com/TartaricAcid/TouhouLittleMaid)时，女仆坐上琴凳、站上工位会摆演奏姿势。
  这条分支对的是 1.20.1 Forge 版的女仆模组 `1.5.3-forge+mc1.20.1`（[docs/ORCHESTRA.md](docs/ORCHESTRA.md) 第 16.3 节）；YSM 模型的女仆不摆姿势。
- **Logos 附属「炼金釜」**：附属在 26.1 上、不移植到 1.20.1，因此 1.20.1 分支不移植 Logos 附属兼容，也不包含 `logos_dimension:*` 旧 ID 别名。

游戏里鼠标停在乐器、琴凳、指挥台上按住 **W**，看「思索」场景讲琴凳归哪件乐器、乐手站在哪、管风琴怎么搭（[docs/PONDER.md](docs/PONDER.md)）。

玩法和每件乐器的做法见 [docs/ORCHESTRA.md](docs/ORCHESTRA.md)（乐团、工位、指挥）、
[docs/HEXENWALD_INSTRUMENTS.md](docs/HEXENWALD_INSTRUMENTS.md)（第一批乐器、打孔纸带、MIDI）、[docs/PIPE_ORGAN.md](docs/PIPE_ORGAN.md)（管风琴）、
[docs/JAPANESE_INSTRUMENTS.md](docs/JAPANESE_INSTRUMENTS.md)（篠笛、尺八、神乐铃）。

## 构建与测试

模组编译目标为 JDK 17（`net.neoforged.moddev.legacyforge` 内的 NeoFormRuntime 需 JDK 21，Gradle 通过 `foojay-resolver-convention` 解析工具链）。Ponder 用官方发布的版本（从 `maven.createmod.net` 取），不用先编译什么；1.20.1 社区版 LDLib2 不完整支持 `uitest` API，本分支已排除客户端 UI 场景自动化测试：

```text
./gradlew build
./gradlew test
./gradlew runGameTestServer
./gradlew concertHallPack
```

世界测试只跑一部分时加 `-PorchestraTests=starbloom_orchestra:instrument_*`（逗号分开可以写几个），查偶发失败时
`-PorchestraTestRepeat=50` 把每个测试复制 50 份。1.20.1 的测试服务端没有 `--tests`，这两项由本模组读系统属性做
（`gametest/GameTestFunctions`）：结果写到 `build/gametest-results.xml`，一个测试都没选中时以 −1 退出。测试用自己的世界
`run/gametestserver/gametestworld`，每次启动前清空。世界测试的结构模板（`data/starbloom_orchestra/structures/gametest/`）
由 `uv run --no-project python tools/gametest_structures.py` 生成。

发布用的 jar 要带着车万女仆的 jar 编译（1.20.1 Forge 版，`-Ptlm_jar=<路径>` 或放到 `libs/touhoulittlemaid-1.20.1.jar`），否则女仆的演奏姿势不在 jar 里。
模型、贴图、合成采样和录音资源包由 `tools/` 下的脚本生成，见 [docs/INSTRUMENT_ART.md](docs/INSTRUMENT_ART.md)、
[docs/INSTRUMENT_SAMPLES.md](docs/INSTRUMENT_SAMPLES.md)。验证记录见 [VERIFICATION.md](VERIFICATION.md)。

## 协议

代码和自制美术、合成采样以 [MIT](LICENSE) 发布。「音乐厅音源」资源包里的录音采样按各自的协议使用，见 [CREDITS.md](CREDITS.md)。
打包在 jar 里的 Ponder、Flywheel 也是 MIT，见 [CREDITS.md](CREDITS.md)。
