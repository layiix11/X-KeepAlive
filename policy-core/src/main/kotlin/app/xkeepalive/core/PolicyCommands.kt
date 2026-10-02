package app.xkeepalive.core

object PolicyCommands {
    val standbyBuckets = setOf("active", "working_set", "frequent", "rare", "restricted", "never")
    val appOps = setOf("RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND")
    val appOpModes = setOf("allow", "ignore", "deny", "default")

    fun whitelistAdd(packageName: String): String =
        "cmd deviceidle whitelist +${requirePackage(packageName)}"

    fun whitelistRemove(packageName: String): String =
        "cmd deviceidle whitelist -${requirePackage(packageName)}"

    fun whitelistList(): String = "cmd deviceidle whitelist"

    fun dumpsysWhitelistAdd(packageName: String): String =
        "dumpsys deviceidle whitelist +${requirePackage(packageName)}"

    fun setStandby(packageName: String, bucket: String): String {
        require(bucket in standbyBuckets) { "Standby bucket not allowed" }
        return "am set-standby-bucket ${requirePackage(packageName)} $bucket"
    }

    fun getStandby(packageName: String): String =
        "am get-standby-bucket ${requirePackage(packageName)}"

    fun setInactive(packageName: String, inactive: Boolean): String =
        "am set-inactive ${requirePackage(packageName)} ${if (inactive) "true" else "false"}"

    fun getInactive(packageName: String): String =
        "am get-inactive ${requirePackage(packageName)}"

    fun setAppOp(packageName: String, op: String, mode: String): String {
        require(op in appOps) { "App op not allowed" }
        require(mode in appOpModes) { "App op mode not allowed" }
        return "cmd appops set ${requirePackage(packageName)} $op $mode"
    }

    fun getAppOp(packageName: String, op: String): String {
        require(op in appOps) { "App op not allowed" }
        return "cmd appops get ${requirePackage(packageName)} $op"
    }

    fun pidof(packageName: String): String = "pidof ${requirePackage(packageName)}"

    fun psAll(): String = "ps -A"

    fun isAllowed(command: String): Boolean {
        if (command.any { it.code < 32 || it in ";|&`$<>\\\"'()" }) return false
        if (command == whitelistList() || command == psAll()) return true
        val parts = command.split(' ')
        return when {
            parts.size == 4 &&
                parts[0] == "cmd" &&
                parts[1] == "deviceidle" &&
                parts[2] == "whitelist" &&
                (parts[3].startsWith("+") || parts[3].startsWith("-")) &&
                PackageNames.isValid(parts[3].drop(1)) -> true

            parts.size == 4 &&
                parts[0] == "dumpsys" &&
                parts[1] == "deviceidle" &&
                parts[2] == "whitelist" &&
                parts[3].startsWith("+") &&
                PackageNames.isValid(parts[3].drop(1)) -> true

            parts.size == 4 &&
                parts[0] == "am" &&
                parts[1] == "set-standby-bucket" &&
                PackageNames.isValid(parts[2]) &&
                parts[3] in standbyBuckets -> true

            parts.size == 3 &&
                parts[0] == "am" &&
                parts[1] == "get-standby-bucket" &&
                PackageNames.isValid(parts[2]) -> true

            parts.size == 4 &&
                parts[0] == "am" &&
                parts[1] == "set-inactive" &&
                PackageNames.isValid(parts[2]) &&
                parts[3] in setOf("true", "false") -> true

            parts.size == 3 &&
                parts[0] == "am" &&
                parts[1] == "get-inactive" &&
                PackageNames.isValid(parts[2]) -> true

            parts.size == 6 &&
                parts[0] == "cmd" &&
                parts[1] == "appops" &&
                parts[2] == "set" &&
                PackageNames.isValid(parts[3]) &&
                parts[4] in appOps &&
                parts[5] in appOpModes -> true

            parts.size == 5 &&
                parts[0] == "cmd" &&
                parts[1] == "appops" &&
                parts[2] == "get" &&
                PackageNames.isValid(parts[3]) &&
                parts[4] in appOps -> true

            parts.size == 2 &&
                parts[0] == "pidof" &&
                PackageNames.isValid(parts[1]) -> true

            else -> false
        }
    }

    private fun requirePackage(packageName: String): String {
        require(PackageNames.isValid(packageName)) { "Invalid package name" }
        return packageName
    }
}
