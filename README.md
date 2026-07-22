# Roshambo for Android and Seeker

Roshambo is a camera-native rock-paper-scissors game. The Android client uses CameraX and MediaPipe to recognize real hand gestures on-device, and integrates Solana Mobile Wallet Adapter on devnet for wallet connection and score publishing.

This repository contains only the Android/Seeker client. The iOS client, production Firebase backend, anti-abuse logic, and production data are private.

## Features

- Native Kotlin and Jetpack Compose UI
- CameraX live camera pipeline
- MediaPipe 21-landmark hand tracking
- On-device rock, paper, and scissors classification
- Local game, score, XP, levels, and streaks
- Asynchronous friend-challenge client using Firebase Authentication and callable Cloud Functions
- Solana Mobile Wallet Adapter connection and transaction signing on devnet
- Devnet SOL and test-token balance display
- Wallet-signed score publishing through an SPL Memo transaction

## Requirements

- Android Studio with Android SDK 36
- JDK 17
- An Android device or emulator with camera support
- An MWA-compatible wallet for Solana features
- A Firebase Android app and compatible callable backend for friend challenges

## Setup

1. Clone the repository.

2. Download Google's official MediaPipe Hand Landmarker model:

   ```bash
   mkdir -p app/src/main/assets
   curl -L --fail \
     https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task \
     -o app/src/main/assets/hand_landmarker.task
   ```

   Expected SHA-256:

   ```text
   fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1
   ```

3. Copy the Firebase example and replace every placeholder with values from your own Firebase Android app:

   ```bash
   cp app/google-services.json.example app/google-services.json
   ```

4. Build and test:

   ```bash
   ./gradlew test lint assembleDebug
   ```

The app can run local camera rounds without a production backend. Friend challenges require callable Cloud Functions compatible with the request and response types used by the client.

## Privacy

Camera frames and hand landmarks are processed on-device. The client does not upload or store camera video or photographs. Online features submit game state and classified moves, not raw camera media.

## Project status

The repository currently targets Solana devnet. The SKR balance uses a development test mint and must not be presented as production SKR. Production SIWS, SGT verification, .skr identity, and mainnet campaign logic remain grant roadmap items.

## License

Roshambo's Android/Seeker client is licensed under the Apache License 2.0. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
