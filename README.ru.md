<p align="center">
  <img src="src/main/resources/assets/randomrun/textures/gui/logo.png" alt="RRM Network" width="360">
</p>

# RandomRun Mod

<p align="center">
  <a href="https://github.com/holodse-web/randomrunmod/releases/latest"><img src="https://img.shields.io/github/v/release/holodse-web/randomrunmod?color=9b59b6&amp;label=release" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Minecraft-26.3-62b54a" alt="Minecraft 26.3">
  <img src="https://img.shields.io/badge/loader-Fabric-dbd0b4" alt="Fabric">
  <img src="https://img.shields.io/badge/Java-25%2B-e58b36" alt="Java 25 or newer">
</p>

[English version](README.md)

**[Скачать последнюю версию](https://github.com/holodse-web/randomrunmod/releases/latest)** · [Что нового в 27.0](https://github.com/holodse-web/randomrunmod/releases/tag/v27.0) · [Telegram](https://t.me/rrmnetwork)

**RandomRun** — клиентский мод для Fabric (Minecraft **26.3**), который превращает выживание в гонку: вы получаете случайную цель и стараетесь добыть её быстрее часов или быстрее других игроков.

| Версия мода | Minecraft | Fabric Loader | Java |
| --- | --- | --- | --- |
| **27.0** | **26.3** | **0.19.5+** | **25+** |

## Режимы

### Одиночный спидран

- Случайная цель: **предмет** или **моб**, с анимацией слот-машины.
- Фильтр сложности пула предметов; можно включить или исключить недобываемые предметы.
- Свой сид мира для тренировок и честных сравнений.
- Точный таймер, история забегов с фильтрами и личные рекорды.
- **RRM Upgrader** (новое): поставьте до 10 предметов (не более 3 разных типов) на колесо шанса и получите более ценную вещь. Ценность ставки и цели считается по ценам предметов, исход выпадает один раз на стороне сервера мира. Клавиша **U** (меняется в настройках) во время забега.
- **Random Drop**: из блоков выпадают случайные предметы. Такие забеги в таблицу лидеров не идут.

### Битва

- От 2 до 6 игроков, общий отсчёт, одна цель, индикаторы готовности.
- **Раздельные миры**: один сид, разные миры, побеждает самый быстрый.
- **Общий мир**: все в мире хоста через [e4mc](https://modrinth.com/mod/e4mc) или Radmin VPN; побеждает последний выживший или первый нашедший цель.
- Приватные комнаты с паролем и публичная очередь.
- Живые уведомления, когда соперники получают достижения или погибают.

### Онлайн-профиль (по желанию)

- Аккаунт с паролем, глобальная таблица лидеров и последние забеги.
- Античит Trust Score: честные забеги повышают его, читы, творческий режим и открытие в LAN понижают.
- Всё работает и в **оффлайн-режиме** без профиля.

## Интерфейс и настройки

- Переработанные меню с анимациями, частицами и стилизованными кнопками.
- Редактор HUD перетаскиванием, два стиля HUD и два стиля интерфейса.
- Экраны победы, поражения и ничьей с кнопкой **Повтор** (тот же сид и цель).
- Старт командой `/go` или выбранной клавишей, автоудаление завершённых миров.
- Настройки звука и музыки, AFK-экран, встроенная проверка версии.
- Языки: English, Русский, Українська.

## Что нового в 27.0

[Полное описание релиза →](https://github.com/holodse-web/randomrunmod/releases/tag/v27.0)

Главное: перенос на Minecraft 26.3, RRM Upgrader, исправление пароля приватных комнат, более надёжная проверка версии, оптимизация отрисовки.

## Установка

1. Установите Minecraft **26.3** и [**Fabric Loader 0.19.5+**](https://fabricmc.net/use/installer/) (Java 25 и новее).
2. Скачайте [**Fabric API** для 26.3](https://modrinth.com/mod/fabric-api/versions?g=26.3) и положите в папку `mods`.
3. Для битв в общем мире установите [**e4mc** для 26.3](https://modrinth.com/mod/e4mc/versions?g=26.3).
4. Скачайте [`RandomRun-26.3Fabric-27.0.jar`](https://github.com/holodse-web/randomrunmod/releases/latest/download/RandomRun-26.3Fabric-27.0.jar), положите в папку `mods` и удалите старые версии RandomRun.

В разделе [Releases](https://github.com/holodse-web/randomrunmod/releases/latest) также доступен файл `SHA256SUMS.txt` для проверки загрузки. Архивы **Source code** содержат исходники; для игры нужен JAR из списка Assets.

## Онлайн-функции и данные

В онлайн-режиме мод отправляет на свой сервер: ник, случайный UUID игрока, версию мода, Trust Score и флаги, хеш аппаратного идентификатора (используется античитом) и результаты забегов. Установленные моды и ресурспаки сканируются локально на известные читерские инструменты; результат влияет на Trust Score. Чтобы ничего не отправлять, выберите **Играть оффлайн** в главном меню.

## Сборка из исходников

Gradle, Java 25. Данные подключения к серверу в репозиторий не входят: скопируйте `src/main/java/com/randomrun/network/FirebaseClient.java.example` в `FirebaseClient.java` и подставьте свои адреса.

В PowerShell из корня проекта:

```powershell
.\gradlew.bat assemble             # обычный JAR

.\gradlew.bat verifyObfuscatedJar  # сборка после ProGuard и проверка байткода

```

Готовый релиз: `build/obfuscated/release/`. Файл `mapping.txt` сохраните локально для разбора ошибок.

## Ссылки

Telegram: https://t.me/rrmnetwork

## Лицензия

Copyright (c) 2026 Stanislav Kholod. Все права защищены. Исходный код опубликован для ознакомления; изменение и распространение изменённых версий запрещены. Использование в модпаках разрешено с указанием авторства. См. [LICENSE](LICENSE).
