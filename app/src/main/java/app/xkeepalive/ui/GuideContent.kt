package app.xkeepalive.ui

data class GuideSection(val title: String, val body: String)

val guideSections = listOf(
    GuideSection(
        "What it actually does",
        "X-KeepAlive notices when the game you chose moves to the foreground or the background, using usage access. If Shizuku is running and you authorize it, the app reads the current Doze whitelist, standby bucket, idle state, and RUN_IN_BACKGROUND / RUN_ANY_IN_BACKGROUND modes, then changes only the values it could read. “Background management active” appears only when at least one of those reads confirms the result. Stopping monitoring writes back the saved values. A Doze entry that was already present is left in place. If a restore cannot be confirmed, the log says so and does not call it successful.",
    ),
    GuideSection(
        "What it cannot do",
        "It does not play for you, simulate taps, or use an accessibility service. It does not start, close, or reopen the game. It does not write the OOM score or use hidden APIs. It cannot stop the game from reloading if the activity loses its graphics surface, even while the process is still in memory. It cannot stop Android from killing the process when memory is low.",
    ),
    GuideSection(
        "An ADB pairing is not enough",
        "Wireless debugging you already paired authorizes the client you paired, usually a PC or the Shizuku app. An Android app cannot see that session. X-KeepAlive does not open an ADB socket. It talks to Shizuku, which runs as shell (uid 2000) only after you start it.",
    ),
    GuideSection(
        "Standard mode",
        "Without Shizuku the app still detects when the game opens, leaves, and returns, and it keeps its own foreground service. It does not change the game’s priority. In this mode the log says the PID cannot be checked. You can still set the game’s battery to Unrestricted by hand.",
    ),
    GuideSection(
        "Wireless debugging",
        "1. Settings, About phone, tap Build number seven times.\n2. Developer options, turn on Wireless debugging.\n3. In Wireless debugging open Pair device with pairing code.\n4. Enter that code in Shizuku, under Start via wireless debugging.\n5. When Shizuku is running, come back here and tap Authorize Shizuku.\nX-KeepAlive does not pair by itself.",
    ),
    GuideSection(
        "After a reboot",
        "Wireless debugging and Shizuku turn off. Start them again by hand. If you left the switch on and boot restore is enabled, X-KeepAlive can restart only its own monitoring, and only if usage access is still granted. It does not claim to restart ADB or Shizuku.",
    ),
    GuideSection(
        "Permissions",
        "Usage access: required to know which app is in the foreground.\nNotification: required for the foreground service, which stays visible.\nX-KeepAlive battery: keeps Android from suspending monitoring.\nGame battery: a system setting, separate from the Shizuku commands.\nShizuku: only if you tap the button. A denial leaves standard mode on.",
    ),
    GuideSection(
        "Privacy",
        "The selection, the log, and test results stay on the phone in app-private storage. There is no account and no app server in this app. The source does not declare the internet permission and does not include an ad or analytics SDK. Shizuku is a local service on the device. This does not describe what Android or other installed apps do outside this code.",
    ),
)
