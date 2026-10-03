"""ADB Release ABBA 对照。先用仅 debug 的 PerformanceFixtureActivity seed，结束后 clean。
进程冷启动保留文件缓存；首帧耗时来自 am start -S -W，不代表列表全部加载完成。
滚动统计来自 gfxinfo framestats；固定 AOT speed，禁止清除应用数据或卸载。
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import statistics
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument("--baseline", type=Path, required=True)
parser.add_argument("--candidate", type=Path, required=True)
parser.add_argument("--serial", required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
PKG = "com.pickupcode.app"
COMPONENT = PKG + "/.MainActivity"

def adb(*cmd, timeout=55):
    return subprocess.run(["adb", "-s", args.serial, *cmd], check=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          timeout=timeout, encoding="utf-8", errors="replace").stdout

def launch():
    out = adb("shell", "am", "start", "-S", "-W", "-n", COMPONENT)
    found = re.search(r"TotalTime: (\d+)", out)
    assert found and "Status: ok" in out, "启动失败或缺少首帧时间"
    time.sleep(1.5)
    return int(found[1])

def temperature():
    match = re.search(r"temperature: (\d+)", adb("shell", "dumpsys", "battery"))
    return int(match[1]) / 10 if match else None

def metrics(out):
    def number(pattern):
        m = re.search(pattern, out)
        assert m, "gfxinfo 缺少字段 " + pattern
        return int(m[1])
    result = {
        "frames": number(r"Total frames rendered: (\d+)"),
        "janky": number(r"Janky frames: (\d+)"),
        "jankyLegacy": number(r"Janky frames \(legacy\): (\d+)"),
        "p50Ms": number(r"50th percentile: (\d+)ms"),
        "p90Ms": number(r"90th percentile: (\d+)ms"),
        "p95Ms": number(r"95th percentile: (\d+)ms"),
        "p99Ms": number(r"99th percentile: (\d+)ms"),
    }
    assert result["frames"] > 100, "滚动帧样本太少"
    result["jankPercent"] = round(100 * result["janky"] / result["frames"], 3)
    return result

policy = adb("shell", "dumpsys", "window", "policy")
assert "showing=true" not in policy, "请先解锁手机"
size = adb("shell", "wm", "size")
assert "1440x3200" in size and "Override" not in size, "请恢复物理分辨率"
assert adb("shell", "settings", "get", "system", "font_scale").strip() == "1.0"
report = {
    "method": "ABBA, Release + R8 + resource/native compression, AOT speed, 144 synthetic records",
    "baseline": {"commit": "407ca98", "sha256": hashlib.sha256(args.baseline.read_bytes()).hexdigest()},
    "candidate": {"commit": "2faf6eb", "sha256": hashlib.sha256(args.candidate.read_bytes()).hexdigest()},
    "device": {"model": adb("shell", "getprop", "ro.product.model").strip(),
               "sdk": adb("shell", "getprop", "ro.build.version.sdk").strip(),
               "size": size.strip(), "density": adb("shell", "wm", "density").strip()},
    "blocks": []
}

def save():
    (args.output / "results.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

for block, variant in enumerate(["baseline", "candidate", "candidate", "baseline"], 1):
    apk = args.baseline if variant == "baseline" else args.candidate
    assert "Success" in adb("install", "--no-incremental", "-r", str(apk)), "保留数据安装失败"
    assert "Success" in adb("shell", "cmd", "package", "compile", "-m", "speed", "-f", PKG), "AOT 失败"
    launch(); launch()
    item = {"block": block, "variant": variant, "temperatureStartC": temperature(), "startupMs": [], "scroll": []}
    report["blocks"].append(item)
    save()
    print(f"Block {block} {variant}: warmup complete", flush=True)
    for i in range(5):
        item["startupMs"].append(launch())
        save()
    print(f"Block {block}: startup {item['startupMs']}", flush=True)
    for trial in range(3):
        launch()
        # 先走一遍，让首次绘制品牌路径/字形不混入持续滚动样本。
        adb("shell", "input", "swipe", "720", "2500", "720", "900", "350")
        adb("shell", "input", "swipe", "720", "900", "720", "2500", "350")
        time.sleep(0.5)
        adb("shell", "dumpsys", "gfxinfo", PKG, "reset")
        for direction in [0] * 6 + [1] * 6:
            y1, y2 = (2500, 900) if direction == 0 else (900, 2500)
            adb("shell", "input", "swipe", "720", str(y1), "720", str(y2), "350")
        time.sleep(0.5)
        raw = adb("shell", "dumpsys", "gfxinfo", PKG, "framestats")
        (args.output / f"block-{block}-scroll-{trial+1}.txt").write_text(raw, encoding="utf-8")
        result = metrics(raw)
        item["scroll"].append(result)
        save()
        print(f"Block {block} scroll {trial+1}: {result}", flush=True)
    item["temperatureEndC"] = temperature()
    save()

summary = {}
for variant in ["baseline", "candidate"]:
    blocks = [b for b in report["blocks"] if b["variant"] == variant]
    cold = [x for b in blocks for x in b["startupMs"]]
    scroll = [x for b in blocks for x in b["scroll"]]
    frames = sum(x["frames"] for x in scroll)
    janky = sum(x["janky"] for x in scroll)
    summary[variant] = {"startupSamples": len(cold), "startupMedianMs": statistics.median(cold),
                        "startupMinMs": min(cold), "startupMaxMs": max(cold),
                        "scrollSamples": len(scroll), "scrollFrames": frames, "jankyFrames": janky,
                        "jankPercent": round(janky / frames * 100, 3),
                        "scrollP95MedianMs": statistics.median(x["p95Ms"] for x in scroll),
                        "scrollP99MedianMs": statistics.median(x["p99Ms"] for x in scroll)}
report["summary"] = summary
save()
print(json.dumps(summary, ensure_ascii=False), flush=True)
