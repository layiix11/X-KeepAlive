# X-KeepAlive

<p align="center">
  <img src="x-keepalive-icon.png" alt="X-KeepAlive logo" width="180">
</p>

> Android utility that helps selected games remain in the background longer when switching between apps. Uses Shizuku for supported background policies; no root required.

[**Download X-KeepAlive 1.0.3**](https://github.com/layiix11/X-KeepAlive/releases/tag/1.0.3) · [All releases](https://github.com/layiix11/X-KeepAlive/releases)

## Features

- Applies supported background policies through Shizuku, without root.
- Monitors the selected app and records events in a local log.
- Saves the original Android settings before changing them and attempts to restore those values when monitoring is stopped.
- Provides standard mode without Shizuku, with foreground detection, local logging, notifications, and shortcuts to Android settings.

Shizuku must be started and authorized by the user. X-KeepAlive does not open an ADB socket or restart Shizuku after a reboot.

## Background policy and restoration

Before changing a setting, X-KeepAlive reads the current state of the Doze whitelist, standby bucket, idle state, and the `RUN_IN_BACKGROUND` and `RUN_ANY_IN_BACKGROUND` AppOps modes. It stores the values it successfully reads.

If a read fails, that setting is not changed. When monitoring is stopped, the app attempts to restore the saved values rather than forcing AppOps to `default`. An item already present in the Doze whitelist is not removed.

If Shizuku becomes unavailable, a command is not confirmed, or restoration is incomplete, the local log reports the failure and the saved record is retained. X-KeepAlive does not modify another package while a previous package still has an unrestored policy.

Restoration depends on Shizuku remaining available and Android accepting the commands. Review the app's status and local log for any reported failure.

## Limitations

X-KeepAlive cannot guarantee that Android will keep a process in memory. Android may terminate an app under memory pressure, and a game may reload even if its process remains alive, for example if its activity or graphics surface is recreated.

The app does not:
- Start, close, or reopen games.
- Simulate input, automate gameplay, or provide bots or macros.
- Modify game files, memory, or code.
- Use hidden APIs or attempt to bypass anti-cheat systems.
- Restart wireless debugging or Shizuku after reboot.

Using background-management tools with a particular game may be subject to that game's rules. X-KeepAlive is not endorsed or certified by game developers or anti-cheat providers, and this README does not guarantee that its use is free from restrictions or bans.

## Installation

Download [X-KeepAlive 1.0.3 from GitHub Releases](https://github.com/layiix11/X-KeepAlive/releases/tag/1.0.3) and install it on your Android device. You may need to allow installation from the source used to download the file.

The repository also provides a debug APK, if included with the release. The debug APK and release APK use different signing certificates and cannot be installed over one another as updates. Use the release APK for normal installation and updates signed with the matching release key.

## First launch

1. Open X-KeepAlive and select the app you want to monitor.
2. Grant usage access if requested.
3. Allow the foreground-service notification.
4. Optionally review the battery settings for X-KeepAlive and the selected app.
5. If you want to use privileged background policies, start Shizuku using its supported setup and authorize X-KeepAlive.
6. Enable monitoring.

To stop monitoring, use the in-app switch or the Stop action in the notification. The app will attempt to restore the saved settings while Shizuku is available.

## Shizuku and wireless debugging

Shizuku is an independent Android service that must be configured and started by the user. Follow the instructions in the Shizuku app and Android's Developer options to start it through wireless debugging, then return to X-KeepAlive and grant authorization.

After a reboot, wireless debugging and Shizuku may need to be started again manually. X-KeepAlive cannot restart them itself.

## Privacy

X-KeepAlive has no Google AdMob integration or advertising SDK. The updated source does not declare the `INTERNET` or `ACCESS_NETWORK_STATE` permissions.

Settings and event logs are stored locally using DataStore and the app's local event log. This describes the behavior implemented in the source; it is not a blanket guarantee about every Android system-level service or device configuration.

## Tests and verification

The following checks were reported for version 1.0.3:

- `:policy-core:test`: 29 tests passed.
- Release build: completed successfully.
- On-device activation, deactivation, and restoration with Shizuku: not tested as part of that build, because no Shizuku-enabled physical device was available in the build environment.

A successful build and unit tests do not replace testing on a real device. Behavior may vary by Android version, device manufacturer, system settings, memory pressure, and the app being monitored.
