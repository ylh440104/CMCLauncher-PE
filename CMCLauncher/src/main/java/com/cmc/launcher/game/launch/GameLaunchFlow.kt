package com.cmc.launcher.game.launch

import android.content.Context
import com.cmc.launcher.R
import com.cmc.launcher.coroutine.Task
import com.cmc.launcher.coroutine.TaskFlowExecutor
import com.cmc.launcher.coroutine.TitledTask
import com.cmc.launcher.coroutine.addTask
import com.cmc.launcher.coroutine.buildPhase
import com.cmc.launcher.game.account.Account
import com.cmc.launcher.game.account.AccountsManager
import com.cmc.launcher.game.account.auth_server.AuthServerHelper
import com.cmc.launcher.game.account.isLocalAccount
import com.cmc.launcher.game.account.isMicrosoftAccount
import com.cmc.launcher.game.account.isReloginRequired
import com.cmc.launcher.game.account.microsoft.validateAccessToken
import com.cmc.launcher.game.account.refreshMicrosoft
import com.cmc.launcher.game.download.game.GameLibDownloader
import com.cmc.launcher.game.renderer.detectFpeEmulatorMods
import com.cmc.launcher.game.support.lwjgl3ify.patchLwjgl3ifyIfNeeded
import com.cmc.launcher.game.version.download.BaseMinecraftDownloader
import com.cmc.launcher.game.version.download.DownloadMode
import com.cmc.launcher.game.version.download.MinecraftDownloader
import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.game.version.installed.VersionFolders
import com.cmc.launcher.game.version.installed.VersionInfoParser
import com.cmc.launcher.game.version.mod.AllModReader
import com.cmc.launcher.game.version.mod.isEnabled
import com.cmc.launcher.game.versioninfo.models.GameManifest
import com.cmc.launcher.ui.activities.runGame
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.utils.GSON
import com.cmc.launcher.utils.network.isNetworkAvailable
import com.cmc.launcher.utils.string.getMessageOrToString
import com.cmc.launcher.viewmodel.ErrorViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.StateFlow

private class LaunchReloginRequired(
    val account: Account
) : RuntimeException()

private class LaunchCheckFailed(
    val account: Account,
    cause: Throwable
) : RuntimeException(cause)

class GameLaunchFlow(scope: CoroutineScope) {
    private val taskExecutor = TaskFlowExecutor(scope)
    val tasksFlow: StateFlow<List<TitledTask>> = taskExecutor.tasksFlow

    fun launch(
        context: Context,
        version: Version,
        skipAccountRefresh: Boolean = false,
        exitActivity: () -> Unit,
        onReloginRequired: (Account) -> Unit = {},
        onRefreshFailed: (Account, Throwable) -> Unit = { _, _ -> },
        isRunning: () -> Unit = {},
        onComplete: () -> Unit,
        onError: (ErrorViewModel.ThrowableMessage) -> Unit,
    ) {
        if (taskExecutor.isRunning()) {

            isRunning()
            return
        }

        val account = AccountsManager.currentAccountFlow.value ?: return
        var errorReported = false
        val handleError: (ErrorViewModel.ThrowableMessage) -> Unit = { message ->
            if (!errorReported) {
                errorReported = true
                onError(message)
            }
        }

        taskExecutor.executePhasesAsync(
            onStart = {
                taskExecutor.addPhases(
                    listOf(
                        buildLaunchPhases(
                            context = context,
                            version = version,
                            account = account,
                            skipAccountRefresh = skipAccountRefresh,
                            exitActivity = exitActivity,
                            onError = handleError
                        )
                    )
                )
            },
            onComplete = onComplete,
            onError = { th ->
                when (th) {
                    is LaunchReloginRequired -> onReloginRequired(th.account)
                    is LaunchCheckFailed -> onRefreshFailed(th.account, th.cause ?: th)
                    else -> handleError(
                        ErrorViewModel.ThrowableMessage(
                            title = androidText(R.string.generic_error),
                            message = androidText(th.getMessageOrToString())
                        )
                    )
                }
            },
        )
    }

    fun cancel() {
        taskExecutor.cancel()
    }

    private fun buildLaunchPhases(
        context: Context,
        version: Version,
        account: Account,
        skipAccountRefresh: Boolean,
        exitActivity: () -> Unit,
        onError: (ErrorViewModel.ThrowableMessage) -> Unit
    ): TaskFlowExecutor.TaskPhase {

        val hasNetwork = isNetworkAvailable(context)
        if (!hasNetwork && !account.isLocalAccount()) {
            version.offlineAccountLogin = true
        }

        return buildPhase {
            var launchManifest: GameManifest? = null

            if (hasNetwork && !skipAccountRefresh && AccountsManager.isLaunchCheckNeeded(account)) {

                addTask(
                    icon = R.drawable.ic_login,
                    title = androidText(R.string.account_logging_in, account.username),
                    dispatcher = Dispatchers.IO
                ) { task ->
                    try {
                        if (account.isMicrosoftAccount()) {
                            checkMicrosoftAccount(task, account)
                        } else {
                            checkOtherAccount(task, context, account)
                        }
                        AccountsManager.markSessionValidated(account)
                        AccountsManager.suspendSaveAccount(account)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        if (e.isReloginRequired()) throw LaunchReloginRequired(account)
                        throw LaunchCheckFailed(account, e)
                    }
                }
            }

            addTask(
                icon = R.drawable.ic_extension_outlined,
                title = androidText(R.string.launch_check_mods),
                dispatcher = Dispatchers.IO
            ) { task ->
                val patchedManifest = checkMods(version)
                val manifest = patchedManifest ?: VersionInfoParser(version)
                    .setInheriting(skipIfNotExists = true)
                    .build()
                val manifestString = GSON.toJson(manifest)

                launchManifest = manifest
                version.launchManifest = manifestString

                patchedManifest?.let {
                    val libDownloader = GameLibDownloader(
                        downloader = BaseMinecraftDownloader(version.getGameHome()),
                        gameJson = manifestString
                    )
                    libDownloader.schedule(task)
                    libDownloader.download(task)
                }
            }

            if (!version.skipGameIntegrityCheck()) {

                addTask(
                    icon = R.drawable.ic_assignment_filled,
                    title = androidText(R.string.minecraft_download_stat_verify_task),
                    task = createGameDownloadTask(
                        context = context,
                        version = version,
                        onError = onError
                    )
                )
            }

            addTask(
                icon = R.drawable.ic_rocket_launch_filled,
                title = androidText(R.string.main_launch_game)
            ) { _ ->
                launchManifest?.let { manifest ->
                    version.launchManifest = GSON.toJson(
                        VersionInfoParser(version)
                            .setManifest(manifest)
                            .setInheriting()
                            .build()
                    )
                }
                runGame(context, version, account)
                exitActivity()
            }
        }
    }

    private suspend fun checkMicrosoftAccount(task: Task, account: Account) {
        val expired = System.currentTimeMillis() > account.expiresAt - 5 * 60 * 1000
        if (expired || !validateAccessToken(account)) {
            account.refreshMicrosoft(task, currentCoroutineContext())
        }
    }

    private suspend fun checkOtherAccount(task: Task, context: Context, account: Account) {
        task.updateMessage(androidText(R.string.account_logging_in, account.username))
        val helper = AuthServerHelper(
            baseUrl = account.otherBaseUrl!!,
            serverName = account.accountType!!,
            email = account.otherAccount!!,
            password = account.otherPassword!!
        )
        if (!helper.validateOrRefresh(context, account)) {
            helper.passwordLogin(context, account)
        }
    }

    private fun createGameDownloadTask(
        context: Context,
        version: Version,
        onError: (ErrorViewModel.ThrowableMessage) -> Unit
    ): Task {
        return MinecraftDownloader(
            context = context,
            version = version.getVersionInfo()?.minecraftVersion ?: version.getVersionName(),
            customName = version.getVersionName(),
            gameHome = version.getGameHome(),
            mode = DownloadMode.VERIFY_AND_REPAIR,
            onError = { message ->
                onError(
                    ErrorViewModel.ThrowableMessage(
                        title = androidText(R.string.minecraft_download_failed),
                        message = androidText(message)
                    )
                )
            }
        ).getDownloadTask()
    }

    private suspend fun checkMods(version: Version): GameManifest? {
        val modsDir = VersionFolders.MOD.getDir(version.getGameDir())
        val mods = AllModReader(modsDir).readAllLocals()
        version.fpeEmulatorModDetected = detectFpeEmulatorMods(mods)

        if (mods.any { it.id == "touchcontroller" && it.file.isEnabled() }) {
            version.enableTouchProxy = true
        }

        val patched = patchLwjgl3ifyIfNeeded(version, mods)
        return patched
    }
}
