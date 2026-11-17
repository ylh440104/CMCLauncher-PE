package com.cmc.launcher.viewmodel

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cmc.launcher.R
import com.cmc.launcher.context.copyLocalFile
import com.cmc.launcher.coroutine.Task
import com.cmc.launcher.coroutine.TaskSystem
import com.cmc.launcher.game.account.Account
import com.cmc.launcher.game.account.AccountsManager
import com.cmc.launcher.game.account.RegionGate
import com.cmc.launcher.game.account.accountErrorText
import com.cmc.launcher.game.account.addOtherServer
import com.cmc.launcher.game.account.auth_server.AuthServerHelper
import com.cmc.launcher.game.account.auth_server.data.AuthServer
import com.cmc.launcher.game.account.isLocalAccount
import com.cmc.launcher.game.account.isMicrosoftAccount
import com.cmc.launcher.game.account.isReloginRequired
import com.cmc.launcher.game.account.localLogin
import com.cmc.launcher.game.account.microsoft.MINECRAFT_SERVICES_URL
import com.cmc.launcher.game.account.microsoftLogin
import com.cmc.launcher.game.account.refreshMicrosoft
import com.cmc.launcher.game.account.wardrobe.EmptyCape
import com.cmc.launcher.game.account.wardrobe.SkinModelType
import com.cmc.launcher.game.account.wardrobe.SkinType
import com.cmc.launcher.game.account.wardrobe.capeLocalRes
import com.cmc.launcher.game.account.wardrobe.getLocalUUIDWithSkinModel
import com.cmc.launcher.game.account.wardrobe.isSlimModel
import com.cmc.launcher.game.account.wardrobe.isValid
import com.cmc.launcher.game.account.wardrobe.validateSkinFile
import com.cmc.launcher.game.account.yggdrasil.PlayerProfile
import com.cmc.launcher.game.account.yggdrasil.cacheAllCapes
import com.cmc.launcher.game.account.yggdrasil.changeCape
import com.cmc.launcher.game.account.yggdrasil.executeWithAuthorization
import com.cmc.launcher.game.account.yggdrasil.findUsing
import com.cmc.launcher.game.account.yggdrasil.getFile
import com.cmc.launcher.game.account.yggdrasil.getPlayerProfile
import com.cmc.launcher.game.account.yggdrasil.uploadSkin
import com.cmc.launcher.path.PathManager
import com.cmc.launcher.ui.AndroidStringText
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.ui.screens.content.elements.AccountOperation
import com.cmc.launcher.ui.screens.content.elements.AccountSkinOperation
import com.cmc.launcher.ui.screens.content.elements.ChangeCape
import com.cmc.launcher.ui.screens.content.elements.ChangeSkin
import com.cmc.launcher.ui.screens.content.elements.LocalLoginOperation
import com.cmc.launcher.ui.screens.content.elements.LoginMenuOperation
import com.cmc.launcher.ui.screens.content.elements.MicrosoftLoginOperation
import com.cmc.launcher.ui.screens.content.elements.OtherLoginOperation
import com.cmc.launcher.ui.screens.content.elements.ServerOperation
import com.cmc.launcher.utils.network.toLocal
import com.cmc.launcher.utils.string.getMessageOrToString
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.UUID
import io.ktor.client.plugins.ResponseException as KtorResponseException
import kotlinx.coroutines.flow.combine as kotlinxCombine

sealed interface AccountManageIntent {

    data class UpdateLoginMenuOp(val operation: LoginMenuOperation) : AccountManageIntent

    data class UpdateMicrosoftLoginOp(val operation: MicrosoftLoginOperation) :
        AccountManageIntent

    data class UpdateLocalLoginOp(val operation: LocalLoginOperation) : AccountManageIntent
    data class UpdateOtherLoginOp(val operation: OtherLoginOperation) : AccountManageIntent
    data class UpdateServerOp(val operation: ServerOperation) : AccountManageIntent
    data class UpdateAccountOp(val operation: AccountOperation) : AccountManageIntent
    data class UpdateAccountSkinOp(val operation: AccountSkinOperation) :
        AccountManageIntent
    data class UpdatePendingSkinData(val skinState: ChangeSkin) :
        AccountManageIntent
    data class UpdatePendingCapeData(val capeState: ChangeCape) :
        AccountManageIntent
    data class OnSkinPicked(val uri: Uri) : AccountManageIntent
    data object ResetAccountSkinDialogState : AccountManageIntent

    data class PerformMicrosoftLogin(
        val useSystemBrowser: Boolean,
        val toWeb: (url: String) -> Unit,
        val toSystemBrowser: (url: String) -> Unit,
        val backToMain: () -> Unit,
        val checkIfInWebScreen: () -> Boolean
    ) : AccountManageIntent

    data class ApplySkin(val account: Account, val file: File, val model: SkinModelType) : AccountManageIntent

    data class UploadMicrosoftSkin(
        val account: Account,
        val skinFile: File,
        val skinModel: SkinModelType
    ) : AccountManageIntent

    data class FetchMicrosoftCapes(
        val account: Account,
    ) : AccountManageIntent

    data class ApplyMicrosoftCape(
        val account: Account,
        val cape: PlayerProfile.Cape
    ) : AccountManageIntent

    data class CreateLocalAccount(val userName: String, val userUUID: String?) :
        AccountManageIntent

    data class LoginWithOtherServer(
        val server: AuthServer,
        val email: String,
        val pass: String
    ) : AccountManageIntent

    data class AddServer(val url: String) : AccountManageIntent

    data class DeleteServer(val server: AuthServer) : AccountManageIntent

    data class DeleteAccount(val account: Account) : AccountManageIntent

    data class RefreshAccount(val account: Account) : AccountManageIntent

    data class ReloginOtherAccount(
        val account: Account,
        val password: String
    ) : AccountManageIntent

    data class ResetSkin(val account: Account) : AccountManageIntent
}

sealed class AccountManageEffect {

    data class ShowError(val title: AndroidStringText, val message: AndroidStringText) : AccountManageEffect()
}

@HiltViewModel(assistedFactory = AccountManageViewModel.Factory::class)
class AccountManageViewModel @AssistedInject constructor(
    @Assisted private val eventViewModel: EventViewModel,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(eventViewModel: EventViewModel): AccountManageViewModel
    }
    private val _loginMenuOp = MutableStateFlow<LoginMenuOperation>(LoginMenuOperation.None)

    private val _microsoftLoginOp =
        MutableStateFlow<MicrosoftLoginOperation>(MicrosoftLoginOperation.None)
    private val _localLoginOp = MutableStateFlow<LocalLoginOperation>(LocalLoginOperation.None)
    private val _otherLoginOp = MutableStateFlow<OtherLoginOperation>(OtherLoginOperation.None)
    private val _serverOp = MutableStateFlow<ServerOperation>(ServerOperation.None)
    private val _accountOp = MutableStateFlow<AccountOperation>(AccountOperation.None)
    private val _accountSkinOp = MutableStateFlow<AccountSkinOperation>(AccountSkinOperation.None)
    private val _accountSkinDialogState = MutableStateFlow(AccountSkinDialogState())
    private val _accountCapeOpMap = MutableStateFlow<Map<String, List<PlayerProfile.Cape>>>(emptyMap())

    private val _effect = Channel<AccountManageEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    val loginUiState: StateFlow<LoginUiState> = kotlinxCombine(
        _loginMenuOp,
        _microsoftLoginOp,
        _localLoginOp,
        _otherLoginOp
    ) { loginMenuOp, microsoftLoginOp, localLoginOp, otherLoginOp ->
        LoginUiState(
            menuOp = loginMenuOp,
            microsoftOp = microsoftLoginOp,
            localOp = localLoginOp,
            otherOp = otherLoginOp
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LoginUiState()
    )

    data class LoginUiState(
        val menuOp: LoginMenuOperation = LoginMenuOperation.None,
        val microsoftOp: MicrosoftLoginOperation = MicrosoftLoginOperation.None,
        val localOp: LocalLoginOperation = LocalLoginOperation.None,
        val otherOp: OtherLoginOperation = OtherLoginOperation.None
    )

    val profileUiState: StateFlow<ProfileUiState> = kotlinxCombine(
        AccountsManager.accountsFlow,
        AccountsManager.currentAccountFlow,
        AccountsManager.authServersFlow,
        _accountCapeOpMap,
    ) { accounts, currentAccount, authServers, accountCapeOpMap ->
        ProfileUiState(
            accounts = accounts,
            currentAccount = currentAccount,
            authServers = authServers,
            accountCapeOpMap = accountCapeOpMap,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState()
    )

    data class ProfileUiState(
        val accounts: List<Account> = emptyList(),
        val currentAccount: Account? = null,
        val authServers: List<AuthServer> = emptyList(),
        val accountCapeOpMap: Map<String, List<PlayerProfile.Cape>> = emptyMap(),
    )

    data class AccountSkinDialogState(
        val pendingSkinData: ChangeSkin = ChangeSkin.None,
        val pendingCapeData: ChangeCape = ChangeCape.None,
        val importingSkin: Boolean = false
    )

    val operationUiState: StateFlow<OperationUiState> = kotlinxCombine(
        _serverOp,
        _accountOp,
        _accountSkinOp,
        _accountSkinDialogState
    ) { serverOp, accountOp, accountSkinOp, accountSkinDialogState ->
        OperationUiState(serverOp, accountOp, accountSkinOp, accountSkinDialogState)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OperationUiState()
    )

    data class OperationUiState(
        val serverOp: ServerOperation = ServerOperation.None,
        val accountOp: AccountOperation = AccountOperation.None,
        val accountSkinOp: AccountSkinOperation = AccountSkinOperation.None,
        val accountSkinDialogState: AccountSkinDialogState = AccountSkinDialogState()
    )

    fun onIntent(intent: AccountManageIntent) {
        when (intent) {
            is AccountManageIntent.UpdateLoginMenuOp ->
                _loginMenuOp.value = intent.operation

            is AccountManageIntent.UpdateMicrosoftLoginOp ->
                _microsoftLoginOp.value = intent.operation

            is AccountManageIntent.UpdateLocalLoginOp -> _localLoginOp.value = intent.operation
            is AccountManageIntent.UpdateOtherLoginOp -> _otherLoginOp.value = intent.operation
            is AccountManageIntent.UpdateServerOp -> _serverOp.value = intent.operation
            is AccountManageIntent.UpdateAccountOp -> _accountOp.value = intent.operation
            is AccountManageIntent.UpdateAccountSkinOp -> {
                _accountSkinOp.value = intent.operation
            }

            is AccountManageIntent.UpdatePendingSkinData -> {
                _accountSkinDialogState.update {
                    it.copy(
                        pendingSkinData = intent.skinState
                    )
                }
            }

            is AccountManageIntent.UpdatePendingCapeData -> {
                _accountSkinDialogState.update {
                    it.copy(
                        pendingCapeData = intent.capeState
                    )
                }
            }

            is AccountManageIntent.OnSkinPicked -> onSkinPicked(intent)
            is AccountManageIntent.ResetAccountSkinDialogState -> {
                _accountSkinDialogState.update { AccountSkinDialogState() }
            }

            is AccountManageIntent.PerformMicrosoftLogin -> performMicrosoftLogin(intent)
            is AccountManageIntent.ApplySkin ->
                applySkin(intent.account, intent.file, intent.model)

            is AccountManageIntent.UploadMicrosoftSkin -> uploadMicrosoftSkin(intent)
            is AccountManageIntent.FetchMicrosoftCapes -> fetchMicrosoftCapes(intent.account)
            is AccountManageIntent.ApplyMicrosoftCape -> applyMicrosoftCape(intent)
            is AccountManageIntent.CreateLocalAccount -> createLocalAccount(
                intent.userName,
                intent.userUUID
            )

            is AccountManageIntent.LoginWithOtherServer -> loginWithOtherServer(intent)
            is AccountManageIntent.AddServer -> addServer(intent.url)
            is AccountManageIntent.DeleteServer -> deleteServer(intent.server)
            is AccountManageIntent.DeleteAccount -> deleteAccount(intent.account)
            is AccountManageIntent.RefreshAccount -> refreshAccount(intent.account)
            is AccountManageIntent.ReloginOtherAccount -> reloginOtherAccount(intent)
            is AccountManageIntent.ResetSkin -> resetSkin(intent.account)
        }
    }

    private fun onSkinPicked(intent: AccountManageIntent.OnSkinPicked) {
        viewModelScope.launch(Dispatchers.IO) {
            _accountSkinDialogState.update {
                it.copy(importingSkin = true)
            }

            val cacheFile = File(
                PathManager.DIR_IMAGE_CACHE,
                "skin_pick_${UUID.randomUUID()}"
            )

            runCatching {
                context.copyLocalFile(intent.uri, cacheFile)
                validateSkinFile(cacheFile)
            }.onSuccess { skinType ->
                val data = when (skinType) {
                    SkinType.DualLayer -> {
                        val type = if (cacheFile.isSlimModel()) {
                            SkinModelType.ALEX
                        } else {
                            SkinModelType.STEVE
                        }
                        ChangeSkin.SkinModel.Model(type)
                    }
                    SkinType.Classic -> ChangeSkin.SkinModel.Rejected
                    SkinType.Invalid -> {
                        emitError(
                            androidText(R.string.generic_warning),
                            androidText(R.string.account_change_skin_invalid)
                        )
                        return@onSuccess
                    }
                }

                _accountSkinDialogState.update {
                    it.copy(
                        pendingSkinData = ChangeSkin.ChangeSkinData(
                            cacheFile = cacheFile,
                            data = data
                        )
                    )
                }
            }.onFailure { th ->
                emitError(
                    androidText(R.string.account_change_skin_failed_to_import),
                    androidText(th.getMessageOrToString())
                )
            }

            _accountSkinDialogState.update {
                it.copy(importingSkin = false)
            }
        }
    }

    private fun emitError(title: AndroidStringText, message: AndroidStringText) {
        viewModelScope.launch(Dispatchers.Main) {
            _effect.send(AccountManageEffect.ShowError(title, message))
        }
    }

    private fun emitToast(
        text: AndroidStringText,
        duration: Int = Toast.LENGTH_SHORT
    ) {
        eventViewModel.sendToast(text, duration)
    }

    private fun performMicrosoftLogin(intent: AccountManageIntent.PerformMicrosoftLogin) {
        microsoftLogin(
            context,
            useSystemBrowser = intent.useSystemBrowser,
            toWeb = intent.toWeb,
            toSystemBrowser = intent.toSystemBrowser,
            backToMain = intent.backToMain,
            checkIfInWebScreen = intent.checkIfInWebScreen,
            { onIntent(AccountManageIntent.UpdateMicrosoftLoginOp(it)) },
            showToast = ::emitToast,
            { emitError(it.title, it.message) }
        )
        onIntent(AccountManageIntent.UpdateMicrosoftLoginOp(MicrosoftLoginOperation.None))
    }

    private fun applySkin(account: Account, file: File, model: SkinModelType) {
        when {
            account.isLocalAccount() -> saveLocalSkin(account, file, model)
            account.isMicrosoftAccount() -> importSkinFile(account, file, model)
        }
    }

    private fun importSkinFile(account: Account, file: File, model: SkinModelType) {
        TaskSystem.submitTask(
            Task.runTask(
                id = account.uniqueUUID,
                dispatcher = Dispatchers.IO,
                task = {
                    if (validateSkinFile(file).isValid()) {
                        onIntent(
                            AccountManageIntent.UploadMicrosoftSkin(
                                account = account,
                                skinFile = file,
                                skinModel = model
                            )
                        )
                    } else {
                        FileUtils.deleteQuietly(file)
                        emitError(
                            androidText(R.string.generic_warning),
                            androidText(R.string.account_change_skin_invalid)
                        )
                    }
                },
                onError = { th ->
                    FileUtils.deleteQuietly(file)
                    emitError(
                        androidText(R.string.account_change_skin_failed_to_import),
                        androidText(th.getMessageOrToString())
                    )
                }
            )
        )
    }

    private fun uploadMicrosoftSkin(intent: AccountManageIntent.UploadMicrosoftSkin) {
        val account = intent.account
        val skinFile = intent.skinFile
        val skinModel = intent.skinModel

        TaskSystem.submitTask(
            Task.runTask(
                dispatcher = Dispatchers.IO,
                task = { task ->
                    executeWithAuthorization(block = {
                        task.updateProgress(-1f)
                        task.updateMessage(androidText(R.string.account_change_skin_uploading))
                        uploadSkin(MINECRAFT_SERVICES_URL, account.accessToken, skinFile, skinModel)
                    }, onRefreshRequest = {
                        account.refreshMicrosoft(task = task, coroutineContext = coroutineContext)
                        AccountsManager.suspendSaveAccount(account)
                    })

                    task.updateMessage(androidText(R.string.account_change_skin_update_local))
                    runCatching {
                        account.downloadYggdrasil()
                    }.onFailure { th ->
                        emitError(
                            androidText(R.string.account_logging_in_failed),
                            formatAccountError(th)
                        )
                    }

                    emitToast(
                        androidText(R.string.account_change_skin_update_toast),
                        duration = Toast.LENGTH_LONG
                    )
                },
                onError = { th ->
                    when {
                        th.isReloginRequired() -> {
                            onIntent(
                                AccountManageIntent.UpdateAccountOp(
                                    AccountOperation.OnRelogin(account)
                                )
                            )
                        }
                        th is KtorResponseException -> {
                            emitError(
                                androidText(
                                    R.string.account_change_skin_failed_to_upload,
                                    th.response.status.value
                                ),
                                th.toLocal()
                            )
                        }
                        else -> {
                            emitError(
                                androidText(R.string.generic_error),
                                formatAccountError(th)
                            )
                        }
                    }
                }
            )
        )
    }

    private fun fetchMicrosoftCapes(account: Account) {
        TaskSystem.submitTask(
            Task.runTask(
                id = account.uniqueUUID,
                dispatcher = Dispatchers.IO,
                task = { task ->
                    executeWithAuthorization(block = {
                        task.updateProgress(-1f)
                        task.updateMessage(androidText(R.string.account_change_cape_fetch_all))
                        val profile = getPlayerProfile(MINECRAFT_SERVICES_URL, account.accessToken)
                        task.updateProgress(-1f)
                        task.updateMessage(androidText(R.string.account_change_cape_cache_all))
                        cacheAllCapes(profile)

                        runCatching {
                            account.downloadYggdrasil(refresh = false)
                        }.onFailure { e ->
                            if (e is CancellationException) throw e
                        }

                        val activeCape = profile.capes.findUsing()
                        val targetCape = account.getCapeFile()
                        if (activeCape != null) {
                            val activeCapeFile = activeCape.getFile(PathManager.DIR_ACCOUNT_CAPE)
                            if (activeCapeFile.exists()) {
                                runCatching {
                                    activeCapeFile.copyTo(targetCape, overwrite = true)
                                }
                            }
                        } else {
                            FileUtils.deleteQuietly(targetCape)
                        }

                        _accountCapeOpMap.update { it + (account.uniqueUUID to profile.capes) }
                        AccountsManager.refreshWardrobe()
                    }, onRefreshRequest = {
                        account.refreshMicrosoft(task = task, coroutineContext = coroutineContext)
                        AccountsManager.suspendSaveAccount(account)
                    })
                },
                onError = { th ->
                    if (th.isReloginRequired()) {
                        onIntent(
                            AccountManageIntent.UpdateAccountOp(
                                AccountOperation.OnRelogin(account)
                            )
                        )
                    } else {
                        emitError(
                            androidText(R.string.account_change_cape_fetch_all_failed),
                            androidText(th.getMessageOrToString())
                        )
                    }
                }
            )
        )
    }

    private fun applyMicrosoftCape(intent: AccountManageIntent.ApplyMicrosoftCape) {
        val account = intent.account
        val cape = intent.cape
        val capeId = cape.id
        val isReset = cape == EmptyCape

        TaskSystem.submitTask(
            Task.runTask(
                id = account.uniqueUUID + "_cape",
                dispatcher = Dispatchers.IO,
                task = { task ->
                    executeWithAuthorization(block = {
                        task.updateMessage(androidText(R.string.account_change_cape_apply))
                        changeCape(
                            MINECRAFT_SERVICES_URL,
                            account.accessToken,
                            capeId
                        )
                    }, onRefreshRequest = {
                        account.refreshMicrosoft(task = task, coroutineContext = coroutineContext)
                        AccountsManager.suspendSaveAccount(account)
                    })

                    val capeFile = cape.getFile(PathManager.DIR_ACCOUNT_CAPE)
                    val targetCape = account.getCapeFile()
                    FileUtils.deleteQuietly(targetCape)
                    if (!isReset && capeFile.exists()) {
                        runCatching {
                            capeFile.copyTo(targetCape)
                        }
                    }

                    AccountsManager.refreshWardrobe()

                    _accountCapeOpMap.update { capesMap ->
                        if (!capesMap.containsKey(account.uniqueUUID)) return@update capesMap
                        buildMap {
                            capesMap.forEach { (accountId, capes) ->
                                if (accountId == account.uniqueUUID) {
                                    val newList = capes.map { cape ->
                                        when {
                                            cape.id == capeId -> cape.copy(state = "ACTIVE")
                                            cape.state == "ACTIVE" -> cape.copy(state = "INACTIVE")
                                            else -> cape
                                        }
                                    }
                                    put(accountId, newList)
                                } else put(accountId, capes)
                            }
                        }
                    }

                    if (isReset) {
                        emitToast(androidText(R.string.account_change_cape_apply_reset))
                    } else {
                        val capeName = cape.capeLocalRes()?.let { localRes ->
                            androidText(localRes)
                        } ?: androidText(cape.alias)
                        emitToast(
                            androidText(
                                R.string.account_change_cape_apply_success,
                                capeName
                            )
                        )
                    }
                },
                onError = { th ->
                    when {
                        th.isReloginRequired() -> {
                            onIntent(
                                AccountManageIntent.UpdateAccountOp(
                                    AccountOperation.OnRelogin(account)
                                )
                            )
                        }
                        th is KtorResponseException -> {
                            emitError(
                                androidText(
                                    R.string.account_change_cape_apply_failed,
                                    th.response.status.value
                                ),
                                th.toLocal()
                            )
                        }
                        else -> {
                            emitError(
                                androidText(R.string.generic_error),
                                formatAccountError(th)
                            )
                        }
                    }
                }
            )
        )
    }

    private fun createLocalAccount(userName: String, userUUID: String?) {
        if (!RegionGate.canUseOffline(context)) {
            onIntent(AccountManageIntent.UpdateLocalLoginOp(LocalLoginOperation.None))
            return
        }
        localLogin(userName, userUUID)
        onIntent(AccountManageIntent.UpdateLocalLoginOp(LocalLoginOperation.None))
    }

    private fun loginWithOtherServer(intent: AccountManageIntent.LoginWithOtherServer) {
        AuthServerHelper(intent.server, intent.email, intent.pass, onSuccess = { account, task ->
            task.updateMessage(androidText(R.string.account_logging_in_saving))
            account.downloadYggdrasil()
            AccountsManager.suspendSaveAccount(account)
        }, onFailed = {
            onIntent(AccountManageIntent.UpdateOtherLoginOp(OtherLoginOperation.OnFailed(it)))
        }).createNewAccount(context) { profiles, select ->
            onIntent(
                AccountManageIntent.UpdateOtherLoginOp(
                    OtherLoginOperation.SelectRole(profiles, select)
                )
            )
        }
    }

    private fun addServer(url: String) {
        addOtherServer(url) {
            onIntent(AccountManageIntent.UpdateServerOp(ServerOperation.OnThrowable(it)))
        }
        onIntent(AccountManageIntent.UpdateServerOp(ServerOperation.None))
    }

    private fun deleteServer(server: AuthServer) {
        AccountsManager.deleteAuthServer(server)
        onIntent(AccountManageIntent.UpdateServerOp(ServerOperation.None))
    }

    private fun deleteAccount(account: Account) {
        AccountsManager.deleteAccount(account)
        onIntent(AccountManageIntent.UpdateAccountOp(AccountOperation.None))
    }

    private fun refreshAccount(account: Account) {
        AccountsManager.refreshAccount(context, account) { th ->
            onIntent(
                AccountManageIntent.UpdateAccountOp(
                    if (th.isReloginRequired()) {
                        AccountOperation.OnRelogin(account)
                    } else {
                        AccountOperation.OnFailed(th)
                    }
                )
            )
        }
    }

    private fun reloginOtherAccount(intent: AccountManageIntent.ReloginOtherAccount) {
        val account = intent.account
        AuthServerHelper(
            baseUrl = account.otherBaseUrl!!,
            serverName = account.accountType!!,
            email = account.otherAccount!!,
            password = intent.password,
            onSuccess = { acc, task ->
                task.updateMessage(androidText(R.string.account_logging_in_saving))
                acc.downloadYggdrasil()
                AccountsManager.markSessionValidated(acc)
                AccountsManager.suspendSaveAccount(acc)
                onIntent(AccountManageIntent.UpdateAccountOp(AccountOperation.None))
            },
            onFailed = { th ->
                onIntent(
                    AccountManageIntent.UpdateAccountOp(
                        AccountOperation.OnRelogin(account, error = th)
                    )
                )
            }
        ).let { helper ->
            TaskSystem.submitTask(helper.justLogin(context, account))
        }
    }

    private fun saveLocalSkin(account: Account, file: File, model: SkinModelType) {
        val skinFile = account.getSkinFile()

        TaskSystem.submitTask(Task.runTask(dispatcher = Dispatchers.IO, task = {
            if (validateSkinFile(file).isValid()) {
                account.skinModelType = model
                account.profileId = getLocalUUIDWithSkinModel(account.username, model)
                file.copyTo(skinFile, true)
                FileUtils.deleteQuietly(file)
                AccountsManager.suspendSaveAccount(account)
                AccountsManager.refreshWardrobe()
                onIntent(
                    AccountManageIntent.UpdateAccountSkinOp(
                        AccountSkinOperation.None
                    )
                )
            } else {
                emitError(
                    androidText(R.string.generic_warning),
                    androidText(R.string.account_change_skin_invalid)
                )
                onIntent(
                    AccountManageIntent.UpdateAccountSkinOp(
                        AccountSkinOperation.None
                    )
                )
            }
        }, onError = { th ->
            FileUtils.deleteQuietly(file)
            emitError(androidText(R.string.error_import_image), androidText(th.getMessageOrToString()))
            AccountsManager.refreshWardrobe()
            onIntent(
                AccountManageIntent.UpdateAccountSkinOp(
                    AccountSkinOperation.None
                )
            )
        }))
    }

    private fun resetSkin(account: Account) {
        TaskSystem.submitTask(Task.runTask(dispatcher = Dispatchers.IO, task = {
            account.apply {
                FileUtils.deleteQuietly(getSkinFile())
                skinModelType = SkinModelType.NONE
                profileId = getLocalUUIDWithSkinModel(username, skinModelType)
                AccountsManager.suspendSaveAccount(this)
                AccountsManager.refreshWardrobe()
            }
        }))
        onIntent(
            AccountManageIntent.UpdateAccountSkinOp(
                AccountSkinOperation.None
            )
        )
    }

    fun formatAccountError(th: Throwable): AndroidStringText = accountErrorText(th)
}
