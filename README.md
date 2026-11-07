# RoboCam

**RoboCam** turns an Android phone or tablet into a camera and control server for a LEGO Mindstorms EV3 robot (or a fully custom robot): it serves an HTML5 control client over Wi-Fi, streams the (optionally processed) camera view and drives the robot via Bluetooth. The web client is a separate repository: **RoboCam-HTML5-Client**.

---

RoboCam — Android-приложение, превращающее телефон или планшет в камеру и сервер управления для робота.

- **Встроенный сервер.** HTTP + WebSocket (библиотека Webbit) раздаёт HTML5-клиент и принимает команды управления.
- **Видео с камеры.** Кадры обрабатываются на Java (Camera2 API на Android 6+, устаревший Camera API на более старых версиях) и транслируются браузерам подключённых устройств, вместе с кешированием для нескольких клиентов.
- **Драйвер EV3.** Управление роботом LEGO Mindstorms EV3 по Bluetooth — свой драйвер байт-кода EV3, включая загрузку пользовательских программ на кирпич.
- **Драйвер «нестандартного робота».** Экранный пульт собирается прямо в приложении: группы клавиш, спиннеры, поведение портов — настройки роботов сохраняются, импортируются и экспортируются.
- **Локальный пульт.** Управление роботом можно вести с самого телефона — с физической клавиатуры Android-устройства.
- **Локализация:** русский, английский, казахский.
- **Баннеры.** Поддержка отображения баннеров в клиенте (свои HTML5-баннеры или AdMob).

Подключение клиента: запустите RoboCam на телефоне, откройте браузер на другом устройстве той же Wi-Fi-сети (или на телефоне) и перейдите по адресу, указанному в настройках сервера приложения.

## Статьи

Серия статей о RoboCam: [proghouse.ru/tags/robocam](http://www.proghouse.ru/tags/robocam)

## Клиент (HTML5)

Веб-клиент написан в Construct 2 и выложен отдельным репозиторием: [RoboCam-HTML5-Client](https://github.com/zMotoR/RoboCam-HTML5-Client).

## Совместимость

- Android 4.4 (API 19) и новее — один APK, flavor'ов больше нет.
- targetSdk 36 — требование Google Play для новых версий с 31 августа 2026 года.
- На Android 4.4–5.1 (API 19–20) включён legacy multidex: приложение вместе с AndroidX и рекламным SDK перебирает лимит 64K методов, а нативной поддержки multidex там нет.
- RenderScript больше не используется, обработка кадра — чистый Java. Исходники `yuv420888.rs` и `RSHelper.java` лежат в `app/src/main/rs2`, но в сборку не входят.

## Сборка

- JDK 21 — путь задан в `gradle.properties` (`org.gradle.java.home`).
- Gradle 9.6.0, Android Gradle Plugin 9.4.0.
- compileSdk 36, targetSdk 36, minSdk 19; текущая версия — 1.5.1 (versionCode 43).
- Зависимости: AndroidX AppCompat 1.6.1, AndroidX Multidex 2.0.1, Google Mobile Ads 22.6.0, Webbit 0.4.15.
- Debug: `gradlew.bat assembleDebug` (в Linux/macOS — `./gradlew assembleDebug`).
- Release: `gradlew.bat assembleRelease` — сжатие и обфускация через R8 (`proguard-android-optimize.txt` плюс `-dontoptimize` в `app/proguard-rules.pro`); APK на выходе не подписан.

Если домашний каталог пользователя содержит не-ASCII символы (например, кириллицу), задайте `GRADLE_USER_HOME` на путь из ASCII: генератор AIDL вписывает имя каталога в шапку сгенерированного файла, и `javac` падает на кодировке.

## Для разработчиков

Соглашения по репозиторию, карта кода, жёсткие ограничения сборки и правила публикации — в [AGENTS.md](AGENTS.md).
