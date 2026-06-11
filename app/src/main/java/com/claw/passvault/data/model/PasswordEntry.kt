package com.claw.passvault.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "passwords")
data class PasswordEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val username: String,     // encrypted at rest
    val password: String,     // encrypted at rest
    val notes: String,        // encrypted at rest
    val category: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class Category(val label: String, val icon: String) {
    ALL("Todas", "🔑"),
    EMAIL("Email", "📧"),
    SOCIAL("Redes Sociales", "💬"),
    BANKING("Banca", "🏦"),
    SHOPPING("Compras", "🛒"),
    WORK("Trabajo", "💼"),
    ENTERTAINMENT("Entretenimiento", "🎮"),
    OTHER("Otros", "📌");
}
