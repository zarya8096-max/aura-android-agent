package com.aura.agent.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class LocalStore(context: Context) {
    private val file = File(context.filesDir, "aura_agent_state.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): AppStateSnapshot {
        if (!file.exists()) {
            return initialSnapshot()
        }
        return try {
            val loaded = json.decodeFromString<AppStateSnapshot>(file.readText())
            if (loaded.providers.isEmpty()) initialSnapshot() else loaded
        } catch (_: Exception) {
            initialSnapshot()
        }
    }

    fun save(snapshot: AppStateSnapshot) {
        try {
            file.writeText(json.encodeToString(snapshot))
        } catch (_: Exception) {
            // Ignore transient IO failures
        }
    }

    private fun initialSnapshot(): AppStateSnapshot {
        val providers = Defaults.defaultProviders()
        return AppStateSnapshot(
            providers = providers,
            activeProviderId = providers.first().id,
            gems = Defaults.defaultGems(),
            skills = Defaults.defaultSkills(),
            sessions = emptyList(),
            notes = emptyList(),
            darkMode = true
        )
    }
}
