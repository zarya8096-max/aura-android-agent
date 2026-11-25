package com.aura.agent.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class LocalStore(context: Context) {
    val filesDir: File = context.filesDir
    val workspaceDir: File = File(context.filesDir, "workspace").apply { mkdirs() }
    val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }

    private val stateFile = File(context.filesDir, "aura_agent_state_v3.json")
    private val soulFile = File(context.filesDir, "SOUL.md")
    private val userFile = File(context.filesDir, "USER.md")

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): AppStateSnapshot {
        if (!stateFile.exists()) {
            return initialSnapshot()
        }
        return try {
            val loaded = json.decodeFromString<AppStateSnapshot>(stateFile.readText())
            val providers = if (loaded.providers.isEmpty()) Defaults.defaultProviders() else loaded.providers
            val soul = loaded.soulMarkdown.ifBlank {
                if (soulFile.exists()) soulFile.readText() else Defaults.DEFAULT_SOUL_MD
            }
            val userMd = loaded.userProfileMarkdown.ifBlank {
                if (userFile.exists()) userFile.readText() else Defaults.DEFAULT_USER_MD
            }
            loaded.copy(
                providers = providers,
                soulMarkdown = soul,
                userProfileMarkdown = userMd,
                subagents = if (loaded.subagents.isEmpty()) Defaults.defaultSubagents() else loaded.subagents
            )
        } catch (_: Exception) {
            initialSnapshot()
        }
    }

    private val writeLock = Any()

    fun save(snapshot: AppStateSnapshot) {
        synchronized(writeLock) {
            try {
                atomicWrite(stateFile, json.encodeToString(snapshot))
                atomicWrite(soulFile, snapshot.soulMarkdown)
                atomicWrite(userFile, snapshot.userProfileMarkdown)
            } catch (_: Exception) {
                // Ignore transient IO failures
            }
        }
    }

    private fun atomicWrite(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            target.writeText(content)
            tmp.delete()
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
            memories = emptyList(),
            soulMarkdown = Defaults.DEFAULT_SOUL_MD,
            userProfileMarkdown = Defaults.DEFAULT_USER_MD,
            localModels = emptyList(),
            themePreset = AppThemePreset.OBSIDIAN_GRAPHITE,
            subagents = Defaults.defaultSubagents()
        )
    }
}
