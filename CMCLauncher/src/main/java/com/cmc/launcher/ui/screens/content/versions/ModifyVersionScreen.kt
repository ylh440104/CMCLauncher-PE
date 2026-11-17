package com.cmc.launcher.ui.screens.content.versions

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.cmc.launcher.R
import com.cmc.launcher.game.addons.modloader.AddonVersion
import com.cmc.launcher.game.addons.modloader.ModLoader
import com.cmc.launcher.game.addons.modloader.fabriclike.fabric.FabricVersions
import com.cmc.launcher.game.addons.modloader.fabriclike.legacyfabric.LegacyFabricVersions
import com.cmc.launcher.game.addons.modloader.fabriclike.quilt.QuiltVersions
import com.cmc.launcher.game.addons.modloader.forgelike.forge.ForgeVersions
import com.cmc.launcher.game.addons.modloader.forgelike.neoforge.NeoForgeVersions
import com.cmc.launcher.game.addons.modloader.optifine.OptiFineVersions
import com.cmc.launcher.game.download.game.GameDownloadInfo
import com.cmc.launcher.game.download.game.models.LaunchFor
import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.game.version.installed.VersionInfo
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.ui.base.BaseScreen
import com.cmc.launcher.ui.components.AnimatedLazyColumn
import com.cmc.launcher.ui.components.TextTransition
import com.cmc.launcher.ui.components.animated
import com.cmc.launcher.ui.components.influencedByBackgroundColor
import com.cmc.launcher.ui.screens.NestedNavKey
import com.cmc.launcher.ui.screens.NormalNavKey
import com.cmc.launcher.ui.screens.TitledNavKey
import com.cmc.launcher.ui.screens.content.download.game.AddonList
import com.cmc.launcher.ui.screens.content.download.game.AddonState
import com.cmc.launcher.ui.screens.content.download.game.CurrentAddon
import com.cmc.launcher.ui.screens.content.download.game.FabricList
import com.cmc.launcher.ui.screens.content.download.game.ForgeList
import com.cmc.launcher.ui.screens.content.download.game.LegacyFabricList
import com.cmc.launcher.ui.screens.content.download.game.LoaderVerSupports
import com.cmc.launcher.ui.screens.content.download.game.NeoForgeList
import com.cmc.launcher.ui.screens.content.download.game.OptiFineList
import com.cmc.launcher.ui.screens.content.download.game.QuiltList
import com.cmc.launcher.ui.screens.content.download.game.SelectGameVersionHost
import com.cmc.launcher.ui.screens.content.download.game.SelectGameVersionScreen
import com.cmc.launcher.ui.screens.content.download.game.isOptiFineCompatibleWithForge
import com.cmc.launcher.ui.screens.content.download.game.rememberLoaderVerSupports
import com.cmc.launcher.ui.screens.content.download.game.runWithState
import com.cmc.launcher.ui.screens.content.elements.backgroundGlass
import com.cmc.launcher.ui.screens.navigateTo
import com.cmc.launcher.ui.screens.onBack
import com.cmc.launcher.ui.screens.rememberTitledNavBackStack
import com.cmc.launcher.ui.screens.rememberTransitionSpec
import com.cmc.launcher.ui.theme.cardColor
import com.cmc.launcher.ui.theme.onCardColor
import com.cmc.launcher.utils.GSON
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.viewmodel.EventViewModel
import com.cmc.launcher.viewmodel.ModifyDiffs
import com.cmc.launcher.viewmodel.ModifyOperation
import com.cmc.launcher.viewmodel.ModifyPayload
import com.cmc.launcher.viewmodel.ModifyVersionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "ModifyVersionScreen"

@Composable
fun ModifyVersionScreen(
    viewModel: ModifyVersionViewModel,
    mainScreenKey: TitledNavKey?,
    versionsScreenKey: TitledNavKey?,
    version: Version,
    eventViewModel: EventViewModel,
    onModify: (ModifyPayload) -> Unit
) {
    val backStack = rememberTitledNavBackStack(NormalNavKey.Versions.ModifyVersion)

    var selectedGameVersion by remember { mutableStateOf<String?>(null) }

    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize(),
        onBack = { onBack(backStack) },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        transitionSpec = rememberTransitionSpec(),
        popTransitionSpec = rememberTransitionSpec(),
        entryProvider = entryProvider {
            entry<NormalNavKey.Versions.ModifyVersion> {
                ModifyVersionContent(
                    mainScreenKey = mainScreenKey,
                    versionsScreenKey = versionsScreenKey,
                    version = version,
                    modifyViewModel = viewModel,
                    selectedGameVersion = selectedGameVersion,
                    onSelectGameVersion = {
                        backStack.navigateTo(NormalNavKey.Versions.SelectGameVersion)
                    },
                    onModify = onModify
                )
            }
            entry<NormalNavKey.Versions.SelectGameVersion> {
                SelectGameVersionScreen(
                    host = SelectGameVersionHost.VersionSettings(
                        mainScreenKey = mainScreenKey,
                        versionSettingsScreenKey = versionsScreenKey
                    ),
                    eventViewModel = eventViewModel
                ) { selected ->
                    selectedGameVersion = selected.version.id
                    onBack(backStack)
                }
            }
        }
    )
}

@Composable
private fun ModifyVersionContent(
    mainScreenKey: TitledNavKey?,
    versionsScreenKey: TitledNavKey?,
    version: Version,
    modifyViewModel: ModifyVersionViewModel,
    selectedGameVersion: String?,
    onSelectGameVersion: () -> Unit = {},
    onModify: (ModifyPayload) -> Unit
) {
    val versionInfo = remember(version) {
        version.getVersionInfo() ?: error("Version information is unavailable: ${version.getVersionName()}")
    }
    val originalGameVersion = versionInfo.minecraftVersion
    val targetGameVersion = selectedGameVersion ?: originalGameVersion

    val loaderSupports = rememberLoaderVerSupports(targetGameVersion)
    val installedLoaders = rememberInstalledLoaders(version, versionInfo)

    val addonsVM = viewModel(
        key = version.toString() + "_ModifyVersion_" + targetGameVersion + "_" + installedLoaders
    ) {
        ModifyAddonsViewModel(
            gameVersion = targetGameVersion,
            originalGameVersion = originalGameVersion,
            installedLoaders = installedLoaders,
            loaderSupports = loaderSupports
        )
    }

    BaseScreen(
        levels1 = listOf(
            Pair(NestedNavKey.VersionSettings::class.java, mainScreenKey)
        ),
        Triple(NormalNavKey.Versions.ModifyVersion, versionsScreenKey, false)
    ) { isVisible ->
        val scrollState = rememberLazyListState()
        AnimatedLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nonInteractiveScrollbar(
                    state = scrollState.scrollIndicatorState!!,
                    orientation = Orientation.Vertical,
                ),
            isVisible = isVisible,
            contentPadding = PaddingValues(all = 12.dp),
            state = scrollState,
        ) {
            animatedItem { ctx ->
                MCVersionRow(
                    modifier = Modifier.animated(ctx),
                    current = originalGameVersion,
                    selected = targetGameVersion,
                    onClick = onSelectGameVersion
                )
            }

            if (targetGameVersion != originalGameVersion) {
                animatedItem { ctx ->
                    ModifyTipItem(
                        modifier = Modifier.animated(ctx),
                        title = stringResource(R.string.versions_modify_mc_changed_title),
                        text = stringResource(R.string.versions_modify_mc_changed_tip)
                    )
                }
            }

            val unrecognizedInstalled = if (targetGameVersion == originalGameVersion) {

                installedLoaders.filter { it.loader in addonsVM.unmatchedLoaders }
            } else {
                emptyList()
            }
            if (unrecognizedInstalled.isNotEmpty()) {
                animatedItem { ctx ->
                    ModifyTipItem(
                        modifier = Modifier.animated(ctx),
                        title = stringResource(R.string.versions_modify_unrecognized_title),
                        text = unrecognizedInstalled.joinToString("、") { "${it.loader.displayName} ${it.version}" }
                    )
                }
            }

            animatedItem { ctx ->
                OptiFineList(
                    modifier = Modifier.animated(ctx),
                    currentAddon = addonsVM.currentAddon,
                    addonList = addonsVM.addonList,
                    onValueChanged = { addonsVM.updateDiffs() },
                    onReload = { addonsVM.reloadOptiFine() }
                )
            }

            animatedItem { ctx ->
                ForgeList(
                    modifier = Modifier.animated(ctx),
                    currentAddon = addonsVM.currentAddon,
                    addonList = addonsVM.addonList,
                    onValueChanged = { addonsVM.updateDiffs() },
                    onReload = { addonsVM.reloadForge() }
                )
            }

            if (loaderSupports.isNeoForgeSupports) {
                animatedItem { ctx ->
                    NeoForgeList(
                        modifier = Modifier.animated(ctx),
                        currentAddon = addonsVM.currentAddon,
                        addonList = addonsVM.addonList,
                        onValueChanged = { addonsVM.updateDiffs() },
                        onReload = { addonsVM.reloadNeoForge() }
                    )
                }
            }

            if (loaderSupports.isFabricSupports) {
                animatedItem { ctx ->
                    FabricList(
                        modifier = Modifier.animated(ctx),
                        currentAddon = addonsVM.currentAddon,
                        addonList = addonsVM.addonList,
                        onValueChanged = { addonsVM.updateDiffs() },
                        onReload = { addonsVM.reloadFabric() }
                    )
                }
            }

            if (loaderSupports.isLegacyFabricSupports) {
                animatedItem { ctx ->
                    LegacyFabricList(
                        modifier = Modifier.animated(ctx),
                        currentAddon = addonsVM.currentAddon,
                        addonList = addonsVM.addonList,
                        onValueChanged = { addonsVM.updateDiffs() },
                        onReload = { addonsVM.reloadLegacyFabric() }
                    )
                }
            }

            if (loaderSupports.isQuiltSupports) {
                animatedItem { ctx ->
                    QuiltList(
                        modifier = Modifier.animated(ctx),
                        currentAddon = addonsVM.currentAddon,
                        addonList = addonsVM.addonList,
                        onValueChanged = { addonsVM.updateDiffs() },
                        onReload = { addonsVM.reloadQuilt() }
                    )
                }
            }

            animatedItem { ctx ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animated(ctx),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        enabled = addonsVM.currentDiffs != null && modifyViewModel.installOperation is ModifyOperation.None,
                        onClick = {
                            val diffs = addonsVM.currentDiffs ?: return@Button
                            onModify(
                                ModifyPayload(
                                    info = GameDownloadInfo(
                                        gameVersion = targetGameVersion,
                                        customVersionName = version.getVersionName(),
                                        optifine = addonsVM.currentAddon.optifineVersion.value,
                                        forge = addonsVM.currentAddon.forgeVersion.value,
                                        neoforge = addonsVM.currentAddon.neoforgeVersion.value
                                            .takeIf { loaderSupports.isNeoForgeSupports },
                                        fabric = addonsVM.currentAddon.fabricVersion.value
                                            .takeIf { loaderSupports.isFabricSupports },
                                        legacyFabric = addonsVM.currentAddon.legacyFabricVersion.value
                                            .takeIf { loaderSupports.isLegacyFabricSupports },
                                        quilt = addonsVM.currentAddon.quiltVersion.value
                                            .takeIf { loaderSupports.isQuiltSupports }
                                    ),
                                    currentVersion = version,
                                    diffs = diffs
                                )
                            )
                        }
                    ) {
                        Text(text = stringResource(R.string.versions_modify_start))
                    }
                }
            }
        }
    }
}

private class ModifyAddonsViewModel(

    private val gameVersion: String,

    private val originalGameVersion: String,

    private val installedLoaders: List<VersionInfo.LoaderInfo>,

    loaderSupports: LoaderVerSupports
) : ViewModel() {
    val addonList = AddonList()
    val currentAddon = CurrentAddon()

    var currentDiffs by mutableStateOf<ModifyDiffs?>(null)
        private set

    var unmatchedLoaders by mutableStateOf<Set<ModLoader>>(emptySet())
        private set

    private val pendingPreselects = linkedMapOf<ModLoader, () -> Unit>()

    private val attemptedPreselects = mutableSetOf<ModLoader>()

    private val reloadableLoaders = buildSet {
        add(ModLoader.OPTIFINE)
        add(ModLoader.FORGE)
        if (loaderSupports.isNeoForgeSupports) add(ModLoader.NEOFORGE)
        if (loaderSupports.isFabricSupports) add(ModLoader.FABRIC)
        if (loaderSupports.isLegacyFabricSupports) add(ModLoader.LEGACY_FABRIC)
        if (loaderSupports.isQuiltSupports) add(ModLoader.QUILT)
    }

    private fun findInstalled(loader: ModLoader): VersionInfo.LoaderInfo? {
        return installedLoaders.firstOrNull { it.loader == loader }
    }

    private fun selectedVersionOf(loader: ModLoader): String? = when (loader) {
        ModLoader.OPTIFINE -> currentAddon.optifineVersion.value?.getAddonVersion()
        ModLoader.FORGE -> currentAddon.forgeVersion.value?.getAddonVersion()
        ModLoader.NEOFORGE -> currentAddon.neoforgeVersion.value?.getAddonVersion()
        ModLoader.FABRIC -> currentAddon.fabricVersion.value?.getAddonVersion()
        ModLoader.LEGACY_FABRIC -> currentAddon.legacyFabricVersion.value?.getAddonVersion()
        ModLoader.QUILT -> currentAddon.quiltVersion.value?.getAddonVersion()
        else -> null
    }

    fun updateDiffs() {
        val diffs = buildList {
            if (gameVersion != originalGameVersion) {
                add(ModifyDiffs.McChange(original = originalGameVersion, updateTo = gameVersion))
            }

            val keepUnmatched = gameVersion == originalGameVersion
            installedLoaders.forEach { installed ->
                val selected = selectedVersionOf(installed.loader)
                if (installed.loader !in attemptedPreselects) return@forEach

                if (keepUnmatched && installed.loader in unmatchedLoaders) {
                    if (selected != null && !selectedEqualsInstalled(installed, selected)) {
                        add(
                            ModifyDiffs.LoaderChange(
                                modloader = installed.loader,
                                original = installed.version,
                                updateTo = selected
                            )
                        )
                    }
                    return@forEach
                }
                when {
                    selected == null -> add(ModifyDiffs.LoaderRemove(modloader = installed.loader))
                    !selectedEqualsInstalled(installed, selected) -> add(
                        ModifyDiffs.LoaderChange(
                            modloader = installed.loader,
                            original = installed.version,
                            updateTo = selected
                        )
                    )
                }
            }

            listOf(
                ModLoader.OPTIFINE, ModLoader.FORGE, ModLoader.NEOFORGE,
                ModLoader.FABRIC, ModLoader.LEGACY_FABRIC, ModLoader.QUILT
            ).forEach { loader ->
                if (installedLoaders.none { it.loader == loader }) {
                    selectedVersionOf(loader)?.let { version ->
                        add(ModifyDiffs.LoaderInstall(modloader = loader, version = version))
                    }
                }
            }
        }

        currentDiffs = diffs.takeIf { it.isNotEmpty() }?.let { ModifyDiffs(list = it) }
    }

    private fun selectedEqualsInstalled(
        installed: VersionInfo.LoaderInfo,
        selected: String
    ): Boolean {
        if (selected == installed.version) return true
        if (installed.loader != ModLoader.OPTIFINE) return false
        return addonList.optifineList?.any {
            it.getAddonVersion() == selected && it.matchesInstalledVersion(installed.version)
        } == true
    }

    private fun <T> launchAddonReload(
        updateState: (AddonState) -> Unit,
        fetch: suspend () -> T?,
        afterLoaded: (T?) -> Unit
    ) {
        viewModelScope.launch {
            runWithState(updateState, fetch).also { versions ->
                afterLoaded(versions)
                updateDiffs()
            }
        }
    }

    private fun enqueuePreselect(loader: ModLoader, action: () -> Unit) {
        pendingPreselects[loader] = action
        attemptedPreselects.remove(loader)
        runPendingPreselects()
    }

    private fun runPendingPreselects() {
        installedLoaders
            .sortedBy {
                VersionInfo.PRIMARY_PRIORITY.indexOf(it.loader).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
            }
            .forEach { installed ->
                val loader = installed.loader
                if (loader in attemptedPreselects) return@forEach
                val action = pendingPreselects[loader]
                if (action == null) {
                    if (loader in reloadableLoaders) return

                    attemptedPreselects.add(loader)
                    unmatchedLoaders = unmatchedLoaders + loader
                    return@forEach
                }
                attemptedPreselects.add(loader)
                action()
            }
    }

    private fun <T : AddonVersion> preselectInstalled(
        state: MutableState<T?>,
        loader: ModLoader,
        versions: List<T>?,
        installedVersion: String,
        matcher: (T, String) -> Boolean = { version, installed -> version.isVersion(installed) },
        validator: (T) -> Boolean = { true }
    ) {
        if (state.value != null) return

        val candidate = versions?.find { matcher(it, installedVersion) }
        if (
            candidate == null ||
            !validator(candidate) ||
            !currentAddon.isCompatibleWithSelection(candidate, loader, addonList)
        ) {
            unmatchedLoaders = unmatchedLoaders + loader
            return
        }

        state.value = candidate
        unmatchedLoaders = unmatchedLoaders - loader
    }

    fun reloadOptiFine() {
        launchAddonReload(
            { currentAddon.optifineState = it },
            { OptiFineVersions.fetchOptiFineList(gameVersion = gameVersion) }
        ) { versions ->
            addonList.optifineList = versions
            enqueuePreselect(ModLoader.OPTIFINE) {
                findInstalled(ModLoader.OPTIFINE)?.let { installed ->
                    preselectInstalled(
                        state = currentAddon.optifineVersion,
                        loader = ModLoader.OPTIFINE,
                        versions = versions,
                        installedVersion = installed.version,
                        matcher = { version, installed -> version.matchesInstalledVersion(installed) },

                        validator = { version ->
                            currentAddon.forgeVersion.value?.let { forge ->
                                isOptiFineCompatibleWithForge(version, forge)
                            } ?: true
                        }
                    )
                }
            }
        }
    }

    fun reloadForge() {
        launchAddonReload(
            { currentAddon.forgeState = it },
            { ForgeVersions.fetchForgeList(gameVersion) }
        ) { versions ->
            addonList.forgeList = versions
            enqueuePreselect(ModLoader.FORGE) {
                findInstalled(ModLoader.FORGE)?.let { installed ->
                    preselectInstalled(
                        state = currentAddon.forgeVersion,
                        loader = ModLoader.FORGE,
                        versions = versions,
                        installedVersion = installed.version,

                        validator = { version ->
                            currentAddon.optifineVersion.value?.let { optifine ->
                                isOptiFineCompatibleWithForge(optifine, version)
                            } ?: true
                        }
                    )
                }
            }
        }
    }

    fun reloadNeoForge() {
        launchAddonReload(
            { currentAddon.neoforgeState = it },
            { NeoForgeVersions.fetchNeoForgeList(gameVersion = gameVersion) }
        ) { versions ->
            addonList.neoforgeList = versions
            enqueuePreselect(ModLoader.NEOFORGE) {
                findInstalled(ModLoader.NEOFORGE)?.let { installed ->
                    preselectInstalled(currentAddon.neoforgeVersion, ModLoader.NEOFORGE, versions, installed.version)
                }
            }
        }
    }

    fun reloadFabric() {
        launchAddonReload(
            { currentAddon.fabricState = it },
            { FabricVersions.fetchFabricLoaderList(gameVersion) }
        ) { versions ->
            addonList.fabricList = versions
            enqueuePreselect(ModLoader.FABRIC) {
                findInstalled(ModLoader.FABRIC)?.let { installed ->
                    preselectInstalled(currentAddon.fabricVersion, ModLoader.FABRIC, versions, installed.version)
                }
            }
        }
    }

    fun reloadLegacyFabric() {
        launchAddonReload(
            { currentAddon.legacyFabricState = it },
            { LegacyFabricVersions.fetchFabricLoaderList(gameVersion) }
        ) { versions ->
            addonList.legacyFabricList = versions
            enqueuePreselect(ModLoader.LEGACY_FABRIC) {
                findInstalled(ModLoader.LEGACY_FABRIC)?.let { installed ->
                    preselectInstalled(currentAddon.legacyFabricVersion, ModLoader.LEGACY_FABRIC, versions, installed.version)
                }
            }
        }
    }

    fun reloadQuilt() {
        launchAddonReload(
            { currentAddon.quiltState = it },
            { QuiltVersions.fetchQuiltLoaderList(gameVersion) }
        ) { versions ->
            addonList.quiltList = versions
            enqueuePreselect(ModLoader.QUILT) {
                findInstalled(ModLoader.QUILT)?.let { installed ->
                    preselectInstalled(currentAddon.quiltVersion, ModLoader.QUILT, versions, installed.version)
                }
            }
        }
    }

    init {

        installedLoaders.forEach { installed ->
            if (installed.loader !in reloadableLoaders) {
                attemptedPreselects.add(installed.loader)
                unmatchedLoaders = unmatchedLoaders + installed.loader
            }
        }
        reloadOptiFine()
        reloadForge()
        if (loaderSupports.isNeoForgeSupports) {
            reloadNeoForge()
        }
        if (loaderSupports.isFabricSupports) {
            reloadFabric()
        }
        if (loaderSupports.isLegacyFabricSupports) {
            reloadLegacyFabric()
        }
        if (loaderSupports.isQuiltSupports) {
            reloadQuilt()
        }
    }

    override fun onCleared() {
        viewModelScope.cancel()
    }
}

@Composable
private fun rememberInstalledLoaders(
    version: Version,
    versionInfo: VersionInfo
): List<VersionInfo.LoaderInfo> {
    val installedLoaders by produceState(emptyList(), version, versionInfo) {
        value = withContext(Dispatchers.IO) {
            buildList {
                runCatching {
                    val jsonFile = File(version.getVersionPath(), "${version.getVersionName()}.json")
                    GSON.fromJson(jsonFile.readText(), LaunchFor::class.java)
                }.onFailure { e ->
                    Logger.warning(TAG, "Failed to parse the launchFor info of the version.", e)
                }.getOrNull()?.infos?.forEach { info ->
                    if (info.name.equals("Minecraft", ignoreCase = true)) return@forEach
                    ModLoader.entries.find { it.displayName.equals(info.name, ignoreCase = true) }?.let { loader ->
                        add(VersionInfo.LoaderInfo(loader = loader, version = info.version))
                    }
                }
                versionInfo.loaderInfos.forEach { loaderInfo ->
                    if (none { it.loader == loaderInfo.loader }) {
                        add(loaderInfo)
                    }
                }
            }.distinctBy { it.loader }
        }
    }
    return installedLoaders
}

@Composable
private fun MCVersionRow(
    modifier: Modifier = Modifier,
    current: String,
    selected: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cardColor(),
        contentColor = onCardColor()
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(16.dp))
            Image(
                modifier = Modifier.size(34.dp),
                painter = painterResource(R.drawable.img_minecraft),
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(all = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Minecraft",
                    style = MaterialTheme.typography.titleSmall
                )
                if (selected == current) {
                    Text(
                        text = current,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    TextTransition(
                        from = current,
                        to = selected
                    )
                }
            }
            Icon(
                modifier = Modifier.size(34.dp),
                painter = painterResource(R.drawable.ic_arrow_right_rounded),
                contentDescription = stringResource(R.string.versions_modify_select_mc)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun ModifyTipItem(
    title: String,
    text: String,
    modifier: Modifier = Modifier
) {
    val color = influencedByBackgroundColor(
        color = MaterialTheme.colorScheme.secondaryContainer,
        enabled = true
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = color,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier
                .backgroundGlass(AllSettings.backgroundBlur.state, color)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(R.drawable.ic_info_outlined),
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
