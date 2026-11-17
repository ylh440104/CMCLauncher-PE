package com.cmc.launcher.game.account

import android.content.Context
import com.cmc.launcher.R
import com.cmc.launcher.coroutine.Task
import com.cmc.launcher.coroutine.TaskSystem
import com.cmc.launcher.database.AppDatabase
import com.cmc.launcher.game.account.auth_server.data.AuthServer
import com.cmc.launcher.game.account.auth_server.data.AuthServerDao
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.network.isNetworkAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

private const val TAG = "AccountManager"

object AccountsManager {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _accounts = CopyOnWriteArrayList<Account>()
    private val _accountsFlow = MutableStateFlow<List<Account>>(emptyList())
    val accountsFlow = _accountsFlow.asStateFlow()

    private val _currentAccountFlow = MutableStateFlow<Account?>(null)
    val currentAccountFlow = _currentAccountFlow.asStateFlow()

    private val _authServers = CopyOnWriteArrayList<AuthServer>()
    private val _authServersFlow = MutableStateFlow<List<AuthServer>>(emptyList())
    val authServersFlow = _authServersFlow.asStateFlow()

    private val _refreshWardrobe = MutableStateFlow(false)

    val refreshWardrobe = _refreshWardrobe.asStateFlow()

    private val sessionValidatedAccounts: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private lateinit var database: AppDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var authServerDao: AuthServerDao

    fun initialize(context: Context) {
        database = AppDatabase.getInstance(context)
        accountDao = database.accountDao()
        authServerDao = database.authServerDao()
    }

    fun reloadAccounts() {
        scope.launch {
            suspendReloadAccounts()
        }
    }

    fun refreshWardrobe() {
        _refreshWardrobe.update { !it }
    }

    private suspend fun suspendReloadAccounts() {
        val loadedAccounts = accountDao.getAllAccounts()
        _accounts.clear()
        _accounts.addAll(loadedAccounts)

        _accounts.sortWith(compareBy<Account>(
            { it.accountTypePriority() },
            { it.username },
        ))
        _accountsFlow.value = _accounts.toList()

        if (_accounts.isNotEmpty() && !isAccountExists(AllSettings.currentAccount.getValue())) {
            setCurrentAccountInternal(_accounts[0])
        }

        refreshCurrentAccountState()

        Logger.info(TAG, "Loaded ${_accounts.size} accounts")
    }

    fun reloadAuthServers() {
        scope.launch {
            val loadedServers = authServerDao.getAllServers()
            _authServers.clear()
            _authServers.addAll(loadedServers)

            _authServers.sortWith { o1, o2 -> o1.serverName.compareTo(o2.serverName) }
            _authServersFlow.value = _authServers.toList()

            Logger.info(TAG, "Loaded ${_authServers.size} auth servers")
        }
    }

    fun performLogin(
        context: Context,
        account: Account,
        onSuccess: suspend (Account, task: Task) -> Unit = { _, _ -> },
        onFailed: (th: Throwable) -> Unit = {}
    ) {
        val task = performLoginTask(context, account, onSuccess, onFailed)
        task?.let { TaskSystem.submitTask(it) }
    }

    fun performLoginTask(
        context: Context,
        account: Account,
        onSuccess: suspend (Account, task: Task) -> Unit = { _, _ -> },
        onFailed: (th: Throwable) -> Unit = {},
        onFinally: () -> Unit = {}
    ): Task? =
        when {
            account.isNoLoginRequired() -> null
            account.isAuthServerAccount() -> {
                otherLogin(context = context, account = account, onSuccess = onSuccess, onFailed = onFailed, onFinally = onFinally)
            }
            account.isMicrosoftAccount() -> {
                microsoftRefresh(account = account, onSuccess = onSuccess, onFailed = onFailed, onFinally = onFinally)
            }
            else -> null
        }

    fun refreshAccount(
        context: Context,
        account: Account,
        onFailed: (th: Throwable) -> Unit = {},
    ) {
        if (isNetworkAvailable(context)) {
            performLogin(
                context = context,
                account = account,
                onSuccess = { account, task ->
                    task.updateMessage(androidText(R.string.account_logging_in_saving))
                    account.downloadYggdrasil()
                    markSessionValidated(account)
                    suspendSaveAccount(account)
                },
                onFailed = onFailed
            )
        }
    }

    fun isSessionValidated(account: Account): Boolean =
        sessionValidatedAccounts.contains(account.uniqueUUID)

    fun markSessionValidated(account: Account) {
        sessionValidatedAccounts.add(account.uniqueUUID)
    }

    fun isLaunchCheckNeeded(account: Account): Boolean = when {
        account.isNoLoginRequired() -> false
        account.isMicrosoftAccount() -> !isSessionValidated(account) ||
                System.currentTimeMillis() > account.expiresAt - 5 * 60 * 1000
        else -> !isSessionValidated(account)
    }

    private fun getCurrentAccount(): Account? {
        return _accounts.find {
            it.uniqueUUID == AllSettings.currentAccount.getValue()
        } ?: _accounts.firstOrNull()
    }

    fun setCurrentAccount(account: Account) {
        setCurrentAccountInternal(account)
        refreshCurrentAccountState()
    }

    private fun setCurrentAccountInternal(account: Account) {
        AllSettings.currentAccount.save(account.uniqueUUID)
    }

    private fun refreshCurrentAccountState() {
        _currentAccountFlow.update { getCurrentAccount() }
    }

    fun saveAccount(account: Account) {
        scope.launch {
            suspendSaveAccount(account)
        }
    }

    suspend fun suspendSaveAccount(account: Account) {
        runCatching {
            accountDao.saveAccount(account)
            Logger.info(TAG, "Saved account: ${account.username}")

            setCurrentAccountInternal(account)
        }.onFailure { e ->
            Logger.error(TAG, "Failed to save account: ${account.username}", e)
        }
        suspendReloadAccounts()
    }

    fun deleteAccount(account: Account) {
        scope.launch {
            accountDao.deleteAccount(account)
            val skinFile = account.getSkinFile()
            FileUtils.deleteQuietly(skinFile)
            suspendReloadAccounts()
        }
    }

    suspend fun saveAuthServer(server: AuthServer) {
        runCatching {
            authServerDao.saveServer(server)
            Logger.info(TAG, "Saved auth server: ${server.serverName} -> ${server.baseUrl}")
        }.onFailure { e ->
            Logger.error(TAG, "Failed to save auth server: ${server.serverName}", e)
        }
        reloadAuthServers()
    }

    fun deleteAuthServer(server: AuthServer) {
        scope.launch {
            authServerDao.deleteServer(server)
            reloadAuthServers()
        }
    }

    fun loadFromProfileID(
        profileId: String,
        accountType: String? = null
    ): Account? =
        _accounts.find { it.profileId == profileId && it.accountType == accountType }

    fun isAccountExists(uniqueUUID: String): Boolean {
        return uniqueUUID.isNotEmpty() && _accounts.any { it.uniqueUUID == uniqueUUID }
    }

    fun isAuthServerExists(baseUrl: String): Boolean {
        return baseUrl.isNotEmpty() && _authServers.any { it.baseUrl == baseUrl }
    }
}
