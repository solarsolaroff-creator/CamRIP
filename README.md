# CamRIP

CamRIP — Android-видеоредактор на Kotlin и Jetpack Compose.

## Возможности

- Создание и редактирование проектов с несколькими клипами.
- Эффекты, фигуры/оверлеи, 3D-модели (SceneView).
- Ключевые кадры для анимации параметров.
- Экспорт видео через Media3 Transformer.
- Локальное хранение проектов (без аккаунтов и сервера).
- Настройки темы, акцента, стиля UI, шрифта и иконки приложения.

## Стек

- Kotlin, Jetpack Compose, Material 3
- Media3 (ExoPlayer, Effect, Transformer)
- SceneView
- Navigation Compose
- DataStore Preferences
- Gradle Kotlin DSL

## Сборка

Требования:

- Android Studio / Android SDK
- JDK 17
- Android SDK Platform 34

Сборка debug APK:

```bash
./gradlew assembleDebug
```

APK будет в `app/build/outputs/apk/debug/`.
