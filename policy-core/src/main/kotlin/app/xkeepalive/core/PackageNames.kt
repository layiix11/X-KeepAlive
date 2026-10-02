package app.xkeepalive.core

object PackageNames {
    private val pattern = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    fun isValid(packageName: String): Boolean = pattern.matches(packageName)
}
