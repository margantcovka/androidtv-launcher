#!/bin/bash
# Реальная частота показа кадров лаунчера: листает плитки и смотрит интервалы показа
# в SurfaceFlinger. ~16.7 мс — панель на 60 Гц, ~20 мс — застряла на 50 Гц (Android при
# этом продолжает считать, что 60, и bench.py показывает почти все кадры опоздавшими).
# Лечится перезагрузкой телевизора.
SERIAL="${SERIAL:-10.100.102.13:5555}"
ADB="${ADB:-/opt/homebrew/share/android-commandlinetools/platform-tools/adb}"
LAYER='io.github.margantcovka.tvlauncher/io.github.margantcovka.tvlauncher.MainActivity#0'
"$ADB" -s "$SERIAL" shell dumpsys SurfaceFlinger --latency-clear >/dev/null 2>&1
for k in RIGHT RIGHT RIGHT RIGHT LEFT LEFT LEFT LEFT; do
  "$ADB" -s "$SERIAL" shell input keyevent "DPAD_$k" >/dev/null 2>&1
done
"$ADB" -s "$SERIAL" shell "dumpsys SurfaceFlinger --latency '$LAYER'" 2>/dev/null | python3 -c '
import sys
present = sorted(int(c[1]) for c in (l.split() for l in sys.stdin) if len(c) == 3 and 0 < int(c[1]) < 9e18)
gaps = sorted(d for d in ((b - a) / 1e6 for a, b in zip(present, present[1:])) if d < 200)  # без пауз между нажатиями
if not gaps:
    sys.exit("нет кадров: лаунчер не на экране?")
p50 = gaps[len(gaps) // 2]
print(f"кадров {len(gaps) + 1}, интервал p50 {p50:.1f} мс -> панель ~{1000 / p50:.0f} Гц")
'
