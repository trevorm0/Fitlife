# FitLife

A local-first Android app (Kotlin + Jetpack Compose) that combines workouts, nutrition, medication and appointment reminders, and health tracking. Data stays on the device; cloud backup is optional.

## How it works

**Accounts.** You register and sign in on the device. Only a salted PBKDF2 hash of the password is stored. Each account gets its own Room database file (`fitlife_<id>.db`), so accounts never see each other's data. There is no password recovery.

**Storage.** Structured data (workouts, meals, goals, medications, appointments, steps, pulse, etc.) lives in Room. Settings and targets live in DataStore. On first sign-in the app seeds a built-in library of six bodyweight workouts.

**App structure.** Screens (Compose) talk to ViewModels, which use repositories over the Room DAOs. Dependencies are wired manually in `FitLifeApp`. Navigation has five tabs: Home, Workouts, Health, Nutrition, Profile. Detail screens hide the bottom bar.

**Workouts.** Pick a workout, then the active-workout screen steps through its exercises with a timer (pause, skip, end). Finished sessions are saved to history and feed your stats and streak.

**Nutrition and goals.** You log meals; calorie and macro totals are computed from them against your targets. Goals have a start, target and current value, and progress is calculated from those.

**Medication and appointments.** Each item has one or more reminders. Alarms are scheduled with `AlarmManager` (exact when permitted, otherwise inexact) and rebuilt after reboot, app update, time or time-zone change, and every app start. Taken / skipped / missed is recorded only when you tap it, never by a notification.

**Health.** Steps come from the device step sensor. Optionally, steps, heart rate, sleep and active calories are read from Health Connect (read-only, per-permission). Missing data is shown as empty, never invented.

**Feature modules** (`features/`), all on-device:
- **Pulse:** CameraX captures the fingertip over the flash for 30 s; the green-channel signal is filtered and peak-detected to get BPM. Weak signals are rejected.
- **Form coach:** CameraX frames go to ML Kit Pose Detection; rule-based joint-angle checks count reps and give cues for squat, push-up, plank and lunge. Video is never stored.
- **Meal photo:** ML Kit labels the image, then `FoodMapper` maps labels to catalog foods with calories and macros. You adjust portions and save to your meal log. Photos are discarded.
- **Leagues:** private invite-code leagues with weekly challenges and configurable scoring; members choose what to share.
- **Adaptive recommendations:** combines steps, workouts, meals, pulse and goals into explainable suggestions.

**Cloud backup (optional).** Off unless you add `google-services.json`, enable it in Settings, sign in, and tap Back up now. It uploads a full snapshot to Firestore; restoring replaces local data.

## Run it

Open the folder in Android Studio (JDK 17, SDK 35), sync Gradle, and run `app` on a device or emulator (API 26+). Use a physical device for camera, step sensor and Health Connect. Run tests with `./gradlew test`.

*FitLife is a wellness tool, not a medical device.*
