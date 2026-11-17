"""自己编译「思索」用的 Ponder 和 catnip（docs/PONDER.md 第 4 节）。

官方发布的 1.0.52+mc26.1.2 在 26.1 上用不了（进不了世界、按住 W 打不开界面），上游 mc26.1/dev 分支已经修好但还没发布。
这里从固定的提交拉上游源码，用它自己的构建编译 NeoForge 版，发布到 libs/ponder-maven（libs/ 不进 git），版本号
1.0.52.1+mc26.1.2：比官方 1.0.52 高（别的模组也带着 1.0.52 时用这一份），比以后的 1.0.53 低（官方新版出来以后用官方的）。

    uv run --no-project python tools/ponder_build.py

要 git、JDK 25 和网络（上游的构建会下载它自己的 Gradle、NeoForge、Fabric 工具链）。同一个提交编译过就直接核对、不重新编译；
--force 重新编译。上游的发布任务只在设了 MAVEN_USERNAME / MAVEN_PASSWORD 时才推到它的远程仓库，这里把这两个变量去掉了。
"""
import hashlib
import os
import shutil
import stat
import subprocess
import sys
import zipfile
from pathlib import Path

REPO = "https://github.com/Creators-of-Create/Ponder.git"
# mc26.1/dev，2026-11-14「Fix heights again」
COMMIT = "ef65d6ed333c362d72fa43a8bc6848e3b9b4c9a3"
# 上游的版本号是 1.0.<BUILD_NUMBER>+mc26.1.2；同 gradle.properties 的 ponder_version
BUILD_NUMBER = "52.1"
VERSION = f"1.0.{BUILD_NUMBER}+mc26.1.2"

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "build" / "ponder-upstream"
OUT = ROOT / "libs" / "ponder-maven"
ARTIFACTS = {"ponder": "net/createmod/ponder/ponder-neoforge", "catnip": "net/createmod/catnip/catnip-neoforge"}


def git(*args, capture=False):
    r = subprocess.run(["git", *args], cwd=SRC, check=True, capture_output=capture, text=True)
    return r.stdout.strip() if capture else None


def clean_checkout():
    """SRC 是不是正好在 COMMIT、没有改动。"""
    if not (SRC / ".git").exists():
        return False
    return git("rev-parse", "HEAD", capture=True) == COMMIT and git("status", "--porcelain", capture=True) == ""


def checkout():
    if clean_checkout():
        return
    if SRC.exists():
        # git 的对象文件是只读的，Windows 上要先去掉只读才删得掉
        shutil.rmtree(SRC, onexc=lambda f, p, e: (os.chmod(p, stat.S_IWRITE), f(p)))
    SRC.mkdir(parents=True)
    git("init", "-q")
    git("remote", "add", "origin", REPO)
    git("fetch", "-q", "--depth", "1", "origin", COMMIT)
    git("-c", "advice.detachedHead=false", "checkout", "-q", "FETCH_HEAD")


def jar(name):
    return OUT / ARTIFACTS[name] / VERSION / f"{ARTIFACTS[name].rsplit('/', 1)[1]}-{VERSION}.jar"


def build():
    env = {k: v for k, v in os.environ.items() if k not in ("MAVEN_USERNAME", "MAVEN_PASSWORD")}
    env["BUILD_NUMBER"] = BUILD_NUMBER
    gradlew = str(SRC / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    subprocess.run([gradlew, "--no-daemon", ":ponder-neoforge:publishToMavenLocal", ":catnip-neoforge:publishToMavenLocal",
                    f"-Dmaven.repo.local={OUT}"], cwd=SRC, env=env, check=True)


def verify():
    """jar 里的版本号、上游提交（构建写进清单的 Git-Hash）对得上，再打出 SHA-256。"""
    for name in ARTIFACTS:
        path = jar(name)
        if not path.exists():
            sys.exit(f"missing {path}")
        with zipfile.ZipFile(path) as z:
            toml = z.read("META-INF/neoforge.mods.toml").decode("utf-8")
            manifest = z.read("META-INF/MANIFEST.MF").decode("utf-8")
        if f'version = "{VERSION}"' not in toml and f"version=\"{VERSION}\"" not in toml:
            sys.exit(f"{path.name}: mods.toml does not say version {VERSION}")
        if COMMIT not in manifest or "-modified" in manifest:
            sys.exit(f"{path.name}: not built from a clean checkout of {COMMIT}")
        print(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.relative_to(ROOT).as_posix()}")


def main():
    force = "--force" in sys.argv[1:]
    if force or not all(jar(n).exists() for n in ARTIFACTS):
        checkout()
        build()
    verify()


if __name__ == "__main__":
    main()
