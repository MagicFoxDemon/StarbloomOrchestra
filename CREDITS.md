# 致谢与第三方素材

本模组的代码、自制美术和合成的乐器采样以 MIT 发布（[LICENSE](LICENSE)）。下面列出的第三方素材按各自的协议使用，**不适用**本模组的协议。

## 乐器采样

模组本体只带自己合成的乐器音（`tools/instrument_samples.py`），没有第三方素材。单独发布的「音乐厅音源」资源包
（仓库的 `resourcepacks/concert_hall/`）里的录音采样取自下面五个采样库，由 `tools/instrument_sources.py` 从固定提交下载、改编
（混成单声道、重采样、切头截尾、加循环点、调整音量、重新编码）。资源包根目录的 `NOTICE.txt` 是随包的协议说明，
做法见 [乐器采样](docs/INSTRUMENT_SAMPLES.md)。

| 乐器 | 来源 | 作者 | 协议 |
|---|---|---|---|
| 星辰花三角钢琴 | [Salamander Grand Piano V3](https://github.com/sfzinstruments/SalamanderGrandPiano)（提交 `3382bf94`，原始发布 <https://archive.org/details/SalamanderGrandPianoV3>） | Alexander Holm；SFZ / FLAC 版 kinwie（sfzinstruments）；重新调律 Markus Fiedler | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/)，**须署名**；本模组的钢琴采样是它的改编 |
| 小提琴、中提琴、大提琴、低音提琴、长笛、双簧管、巴松管、小号、定音鼓 | [VS Chamber Orchestra: Community Edition](https://github.com/sgossner/VSCO-2-CE)（提交 `44030090`） | Versilian Studios（Sam Gossner、Simon Dalzell 录音，Elan Hickler 切采样） | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/)，不要求署名，这里致谢 |
| 灵猫竖琴、钟琴、管钟、大键琴、大鼓、小军鼓、吊镲、三角铁 | [Versilian Community Sample Library](https://github.com/sgossner/VCSL)（提交 `c1ea7bcc`） | Versilian Studios 及贡献者 | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/)，不要求署名，这里致谢 |
| 篠笛 | [Otowabi Shinobue 1.0](https://github.com/fumikomidori/OtowabiShinobue)（提交 `26a14964`，Kontakt 的 NCW 格式，用 `tools/ncw.py` 解码） | fumikomidori（<https://otowabi.com/>） | MIT，**须附版权声明与许可声明**（`NOTICE.txt` 里有全文） |
| 神乐铃 | [Otowabi Kagurasuzu 1.1](https://github.com/fumikomidori/OtowabiKagurasuzu)（提交 `db1d22e5`） | fumikomidori | MIT，同上 |

转载资源包、放进整合包、二次修改时保留 Salamander 的署名与协议链接、Otowabi 两个库的 MIT 声明（都在资源包根目录的 `NOTICE.txt`）。

## 管风琴合成的参考（没有使用其代码或数据）

教堂管风琴的声音（`music/organ/OrganSynth`、`OrganReverb`）是本模组自己写的加法合成和混响，下面几个项目只作为思路参考：
没有拷贝、改写它们的代码，也没有使用它们的音栓数据或冲激响应文件。借鉴了什么、没借鉴什么见 [教堂管风琴](docs/PIPE_ORGAN.md) 第 9 节。

| 项目 | 作者 | 协议 | 参考了 |
|---|---|---|---|
| [Aeolus](https://kokkinizita.linuxaudio.org/linuxaudio/aeolus/index.html) | Fons Adriaensen | GPL-3 | 每个泛音各自的电平与起音、音色沿键盘变化、管列微失谐与随机起伏、起音段 + 循环段的波表 |
| [Aeolus 插件](https://github.com/Archie3d/aeolus_plugin) | Arthur Benilov（Archie3d） | GPL-3 | 混合音栓高音区折回 |
| [Caecilia](https://github.com/alessonqueirozdev-hub/Caecilia) | Alesson Queiroz 与贡献者 | Apache-2.0 | 起音快慢随音高、簧管起音滑音、每根管确定的小差异、簧管共振峰 |
| [zita-rev1](https://kokkinizita.linuxaudio.org/linuxaudio/zita-rev1-doc/quickguide.html) | Fons Adriaensen | GPL | 混响参数的定义（预延迟、低频 / 中频两段混响时间与分频点、「混响时间减半处」的高频阻尼）；网络结构按 Jot 的反馈延迟网络文献自写 |

## 思索（Ponder）

「按住 W 思索」的图文讲解（`client/ponder/`，[docs/PONDER.md](docs/PONDER.md)）用的是 [Ponder](https://github.com/Creators-of-Create/Ponder)
和它要的 catnip，作者 simibubi、Pepper、zelophed（Creators of Create），**MIT**。官方发布的 1.0.52 在 26.1 上用不了，
本模组带的是从上游 mc26.1/dev 分支的提交 `ef65d6ed` 原样编译的（`tools/ponder_build.py`，版本号记作 1.0.52.1+mc26.1.2，源码没改），
打包在本模组的 jar 里（jar-in-jar，各自的 `LICENSE` 随 jar 一起）；场景、蓝图和文字是本模组自己做的。

## 车万女仆（Touhou Little Maid）

女仆的演奏姿势（`client/music/MaidMusicians`）调用车万女仆的公开接口（事件、模型类），不复制它的代码或美术；
`assets/starbloom_orchestra/animation/maid_musician.animation.json` 是本模组自己做的动画。车万女仆：
TartaricAcid 等，<https://github.com/TartaricAcid/TouhouLittleMaid>，代码 MIT。
