package app.xkeepalive.core

object PolicyOutput {
    fun parseExitPayload(payload: String): Pair<Int, String> {
        val separator = payload.indexOf('\n')
        if (separator < 0) return -1 to payload
        val code = payload.substring(0, separator).trim().toIntOrNull() ?: -1
        return code to payload.substring(separator + 1)
    }

    fun parsePids(output: String): Set<Int> =
        output.split(Regex("\\s+")).mapNotNull { token -> token.trim().toIntOrNull()?.takeIf { it in 1..9_999_999 } }.toSet()

    fun parseProcessPids(output: String, packageName: String): Set<Int> {
        if (!PackageNames.isValid(packageName)) return emptySet()
        val name = Regex("(^|\\s)${Regex.escape(packageName)}(:[A-Za-z0-9._]+)?(\\s|$)")
        return output.lineSequence().mapNotNull { line ->
            if (!name.containsMatchIn(line)) return@mapNotNull null
            line.trim().split(Regex("\\s+"))
                .mapNotNull { token -> token.toIntOrNull()?.takeIf { it in 1..9_999_999 } }
                .firstOrNull()
        }.toSet()
    }

    fun parseStandbyBucket(output: String): String? {
        val text = output.trim().lowercase()
        if (text.isEmpty()) return null
        if (text.contains("exempted")) return "exempted"
        PolicyCommands.standbyBuckets.firstOrNull { bucket -> text.contains(bucket) }?.let { return it }
        val number = Regex("\\d+").find(text)?.value?.toIntOrNull() ?: return null
        return when (number) {
            5 -> "exempted"
            10 -> "active"
            20 -> "working_set"
            30 -> "frequent"
            40 -> "rare"
            45 -> "restricted"
            50 -> "never"
            else -> null
        }
    }

    fun parseInactive(output: String): Boolean? {
        val text = output.trim().lowercase()
        return when {
            "idle=true" in text || text == "true" -> true
            "idle=false" in text || text == "false" -> false
            else -> null
        }
    }

    fun whitelistContains(output: String, packageName: String): Boolean {
        if (!PackageNames.isValid(packageName)) return false
        val pattern = Regex("(^|[^A-Za-z0-9_.])${Regex.escape(packageName)}([^A-Za-z0-9_.]|$)")
        return pattern.containsMatchIn(output)
    }

    fun appOpMode(output: String, op: String): String? {
        val line = output.lineSequence().firstOrNull { it.contains(op, ignoreCase = true) } ?: output
        val lower = line.lowercase()
        return when {
            "deny" in lower -> "deny"
            "ignore" in lower -> "ignore"
            "allow" in lower -> "allow"
            "default" in lower -> "default"
            else -> null
        }
    }

    fun commandFailed(exitCode: Int, output: String): Boolean {
        if (exitCode != 0) return true
        val lower = output.lowercase()
        return "securityexception" in lower ||
            "unknown command" in lower ||
            "error:" in lower ||
            "exception" in lower
    }
}
