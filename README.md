# RoboCam

**RoboCam** turns an Android phone or tablet into a camera and control server for a LEGO Mindstorms EV3 robot (or a fully custom robot): it serves an HTML5 control client over Wi-Fi, streams the (optionally processed) camera view and drives the robot via Bluetooth. The web client is a separate repository: **RoboCam-HTML5-Client**.

---

RoboCam — Android-приложение, превращающее телефон или планшет в камеру и сервер управления для робота.

- **Встроенный сервер.** HTTP + WebSocket (библиотека Webbit) раздаёт HTML5-клиент и принимает команды управления.
- **Видео с камеры.** Кадры обрабатываются (в flavor-версии — быстрая обработка через RenderScript) и транслируются браузерам подключённых устройств, вместе с кешированием для нескольких клиентов.
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

## Версии приложения (flavors)

- **withoutRenderScript** — чисто Java-обработка кадра, работает начиная с Android 2.3 (minSdk 9)
- **withRenderScript** — обработка изображения через RenderScript (SDK 21+), быстрее

## Сборка

- Android Studio 2.3.x (Android Gradle Plugin 2.3.3)
- compileSdk 25, buildTools 26.0.0, minSdk 9, targetSdk 25
- Собирается штатно: `./gradlew assembleDebug` (или сборка flavor'ов в Android Studio)
