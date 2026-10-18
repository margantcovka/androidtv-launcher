# Android TV Launcher (в стиле Apple TV)

Домашний экран для Android TV / Google TV в стиле tvOS. Идея и дизайн взяты у
[HarshInvader/webos-launcher](https://github.com/HarshInvader/webos-launcher) (MIT) — это
такой же лаунчер для LG webOS. Код здесь свой: нативный Kotlin + Jetpack Compose for TV.

![Домашний экран на телевизоре](docs/home.png)

## Что есть

- **Top shelf**: размытый фон выбранной плитки на весь верх и её название крупно.
  Фон размывается один раз на CPU в картинку 64×36 и просто растягивается — без живого
  blur, чтобы не тормозить на слабом GPU телевизора.
- **Сетка tvOS**: широкие скруглённые плитки по 5 в ряд. Плитка в фокусе увеличивается,
  отбрасывает тень, по ней проходит блик; подпись видна только у неё.
- Переход ниже первой строки поднимает сетку поверх top shelf; Back или повторный Home
  возвращают в начало.
- **Три раздела**: приложения (`LEANBACK_LAUNCHER`, родные баннеры 320×180, без баннера —
  иконка по центру на цветах её краёв), «Источники» (эфирное ТВ и HDMI/AV-входы из
  `TvInputManager`; отключённые входы приглушены) и «Ещё» (настройки главного экрана,
  системные настройки, стандартный экран).
- Переключение на вход — стандартный интент `TvContract.buildChannelUriForPassthroughInput`.
- **Удержание OK** на плитке: «Переместить» (стрелки двигают плитку внутри раздела,
  OK сохраняет, Back отменяет), «Скрыть с главного экрана», «О приложении».
- **Настройки главного экрана** (плитка «Главный экран»): показать или скрыть любую плитку,
  вернуть порядок по алфавиту.
- Шрифт Inter Display (OFL, `licenses/Inter-OFL.txt`) — ближайший открытый аналог SF Pro.
  Иконки — Material Icons (Apache 2.0).

## Домашний экран по кнопке Home

На Android TV 11 стандартный лаунчер — системное приложение с повышенным приоритетом на
`HOME`, поэтому выбор «по умолчанию» на него не действует. Работает так (через ADB):

```sh
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell cmd package set-home-activity io.github.margantcovka.tvlauncher/.MainActivity
```

Откат:

```sh
adb shell pm enable com.google.android.tvlauncher
```

Без компьютера: плитка «Ещё → Стандартный экран», пока стандартный лаунчер отключён,
открывает его страницу в настройках — там кнопка «Включить».
На Google TV пакет другой: `com.google.android.apps.tv.launcherx`.

## Сборка

Нужны JDK 21 и Android SDK (на этом Маке — Homebrew: `openjdk@21`,
`android-commandlinetools` в `/opt/homebrew/share/android-commandlinetools`).

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # минифицированный, пока подписан debug-ключом
```

`local.properties` (не в git) указывает `sdk.dir`.

## Плавность на слабом телевизоре

Целевой ТВ — AIPONT (Changhong, MediaTek M7322): Mali-470 (GLES 2.0), 4×A55 1.4 ГГц, 1.3 ГБ RAM.
Что дало ~60 кадров/с (замер `tools/bench.py`: было ~99% опоздавших кадров, стало ~3%):

- скруглённые плитки и тень готовятся картинками заранее — GPU не обрезает по форме и не считает тени;
- фон всего экрана — одна непрозрачная картинка 64×36, смешиваемая на CPU (смена приложения,
  затухание полки); фон окна отключён — нет лишней полноэкранной заливки;
- анимации читаются в фазе отрисовки и не перекомпоновывают экран;
- **предкомпиляция** `cmd package compile -m speed -f` после установки (иначе JIT ест ~30% CPU
  во время анимаций) — это делает `tools/install.sh`.

Проверено и отвергнуто: окно RGB_565 и маленькое окно не ускоряют композитор; ровные 30 кадров/с
(через свои часы кадров) владельцу не понравились.

`tools/cadence.sh` показывает реальную частоту панели: однажды она застряла на 50 Гц при
60 Гц в Android (почти все кадры «опаздывали» даже без отрисовки); помогла перезагрузка ТВ.

`tools/bench.py --dbg N` выключает части отрисовки (биты в `Debug.kt`), чтобы померить цену каждой.

## Установка на телевизор

На телевизоре: «Настройки → Об устройстве», 7 раз нажать на номер сборки → появятся
«Для разработчиков» → включить «Отладку по USB/сети». Потом с Мака:

```sh
SERIAL=<IP телевизора>:5555 tools/install.sh   # сборка, установка, предкомпиляция, запуск
```

В первый раз на телевизоре нужно подтвердить запрос «Разрешить отладку».

## Эмулятор

Есть AVD `gtv36` (Google TV, Android 16, 1080p):

```sh
SDK=/opt/homebrew/share/android-commandlinetools
$SDK/emulator/emulator -avd gtv36 -no-audio &        # с -no-window — без окна
$SDK/platform-tools/adb exec-out screencap -p > shot.png
$SDK/platform-tools/adb shell input keyevent DPAD_RIGHT DPAD_CENTER
```

## Устройство кода

```
app/src/main/java/io/github/margantcovka/tvlauncher/
  MainActivity.kt              экраны (главный / настройки), загрузка на onResume, Home
  data/LauncherRepository.kt   все плитки: приложения + источники + «Ещё»
  data/AppSource.kt            TV-приложения, баннеры/иконки (кэш по lastUpdateTime)
  data/InputSource.kt          эфирное ТВ и HDMI/AV-входы
  data/SymbolTiles.kt          плитки с иконкой и подписью (входы, «Ещё»)
  data/LauncherPrefs.kt        порядок и скрытые плитки (SharedPreferences), Catalog
  data/Launcher.kt             что делает плитка по OK
  data/Images.kt               скруглённые плитки, тень, размытый фон top shelf
  ui/HomeScreen.kt             top shelf + сетка по разделам, меню, перестановка
  ui/Tile.kt                   плитка: масштаб, тень, блик, подпись
  ui/TileMenu.kt, FocusRow.kt  меню по удержанию OK, строки в стиле tvOS
  ui/SettingsScreen.kt         настройки главного экрана
  ui/ShelfRenderer.kt          смешивание фона top shelf на CPU
  ui/Tokens.kt                 цвета, размеры, тайминги
```
