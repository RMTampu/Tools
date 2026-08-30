package io.toolbox.kernel

import java.nio.charset.StandardCharsets
import java.util.Base64

internal data class PersistedKernelStateRecord(
    val state: KernelState,
    val sessionId: String,
    val updatedAtMillis: Long,
    val operation: String?
)

internal object PersistedKernelStateCodec {
    private const val FORMAT_VERSION: Int = 1

    internal fun encode(record: PersistedKernelStateRecord): String = listOf(
        FORMAT_VERSION.toString(),
        record.state.name,
        record.updatedAtMillis.toString(),
        encodeField(record.sessionId),
        encodeField(record.operation.orEmpty())
    ).joinToString("|")

    internal fun decode(raw: String): PersistedKernelStateRecord? {
        val parts = raw.split('|', limit = 5)
        if (parts.size != 5 || parts[0].toIntOrNull() != FORMAT_VERSION) return null
        val state = try {
            KernelState.valueOf(parts[1])
        } catch (_: IllegalArgumentException) {
            return null
        }
        val updatedAtMillis = parts[2].toLongOrNull() ?: return null
        val sessionId = decodeField(parts[3]) ?: return null
        if (sessionId.isBlank()) return null
        val operation = decodeField(parts[4]) ?: return null
        return PersistedKernelStateRecord(
            state = state,
            sessionId = sessionId,
            updatedAtMillis = updatedAtMillis,
            operation = operation.ifBlank { null }
        )
    }

    private fun encodeField(value: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeField(value: String): String? = try {
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }
}
