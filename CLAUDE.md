# CLAUDE.md

This file provides guidance to Claude Code when working with this repository.

## Project Overview

**kick-stream** is a lightweight Android TV application for streaming Kick.com content.
3 screens: Login (OAuth QR code), Home (followed channels), Player (HLS stream + chat sidebar).

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose for TV (`androidx.tv:tv-material`)
- **Build:** Gradle 8.11.1, Kotlin DSL, Version Catalog (`gradle/libs.versions.toml`)
- **Video:** Media3 ExoPlayer with HLS
- **Chat:** Pusher Java Client (WebSocket)
- **Network:** Retrofit + OkHttp + kotlinx-serialization
- **Auth:** OAuth 2.1 PKCE via id.kick.com
- **Storage:** DataStore Preferences
- **DI:** None (manual singletons in `NetworkModule`)

## Build & Development Commands

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew assembleRelease      # Build release APK (minified with R8)
./gradlew test                 # Run all unit tests
./gradlew test --tests "com.kickstream.util.PkceUtilTest"  # Single test
./gradlew clean                # Clean build artifacts
```

## Setup

1. Install Android SDK (API 35)
2. Set `sdk.dir` in `local.properties`
3. Register an app at dev.kick.com
4. Add `kick.client.id`, `kick.client.secret`, `kick.redirect.uri` to `local.properties`

## Architecture

```
MainActivity (single Activity)
└─ Compose NavHost
    ├─ LoginScreen    → LoginViewModel  → AuthRepository
    ├─ HomeScreen     → HomeViewModel   → ChannelRepository → KickApi
    └─ PlayerScreen   → PlayerViewModel → ChannelRepository + ChatRepository
```

- **data/api/** — Retrofit interfaces + DTOs
- **data/repository/** — Business logic (auth, channels, chat)
- **data/chat/** — Pusher WebSocket client
- **data/local/** — DataStore token persistence
- **ui/** — Compose screens + components
- **navigation/** — NavHost routes
- **util/** — PKCE + QR code helpers

## Key Patterns

- ViewModels use `AndroidViewModel` for Application context (needed for DataStore)
- Network singletons live in `NetworkModule` (no DI framework)
- Chat uses `callbackFlow` to bridge Pusher callbacks → Kotlin Flow
- Chat messages capped at 200 to prevent memory growth
- Unofficial Kick API endpoints isolated in repository layer for easy swapping
