package com.example.ing_software_abarrotezperez.ai.models

enum class ChatRole { USER, ASSISTANT }

/** Mensaje mostrado en el chat. [persist] = false para mensajes efímeros (p. ej. la bienvenida). */
data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val text: String,
    val table: ResultTable? = null,
    val notes: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val persist: Boolean = true
)
