#!/usr/bin/env python3
"""Замер плавности лаунчера на реальном телевизоре.

Нажимает кнопки пульта через `monkey --port` (одна лёгкая программа на устройстве,
`adb shell input` тратит ~1.7 с на нажатие на слабом ТВ), собирает
`dumpsys gfxinfo … framestats` и печатает сводку: сколько кадров опоздало и где
ушло время — UI-поток, RenderThread, GPU, ожидание буфера.

    tools/bench.py [--serial 10.100.102.13:5555] [--runs 2] [--label текст]
"""
import argparse
import os
import socket
import statistics
import subprocess
import sys
import time

PKG = "io.github.margantcovka.tvlauncher"
ACTIVITY = f"{PKG}/.MainActivity"
ADB = os.environ.get("ADB", "/opt/homebrew/share/android-commandlinetools/platform-tools/adb")
PORT = 1080
VSYNC_NS = 16_666_667

# (клавиша, пауза после нажатия в секундах). Медленная навигация, потом быстрая, плюс смена строк.
SCENARIO = (
    [("DPAD_RIGHT", 0.45)] * 4
    + [("DPAD_DOWN", 0.6)]
    + [("DPAD_LEFT", 0.45)] * 4
    + [("DPAD_UP", 0.6)]
    + [("DPAD_RIGHT", 0.15)] * 4
    + [("DPAD_LEFT", 0.15)] * 4
    + [("DPAD_DOWN", 0.6), ("DPAD_UP", 0.8)]
)


def adb(serial, *args, check=True):
    out = subprocess.run([ADB, "-s", serial, *args], capture_output=True, text=True)
    if check and out.returncode != 0:
        sys.exit(f"adb {' '.join(args)} failed: {out.stderr.strip()}")
    return "\n".join(l for l in out.stdout.splitlines() if "Init wrapper" not in l and "open mma dev" not in l)


def framestats(serial):
    """Кадры из framestats: dict IntendedVsync -> строка колонок (последние ~120 кадров)."""
    text = adb(serial, "shell", "dumpsys", "gfxinfo", PKG, "framestats")
    frames, header = {}, None
    in_data = False
    for line in text.splitlines():
        if line.startswith("---PROFILEDATA---"):
            in_data = not in_data
            header = None
            continue
        if not in_data:
            continue
        cols = line.strip().rstrip(",").split(",")
        if header is None:
            header = cols
            continue
        if len(cols) != len(header):
            continue
        row = dict(zip(header, (int(c) for c in cols)))
        if row["Flags"] != 0:  # пропускаем первые/служебные кадры
            continue
        frames[row["IntendedVsync"]] = row
    return frames


def summary_lines(serial):
    text = adb(serial, "shell", "dumpsys", "gfxinfo", PKG)
    keep = ("Total frames rendered", "Janky frames", "50th", "90th", "95th", "99th",
            "Number Missed Vsync", "Number High input latency", "Number Slow UI thread",
            "Number Slow bitmap uploads", "Number Slow issue draw commands", "Number Frame deadline missed")
    return [l.strip() for l in text.splitlines() if l.strip().startswith(keep)][:12]


def pct(values, p):
    if not values:
        return 0.0
    values = sorted(values)
    return values[min(len(values) - 1, int(round(p / 100 * (len(values) - 1))))]


def ms(ns):
    return ns / 1e6


def run(serial, runs, dbg=0):
    # Холодный старт: фокус на первой плитке, сетка наверху.
    adb(serial, "shell", "am", "start", "-S", "-W", "-n", ACTIVITY, "--ei", "dbg", str(dbg))
    time.sleep(4)
    adb(serial, "shell", "pkill", "-f", "com.android.commands.monkey", check=False)
    subprocess.Popen([ADB, "-s", serial, "shell", "monkey", "--port", str(PORT)],
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    adb(serial, "forward", f"tcp:{PORT}", f"tcp:{PORT}")
    sock = None
    for _ in range(40):
        try:
            candidate = socket.create_connection(("127.0.0.1", PORT), timeout=3)
            candidate.sendall(b"wake\n")
            # adb forward принимает соединение, даже если monkey ещё не слушает: ждём настоящий OK.
            if candidate.recv(64).startswith(b"OK"):
                sock = candidate
                break
            candidate.close()
        except OSError:
            pass
        time.sleep(0.5)
    if sock is None:
        sys.exit("monkey did not start")

    adb(serial, "shell", "dumpsys", "gfxinfo", PKG, "reset")
    frames = {}
    last_poll = time.time()
    for _ in range(runs):
        for key, pause in SCENARIO:
            sock.sendall(f"press {key}\n".encode()); sock.recv(64)
            time.sleep(pause)
            if time.time() - last_poll > 1.2:  # framestats хранит только ~120 кадров
                frames.update(framestats(serial))
                last_poll = time.time()
    time.sleep(1.0)
    frames.update(framestats(serial))
    summary = summary_lines(serial)
    sock.sendall(b"quit\n")
    sock.close()
    adb(serial, "forward", "--remove", f"tcp:{PORT}", check=False)
    adb(serial, "shell", "pkill", "-f", "com.android.commands.monkey", check=False)
    return list(frames.values()), summary


def report(frames, summary, label):
    total = [f["FrameCompleted"] - f["IntendedVsync"] for f in frames]
    ui = [f["SyncQueued"] - f["HandleInputStart"] for f in frames]
    rt = [f["SwapBuffers"] - f["SyncStart"] for f in frames]
    dq = [f["DequeueBufferDuration"] for f in frames]
    gpu = [f["GpuCompleted"] - f["SwapBuffers"] for f in frames if f.get("GpuCompleted", 0) > 0]
    late = sum(1 for t in total if t > VSYNC_NS)
    very_late = sum(1 for t in total if t > 2 * VSYNC_NS)
    print(f"=== {label}")
    print(f"frames {len(frames)}  late(>16.7ms) {late} ({100 * late / max(1, len(frames)):.0f}%)  "
          f">33ms {very_late} ({100 * very_late / max(1, len(frames)):.0f}%)")
    for name, vals in (("total", total), ("ui", ui), ("rt", rt), ("dequeue", dq), ("gpu", gpu)):
        print(f"  {name:8s} p50 {ms(pct(vals, 50)):6.1f}  p90 {ms(pct(vals, 90)):6.1f}  "
              f"p99 {ms(pct(vals, 99)):6.1f}  mean {ms(statistics.mean(vals)) if vals else 0:6.1f} ms")
    print("  gfxinfo: " + " | ".join(summary))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", default="10.100.102.13:5555")
    ap.add_argument("--runs", type=int, default=2)
    ap.add_argument("--label", default="run")
    ap.add_argument("--dbg", type=int, default=0, help="биты Debug.kt: что выключить в отрисовке")
    args = ap.parse_args()
    frames, summary = run(args.serial, args.runs, args.dbg)
    report(frames, summary, args.label)


if __name__ == "__main__":
    main()
