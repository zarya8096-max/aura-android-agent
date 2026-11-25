package com.aura.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aura.agent.ui.components.SparkleIcon
import com.aura.agent.ui.screens.AgentBrainScreen
import com.aura.agent.ui.screens.ChatScreen
import com.aura.agent.ui.screens.GemsScreen
import com.aura.agent.ui.screens.LocalModelsScreen
import com.aura.agent.ui.screens.RouterScreen
import com.aura.agent.ui.screens.SettingsScreen
import androidx.compose.material.icons.outlined.SmartToy
import com.aura.agent.ui.screens.SkillsScreen
import com.aura.agent.ui.screens.SubagentsScreen
import com.aura.agent.ui.theme.AuraTheme
import com.aura.agent.viewmodel.ActiveScreen
import com.aura.agent.viewmodel.AuraViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AuraViewModel = viewModel()
            AuraAppRoot(vm)
        }
    }
}

@Composable
private fun AuraAppRoot(vm: AuraViewModel) {
    val state by vm.state.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    AuraTheme(preset = state.themePreset) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = state.activeScreen == ActiveScreen.CHAT,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.width(304.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 12.dp, vertical = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SparkleIcon(size = 20.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Aura", style = MaterialTheme.typography.titleLarge)
                            }
                            IconButton(onClick = { vm.toggleDarkLightMode() }) {
                                Icon(
                                    imageVector = if (state.themePreset.isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                    contentDescription = "Toggle Light/Dark Mode"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            onClick = {
                                vm.startNewChat(null)
                                scope.launch { drawerState.close() }
                            },
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("New Chat", style = MaterialTheme.typography.titleMedium)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        NavigationDrawerItem(
                            label = { Text("Agent Brain (SOUL & Memory)") },
                            selected = state.activeScreen == ActiveScreen.AGENT_BRAIN,
                            onClick = {
                                vm.navigateTo(ActiveScreen.AGENT_BRAIN)
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(Icons.Outlined.Psychology, contentDescription = null) },
                            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                        )

                        NavigationDrawerItem(
                            label = { Text("Gems (${state.gems.size})") },
                            selected = state.activeScreen == ActiveScreen.GEMS,
                            onClick = {
                                vm.navigateTo(ActiveScreen.GEMS)
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                        )

                        NavigationDrawerItem(
                            label = { Text("Skills & Tools (${state.skills.count { it.enabled }})") },
                            selected = state.activeScreen == ActiveScreen.SKILLS,
                            onClick = {
                                vm.navigateTo(ActiveScreen.SKILLS)
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(Icons.Rounded.Extension, contentDescription = null) },
                            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                        )

                        NavigationDrawerItem(
                            label = { Text("Subagents (${state.subagents.size})") },
                            selected = state.activeScreen == ActiveScreen.SUBAGENTS,
                            onClick = {
                                vm.navigateTo(ActiveScreen.SUBAGENTS)
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(Icons.Outlined.SmartToy, contentDescription = null) },
                            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                        )

                        NavigationDrawerItem(
                            label = { Text("Local Models · HF (${state.localModels.size})") },
                            selected = state.activeScreen == ActiveScreen.LOCAL_MODELS,
                            onClick = {
                                vm.navigateTo(ActiveScreen.LOCAL_MODELS)
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(Icons.Outlined.Memory, contentDescription = null) },
                            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                        )

                        NavigationDrawerItem(
                            label = { Text("AuraRouter · ${state.routerStats.hitRatePercent}% Cache") },
                            selected = state.activeScreen == ActiveScreen.ROUTER,
                            onClick = {
                                vm.navigateTo(ActiveScreen.ROUTER)
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(Icons.Rounded.Hub, contentDescription = null) },
                            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Text(
                            text = "Recent",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(state.sessions, key = { it.id }) { session ->
                                NavigationDrawerItem(
                                    label = {
                                        Text(
                                            session.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    selected = state.activeSessionId == session.id && state.activeScreen == ActiveScreen.CHAT,
                                    onClick = {
                                        vm.selectSession(session.id)
                                        scope.launch { drawerState.close() }
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Outlined.ChatBubbleOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    },
                                    badge = {
                                        IconButton(
                                            onClick = { vm.deleteSession(session.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Delete,
                                                contentDescription = "Delete chat",
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    },
                                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        NavigationDrawerItem(
                            label = { Text("Providers & Appearance") },
                            selected = state.activeScreen == ActiveScreen.SETTINGS,
                            onClick = {
                                vm.navigateTo(ActiveScreen.SETTINGS)
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                        )
                    }
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (state.activeScreen) {
                    ActiveScreen.CHAT -> ChatScreen(
                        state = state,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNewChat = { vm.startNewChat(null) },
                        onToggleDarkLight = { vm.toggleDarkLightMode() },
                        onSelectProvider = { vm.selectProvider(it) },
                        onSendMessage = { vm.sendMessage(it) },
                        onStop = { vm.stopGeneration() },
                        onLaunchGem = { vm.startNewChat(it) },
                        onToggleSoulMd = { vm.toggleChatSoulMd() },
                        onToggleMemoryMd = { vm.toggleChatMemoryMd() },
                        onCycleEffort = { vm.cycleReasoningEffort() },
                        onRespondApproval = { vm.respondToToolApproval(it) },
                        onOpenGems = { vm.navigateTo(ActiveScreen.GEMS) },
                        onOpenSkills = { vm.navigateTo(ActiveScreen.SKILLS) }
                    )
                    ActiveScreen.SUBAGENTS -> SubagentsScreen(
                        subagents = state.subagents,
                        providers = state.providers,
                        onBack = { vm.navigateTo(ActiveScreen.CHAT) },
                        onSaveSubagent = { vm.saveSubagent(it) },
                        onDeleteSubagent = { vm.deleteSubagent(it) }
                    )
                    ActiveScreen.AGENT_BRAIN -> AgentBrainScreen(
                        soulMarkdown = state.soulMarkdown,
                        userProfileMarkdown = state.userProfileMarkdown,
                        memories = state.memories,
                        globalApprovalMode = state.globalApprovalMode,
                        toolPolicies = state.toolPolicies,
                        auditLogs = state.auditLogs,
                        onBack = { vm.navigateTo(ActiveScreen.CHAT) },
                        onSetGlobalApproval = { vm.setGlobalApprovalMode(it) },
                        onSetToolPolicy = { t, m -> vm.setToolPolicy(t, m) },
                        onClearAuditLogs = { vm.clearAuditLogs() },
                        onSaveSoul = { vm.updateSoulMarkdown(it) },
                        onSaveUserProfile = { vm.updateUserProfileMarkdown(it) },
                        onAddMemory = { k, c, cat, pin -> vm.upsertMemory(k, c, cat, emptyList(), pin) },
                        onTogglePin = { vm.toggleMemoryPin(it) },
                        onDeleteMemory = { vm.deleteMemoryById(it) }
                    )
                    ActiveScreen.LOCAL_MODELS -> LocalModelsScreen(
                        localModels = state.localModels,
                        hfResults = state.hfSearchResults,
                        selectedRepoId = state.hfSelectedRepoId,
                        selectedRepoFiles = state.hfSelectedRepoFiles,
                        loading = state.hfLoading,
                        error = state.hfError,
                        activeDownload = state.activeDownload,
                        onBack = { vm.navigateTo(ActiveScreen.CHAT) },
                        onSearch = { vm.searchHuggingFace(it) },
                        onInspectRepo = { vm.inspectHfRepo(it) },
                        onCloseInspect = { vm.closeHfRepoInspect() },
                        onDownloadFile = { repo, file -> vm.downloadHfModel(repo, file) },
                        onCancelDownload = { vm.cancelDownload() },
                        onActivateModel = { vm.activateLocalModel(it) },
                        onDeleteModel = { vm.deleteLocalModel(it) }
                    )
                    ActiveScreen.GEMS -> GemsScreen(
                        gems = state.gems,
                        onBack = { vm.navigateTo(ActiveScreen.CHAT) },
                        onLaunchGem = { vm.startNewChat(it) },
                        onSaveGem = { vm.saveGem(it) },
                        onDeleteGem = { vm.deleteGem(it) }
                    )
                    ActiveScreen.SKILLS -> SkillsScreen(
                        skills = state.skills,
                        onBack = { vm.navigateTo(ActiveScreen.CHAT) },
                        onToggleSkill = { vm.toggleSkill(it) },
                        onSaveSkill = { vm.saveSkill(it) },
                        onDeleteSkill = { vm.deleteSkill(it) }
                    )
                    ActiveScreen.ROUTER -> RouterScreen(
                        config = state.routerConfig,
                        stats = state.routerStats,
                        cacheCount = state.cacheEntries.size,
                        providers = state.providers,
                        healthMap = state.providerHealth,
                        gatewayRunning = state.gatewayRunning,
                        gatewayStatusLog = state.gatewayStatusLog,
                        onBack = { vm.navigateTo(ActiveScreen.CHAT) },
                        onUpdateConfig = { vm.updateRouterConfig(it) },
                        onToggleGateway = { vm.toggleLocalGateway(it) },
                        onClearCache = { vm.clearCache() }
                    )
                    ActiveScreen.SETTINGS -> SettingsScreen(
                        providers = state.providers,
                        activeProviderId = state.activeProviderId,
                        themePreset = state.themePreset,
                        testStatus = state.providerTestStatus,
                        onBack = { vm.navigateTo(ActiveScreen.CHAT) },
                        onToggleDarkLight = { vm.toggleDarkLightMode() },
                        onSelectTheme = { vm.setThemePreset(it) },
                        onSelectProvider = { vm.selectProvider(it) },
                        onSaveProvider = { vm.updateProvider(it) },
                        onDeleteProvider = { vm.deleteProvider(it) },
                        onTestProvider = { vm.testProviderConnection(it) }
                    )
                }
            }
        }
    }
}
