# X-KeepAlive

<p align="center">
  <img src="x-keepalive-icon.png" alt="X-KeepAlive logo" width="180">
</p>

> Android utility to help keep selected games active in the background and reduce unnecessary reloads when switching apps. Requires Shizuku for privileged background policies; no root required.

[**Download the latest APK**](https://github.com/layiix11/X-KeepAlive/releases/latest) · [Releases](https://github.com/layiix11/X-KeepAlive/releases)

Private Android app that follows a game you choose and applies a background policy **only** when Shizuku is running and authorized. It does not play for you, does not simulate taps, and does not require root.

Package: `app.xkeepalive`  
Minimum: Android 8 (API 26)  
Target: Android 15 (API 35)

## What it can do

An app cannot reuse an ADB pairing on its own. Wireless debugging authorizes the client you paired (a PC or Shizuku), not every installed app. X-KeepAlive does not open an ADB socket.

Shizuku, started by you with wireless debugging, exposes a shell service (uid 2000). With your permission the app runs only these documented commands, then **reads the result back**:

| Command | Effect | Check |
|---|---|---|
| `cmd deviceidle whitelist +package` | The game is added to the Doze whitelist. If that is not enough, it retries with `dumpsys deviceidle whitelist +package`. | `cmd deviceidle whitelist` |
| `am set-standby-bucket package active` | Sets the standby bucket to active, unless it is already `exempted`. | `am get-standby-bucket` |
| `am set-inactive package false` | The game is not marked idle. This alone does not keep the process alive. | `am get-inactive` |
| `cmd appops set package RUN_IN_BACKGROUND allow` | Tries to lift the pre-O background restriction. | `cmd appops get` |
| `cmd appops set package RUN_ANY_IN_BACKGROUND allow` | Same attempt for the general restriction. On recent Android versions the system may ignore it. | `cmd appops get` |
| `pidof package` | Reads the game's PIDs. | exit 0 with a PID, or exit 1 if none |

**Background management active** appears only when the whitelist, bucket, exemption, or an appop is actually confirmed. X-KeepAlive's own foreground service is never described as "the game kept alive" by itself.

Without Shizuku the app stays in standard mode: foreground detection, a local log, a notification, and shortcuts to battery settings. No privileged commands.

## What it cannot do

- Stop the game from reloading if the activity loses its graphics surface, even while the process is still in memory.
- Stop Android from killing the process under memory pressure.
- Raise `oom_score_adj` or use hidden APIs. That is not part of this app.
- Start, close, or reopen the game, or simulate input.
- See the ADB pairing while Shizuku is stopped.
- Restart wireless debugging or Shizuku after a reboot. You start those again yourself.

When you return to the game the app compares PIDs:

- same PIDs: the process is still in memory; the game can still reload;
- missing PIDs: Android killed the process;
- new PIDs: the process was recreated;
- Shizuku missing: the PID cannot be checked, and the log says so.

## Architecture

- `policy-core`: state machine, allowed commands, and parsers. No Android dependency, covered by JVM tests.
- `app`: Compose, DataStore, UsageStats, a foreground service, a Shizuku UserService, and optional AdMob.

The shell service rejects any command outside the allowlist, including `am start`, `input`, and shell metacharacters.

Modules: Compose UI, detection (`ForegroundAppDetector`), service (`MonitorService`), Shizuku (`ShizukuBridge`, `BackgroundPolicyExecutor`, `ShellUserService`), permissions (`PermissionGateway`), DataStore settings, and a local log.

## Build

You need JDK 17 or 21, Android SDK 35, and Build-Tools 35.

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :policy-core:test assembleDebug
```

The latest installable APK is available from the [GitHub Releases page](https://github.com/layiix11/X-KeepAlive/releases/latest). A locally built debug APK is signed with the debug certificate and can be found at `app/build/outputs/apk/debug/app-debug.apk`.

Ads use the test IDs in `ads.properties`. Replace those two values only if you want your own AdMob account. AdMob starts only if you turn ads on in settings.

## Install the APK

Download the APK from [X-KeepAlive Releases](https://github.com/layiix11/X-KeepAlive/releases/latest), then install it on your phone (you may need to allow installation from this source) or with ADB.

With the phone already paired for wireless debugging:

1. Developer options → Wireless debugging → note the connection IP and port (not the pairing port).
2. From the PC: `adb connect ADDRESS:PORT`
3. `adb install -r x-keepalive-1.0.2.apk` (use the actual downloaded APK filename).

In Android Studio: **Open** this repository folder, then Run.

## First launch

1. Open X-KeepAlive and choose a game from the list.
2. Grant usage access to X-KeepAlive.
3. Allow the notification: the foreground service has to stay visible.
4. Optional: set X-KeepAlive's battery to unrestricted, and the game's battery to Unrestricted.
5. If you want the ADB policy, start Shizuku and tap **Authorize**. The permission is not requested on its own.
6. Turn the switch on once, then open the game. Closing the UI does not stop monitoring, within Android's limits.

To stop: use the switch or the **Stop** action on the notification. The applied policy is revoked if Shizuku is still reachable.

## Wireless debugging and Shizuku

1. Settings → About phone → tap Build number seven times.
2. Developer options → turn on Wireless debugging.
3. In Wireless debugging open **Pair device with pairing code**.
4. In the Shizuku app choose **Start via wireless debugging** and enter the code.
5. When Shizuku is running, return to X-KeepAlive and authorize it.

After a reboot, wireless debugging and Shizuku are off. If boot restore is enabled, X-KeepAlive can restart only its own monitoring, and only if usage access is still granted. The log warns that Shizuku must be started again by hand.

The in-app guide repeats these steps.

## Tests

Automated checks run in the development environment:

- `:policy-core:test`: 18 tests, 0 failures. They cover states, PIDs, the allowlist, and parsers.
- `:app:assembleDebug`: APK produced.

**Device testing:** X-KeepAlive has also been tested on a physical Android phone. Results can vary by Android version, device manufacturer, battery settings, memory pressure, and the game itself; this is not a guarantee that every game will remain active or avoid reloading.

On the phone use **Options → Verification protocol**. You record the outcome yourself, and it stays in the local log. The app does not invent a result.

## Privacy

Everything stays in DataStore and in `event-log.jsonl` on the device. Cloud backup is off. There is no account and no app server. The app list is not uploaded. Internet is declared only because AdMob, if you enable it, contacts Google. Ads do not appear over the game: the banner is only on the home screen, and only while the toggle is on.
