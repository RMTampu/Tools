package io.toolbox.app

object RuntimeCompatibility {
    const val REQUIRED_API = 30
    const val REQUIRED_ABI = "arm64-v8a"

    data class Result(
        val supported: Boolean,
        val reason: String
    )

    fun evaluate(api: Int, supportedAbis: List<String>): Result {
        if (api != REQUIRED_API) {
            return Result(false, "Android API $api is unsupported; required API is $REQUIRED_API")
        }
        if (REQUIRED_ABI !in supportedAbis) {
            return Result(false, "ABI ${supportedAbis.joinToString()} is unsupported; required ABI is $REQUIRED_ABI")
        }
        return Result(true, "Android 11 / API 30 / arm64-v8a")
    }
}
