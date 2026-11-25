package com.aura.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.aura.agent.ui.screens.ChatScreen
import com.aura.agent.ui.screens.GemsScreen
import com.aura.agent.ui.screens.RouterScreen
import com.aura.agent.ui.screens.SettingsScreen
import com.aura.agent.ui.screens.SkillsScreen
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

    AuraTheme(darkTheme = state.darkMode) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = state.activeScreen == ActiveScreen.CHAT,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.width(300.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 12.dp, vertical = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            SparkleIcon(size = 24.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Aura Agent", style = MaterialTheme.typography.titleLarge)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            onClick = {
                                vm.startNewChat(null)
                                scope.launch { drawerState.close() }
                            },
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("New Chat", style = MaterialTheme.typography.titleMedium)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Gems",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )

                        state.gems.take(3).forEach { gem ->
                            NavigationDrawerItem(
                                label = { Text(gem.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                selected = state.activeGemId == gem.id && state.activeScreen == ActiveScreen.CHAT,
                                onClick = {
                                    vm.startNewChat(gem.id)
                                    scope.launch { drawerState.close() }
                                },
                                icon = {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(gem.accentHex).copy(alpha = 0.22f))
                                    ) {
                                        Icon(
                                            Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color(gem.accentHex),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                },
                                colors = NavigationDrawerItemDefaults.colors(
                                    unselectedContainerColor = Color.Transparent
                                )
                            )
                        }

                        NavigationDrawerItem(
                            label = { Text("Gem Manager") },
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
                                            modifier = Modifier.size(18.dp)
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
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    colors = NavigationDrawerItemDefaults.colors(
                                        unselectedContainerColor = Color.Transparent
                                    )
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        NavigationDrawerItem(
                            label = { Text("API Providers & Settings") },
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
                        onSelectProvider = { vm.selectProvider(it) },
                        onSendMessage = { vm.sendMessage(it) },
                        onStop = { vm.stopGeneration() },
                        onLaunchGem = { vm.startNewChat(it) },
                        onOpenGems = { vm.navigateTo(ActiveScreen.GEMS) },
                        onOpenSkills = { vm.navigateTo(ActiveScreen.SKILLS) }
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
                        onBack = { vm.navigateTo(ActiveScreen.CHAT) },
                        onUpdateConfig = { vm.updateRouterConfig(it) },
                        gatewayRunning = state.gatewayRunning,
                        gatewayStatusLog = state.gatewayStatusLog,
                        onToggleGateway = { vm.toggleLocalGateway(it) },
                        onClearCache = { vm.clearCache() }
                    )
                    ActiveScreen.SETTINGS -> SettingsScreen(
                        providers = state.providers,
                        activeProviderId = state.activeProviderId,
                        darkMode = state.darkMode,
                        onBack = { vm.navigateTo(ActiveScreen.CHAT) },
                        onToggleDarkMode = { vm.toggleDarkMode() },
                        onSelectProvider = { vm.selectProvider(it) },
                        onSaveProvider = { vm.updateProvider(it) },
                        onDeleteProvider = { vm.deleteProvider(it) }
                    )
                }
            }
        }
    }
}
