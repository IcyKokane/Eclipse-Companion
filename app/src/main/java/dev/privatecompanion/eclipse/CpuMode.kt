package dev.privatecompanion.eclipse

/**
 * User-selectable CPU-only llama.cpp thread counts.
 * More threads are not necessarily faster on thermally constrained phones.
 * Never request more CPU workers than Android reports available.
 */
enum class CpuMode(val title: String, private val requested: Int?) {
    AUTO("Auto · recommended", null),
    EFFICIENT("Efficient · low heat", 2),
    BALANCED("Balanced · 4 threads", 4),
    PERFORMANCE("Performance · up to 6 threads", 6);

    fun threads(availableProcessors: Int): Int {
        val cores = availableProcessors.coerceIn(1, 64)
        val desired = requested ?: (cores - 1).coerceIn(1, 4)
        return desired.coerceIn(1, minOf(cores, 8))
    }

    companion object {
        fun fromStored(value: String?): CpuMode =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}
