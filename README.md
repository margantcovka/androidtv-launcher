# Android TV Launcher (в стиле Apple TV)

Домашний экран для Android TV / Google TV в стиле tvOS. Идея и дизайн взяты у
[HarshInvader/webos-launcher](https://github.com/HarshInvader/webos-launcher) (MIT) — это
такой же лаунчер для LG webOS. Код здесь свой: нативный Kotlin + Jetpack Compose for TV.

## Что уже есть (v0.1)

- **Top shelf**: размытый фон выбранного приложения на весь верх и его название крупно.
  Фон размывается один раз на CPU в картинку 64×36 и просто растягивается — без живого
  blur, чтобы не тормозить на слабом GPU телевизора.
- **Сетка tvOS**: широкие скруглённые плитки по 5 в ряд. Плитка в фокусе увеличивается,
  отбрасывает тень, по ней проходит блик; подпись видна только у неё.
- Переход ниже первой строки поднимает сетку поверх top shelf; Back или повторный Home
  возвращают в начало.
- **Список приложений** берётся из системы (`LEANBACK_LAUNCHER`), с родными баннерами
  320×180. Если баннера нет, квадратная иконка ставится посередине, а края плитки
  заливаются цветами краёв иконки.
- Запуск приложения по OK. Список перечитывается при каждом возврате на экран.
- В манифесте есть `HOME`, поэтому там, где прошивка это разрешает, лаунчер можно выбрать
  домашним экраном.

## Что дальше

1. **Как стать домашним экраном** — зависит от телевизора (ждём модель):
   - старый Android TV: просто выбрать лаунчер по умолчанию;
   - Google TV: отключить стандартный лаунчер через ADB
     (`pm disable-user --user 0 com.google.android.apps.tv.launcherx`, откат — `pm enable`)
     или перехватывать Home через службу спецвозможностей (так делает Projectivy;
     обновления Google TV это ломали).
2. **Настройки**: порядок плиток, скрытие приложений, «hold OK» для перестановки.
3. **Источники**: Live TV и HDMI-входы через `TvInputManager`. Переключение на вход
   зависит от производителя — проверять на реальном телевизоре.
4. Шрифт Inter Display (ближайший открытый аналог SF Pro).

## Сборка

Нужны JDK 21 и Android SDK (на этом Маке — Homebrew: `openjdk@21`,
`android-commandlinetools` в `/opt/homebrew/share/android-commandlinetools`).

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # минифицированный, пока подписан debug-ключом
```

`local.properties` (не в git) указывает `sdk.dir`.

## Установка на телевизор

На телевизоре: «Настройки → Об устройстве», 7 раз нажать на номер сборки → появятся
«Для разработчиков» → включить «Отладку по USB/сети». Потом с Мака:

```sh
ADB=/opt/homebrew/share/android-commandlinetools/platform-tools/adb
$ADB connect <IP телевизора>:5555     # на телевизоре подтвердить запрос
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell am start -n io.github.margantcovka.tvlauncher/.MainActivity
```

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
  MainActivity.kt        загрузка списка на onResume, Home-нажатия
  data/AppRepository.kt  список TV-приложений, баннеры/иконки, запуск
  data/Images.kt         размытый фон top shelf, цвета краёв иконки
  data/TvApp.kt          модель плитки
  ui/HomeScreen.kt       top shelf + сетка, логика прокрутки
  ui/Tile.kt             плитка: масштаб, тень, блик, подпись
  ui/Tokens.kt           цвета, размеры, тайминги
```
