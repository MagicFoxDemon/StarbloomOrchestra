# 星辰花乐团 Starbloom Orchestra

Minecraft 26.1.2 / NeoForge 的乐器模组：可以演奏的三角钢琴、竖琴、管风琴、大键琴、钢片琴、钟琴、管钟、大提琴、低音提琴、
定音鼓、大鼓、小军鼓、吊镲、三角铁，手持的小提琴、中提琴、长笛、双簧管、巴松管、小号，和乐器篠笛、尺八、神乐铃；用电脑键盘、MIDI 键盘、打孔纸带或
导入的 MIDI 文件演奏。玩家、生物和车万女仆可以坐上琴凳、站上工位，拿指挥棒指挥整个乐团。

Playable instruments for Minecraft 26.1.2 (NeoForge): grand piano, harp, pipe organ, harpsichord, celesta, glockenspiel,
tubular bells, cello, double bass, timpani, orchestral percussion, handheld strings, winds and trumpet, and the Japanese
shinobue, shakuhachi and kagura suzu. Play them with the
computer keyboard, a MIDI keyboard, punched music rolls or imported MIDI files; seat players, mobs and Touhou Little Maid maids
at benches and posts and conduct the orchestra with a baton.

## 安装

把 `starbloom_orchestra-<版本>.jar` 放进 `mods`。不需要别的前置模组（「思索」用的 Ponder 库打包在 jar 里）。

- **音乐厅音源**（可选）：模组本体只带自己合成的乐器音；想要录音，把 `starbloom_orchestra-concert_hall-<版本>.zip` 放进
  `resourcepacks` 并在「选项 → 资源包」里启用。录音的来源和协议见 [CREDITS.md](CREDITS.md)。
- **车万女仆**（可选）：装了[车万女仆](https://github.com/TartaricAcid/TouhouLittleMaid)时，女仆坐上琴凳、站上工位会摆演奏姿势。
- **Logos 附属「炼金釜」**：附属从 0.12.0 起依赖本模组，附属里的精灵、神狐、灵猫也能当乐手。这些乐器以前就在附属里，
  老存档读档时自动换成本模组的（放下的乐器、纸带、指挥棒都在）；乐器的客户端设置要在 `starbloom_orchestra-client.toml` 里重新设一次。

游戏里鼠标停在乐器、琴凳、指挥台上按住 **W**，看「思索」场景讲琴凳归哪件乐器、乐手站在哪、管风琴怎么搭（[docs/PONDER.md](docs/PONDER.md)）。

玩法和每件乐器的做法见 [docs/ORCHESTRA.md](docs/ORCHESTRA.md)（乐团、工位、指挥）、
[docs/HEXENWALD_INSTRUMENTS.md](docs/HEXENWALD_INSTRUMENTS.md)（第一批乐器、打孔纸带、MIDI）、[docs/PIPE_ORGAN.md](docs/PIPE_ORGAN.md)（管风琴）、
[docs/JAPANESE_INSTRUMENTS.md](docs/JAPANESE_INSTRUMENTS.md)（篠笛、尺八、神乐铃）。

## 构建与测试

第一次构建前先编译「思索」用的 Ponder（官方发布的版本在 26.1 上用不了，从上游源码编译到 `libs/ponder-maven`，见 [docs/PONDER.md](docs/PONDER.md) 第 4 节）：

```text
uv run --no-project python tools/ponder_build.py
./gradlew build
./gradlew test
./gradlew runGameTestServer
./gradlew runClient -PldTest=instrument_gallery_ui -PldTestHeadless -PldTestWindow=1280x720
./gradlew concertHallPack
```

发布用的 jar 要带着车万女仆的 jar 编译（`-Ptlm_jar=<路径>` 或放到 `libs/touhoulittlemaid.jar`），否则女仆的演奏姿势不在 jar 里。
模型、贴图、合成采样和录音资源包由 `tools/` 下的脚本生成，见 [docs/INSTRUMENT_ART.md](docs/INSTRUMENT_ART.md)、
[docs/INSTRUMENT_SAMPLES.md](docs/INSTRUMENT_SAMPLES.md)。验证记录见 [VERIFICATION.md](VERIFICATION.md)。

## 协议

代码和自制美术、合成采样以 [MIT](LICENSE) 发布。「音乐厅音源」资源包里的录音采样按各自的协议使用，见 [CREDITS.md](CREDITS.md)。
