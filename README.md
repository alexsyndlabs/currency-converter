# Конвертер валют — Android (release)

Курсы ЦБ РФ (`cbr-xml-daily.ru`), 14 валют, кэш и работа без сети.
Kotlin, minSdk 24 (Android 7.0+), targetSdk 34, R8-минификация, подписан релизным ключом.

## Получить APK

### GitHub Actions — 3 минуты, ничего ставить не нужно
1. Залить папку в репозиторий (файл `release.jks` тоже, он нужен для подписи).
2. Actions → Build release APK → Run workflow.
3. APK появится в двух местах: артефакт `konverter-valyut-release` и раздел Releases.

Файл: `konverter-valyut-release-1.0.apk` — подписанный, готов к установке.

### Локально (Android Studio)
Open → папка проекта → Build → Generate Signed Bundle/APK, либо `./gradlew assembleRelease`.
Результат: `app/build/outputs/apk/release/`.

## Установка
Скинуть APK на телефон → открыть → разрешить установку из этого источника.
Play Protect может показать предупреждение о неизвестном разработчике — это нормально для APK вне Google Play.

## Ключ подписи
`release.jks` в корне проекта. Alias `converter`, пароль хранилища и ключа `converter2026`, срок 30 лет.
Ключ **нельзя терять**: обновления приложения принимаются только при совпадении подписи.
Для публикации в Google Play пароли лучше вынести в `keystore.properties` / GitHub Secrets, а сам `.jks` из репозитория убрать.

Сменить пароль:
```bash
keytool -storepasswd -keystore release.jks
keytool -keypasswd -alias converter -keystore release.jks
```

## Структура
```
app/src/main/java/cloud/xvps/converter/
  MainActivity.kt   — UI, пересчёт, свап валют
  Rates.kt          — курсы ЦБ, кэш в SharedPreferences, оффлайн-резерв
app/src/main/res/   — layout, строки, тема, иконки
release.jks         — ключ подписи
```

## Расчёт
Курсы приводятся к рублю с учётом номинала:
`результат = сумма × (руб_за_1_из) / (руб_за_1_в)`

## Добавить валюту
В `Rates.kt`: код в `CODES`, название в `NAMES`, символ в `SYMBOLS`, резерв в `FALLBACK`.
Код должен быть в справочнике ЦБ РФ.

## Версия
Поднимать `versionCode` и `versionName` в `app/build.gradle.kts` перед каждой новой сборкой,
иначе телефон откажется ставить обновление поверх.
