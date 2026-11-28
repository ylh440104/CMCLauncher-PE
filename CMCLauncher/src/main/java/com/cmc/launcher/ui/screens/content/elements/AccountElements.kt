package com.cmc.launcher.ui.screens.content.elements

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.cmc.launcher.ui.theme.Alpha
import com.cmc.launcher.ui.theme.FieldShape
import com.cmc.launcher.ui.theme.Size
import com.cmc.launcher.ui.theme.Space
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.cmc.launcher.ui.components.StaticBusyIndicator
import com.cmc.launcher.ui.components.StaticDropdownMenu as DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cmc.launcher.ui.components.StaticDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.petlib.ArmModel
import com.movtery.petlib.PetAnimation
import com.cmc.launcher.BuildKeys
import com.cmc.launcher.R
import com.cmc.launcher.game.account.Account
import com.cmc.launcher.game.account.AccountType
import com.cmc.launcher.game.account.AccountsManager
import com.cmc.launcher.game.account.accountErrorText
import com.cmc.launcher.game.account.accountUUID
import com.cmc.launcher.game.account.auth_server.data.AuthServer
import com.cmc.launcher.game.account.auth_server.models.AuthResult
import com.cmc.launcher.game.account.getAccountTypeName
import com.cmc.launcher.game.account.getUUIDFromUserName
import com.cmc.launcher.game.account.isLocalAccount
import com.cmc.launcher.game.account.isMicrosoftAccount
import com.cmc.launcher.game.account.isSkinChangeAllowed
import com.cmc.launcher.game.account.wardrobe.EmptyCape
import com.cmc.launcher.game.account.wardrobe.SkinModelType
import com.cmc.launcher.game.account.wardrobe.capeLocalRes
import com.cmc.launcher.game.account.wardrobe.toArmModel
import com.cmc.launcher.game.account.yggdrasil.PlayerProfile
import com.cmc.launcher.game.account.yggdrasil.findUsing
import com.cmc.launcher.game.account.yggdrasil.getFile
import com.cmc.launcher.path.PathManager
import com.cmc.launcher.path.URL_MINECRAFT_PURCHASE
import com.cmc.launcher.ui.AndroidStringText
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.ui.components.BaseIconTextButton
import com.cmc.launcher.ui.components.ButtonPosition
import com.cmc.launcher.ui.components.IconTextButton
import com.cmc.launcher.ui.components.ImePanContainer
import com.cmc.launcher.ui.components.MarqueeText
import com.cmc.launcher.ui.components.OwnOutlinedTextField
import com.cmc.launcher.ui.components.PetModelView
import com.cmc.launcher.ui.components.PositionButton
import com.cmc.launcher.ui.components.PositionFilledTonalButton
import com.cmc.launcher.ui.components.RadioCard
import com.cmc.launcher.ui.components.SimpleAlertDialog
import com.cmc.launcher.ui.components.SimpleListDialog
import com.cmc.launcher.ui.components.SingleLineTextCheck
import com.cmc.launcher.ui.components.fadeEdge
import com.cmc.launcher.ui.components.rememberDialogMaxHeight
import com.cmc.launcher.ui.components.verticalScrollWithBar
import com.cmc.launcher.ui.screens.content.elements.ChangeSkin.SkinModel
import com.cmc.launcher.ui.screens.main.control_editor.InfoLayoutTextItem
import com.cmc.launcher.ui.theme.cardColor
import com.cmc.launcher.ui.theme.itemColor
import com.cmc.launcher.ui.theme.onCardColor
import com.cmc.launcher.ui.theme.onItemColor
import com.cmc.launcher.utils.animation.getAnimateTween
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.regex.Pattern
import kotlin.math.roundToInt

private const val TAG = "AccountElements"

sealed interface LoginMenuOperation {
    data object None : LoginMenuOperation

    data object Login : LoginMenuOperation
}

sealed interface MicrosoftLoginOperation {
    data object None : MicrosoftLoginOperation

    data object Tip : MicrosoftLoginOperation

    data object SelectBrowser : MicrosoftLoginOperation
}

sealed interface LocalLoginOperation {
    data object None : LocalLoginOperation

    data object Edit : LocalLoginOperation

    data class Create(val userName: String, val userUUID: String?) : LocalLoginOperation

    data class Alert(val userName: String, val userUUID: String?) : LocalLoginOperation
}

sealed interface ServerOperation {
    data object None : ServerOperation

    data object AddNew : ServerOperation

    data class Delete(val server: AuthServer) : ServerOperation
    data class OnThrowable(val throwable: Throwable) : ServerOperation
}

sealed interface AccountOperation {
    data object None : AccountOperation
    data class Delete(val account: Account) : AccountOperation
    data class OnFailed(val th: Throwable) : AccountOperation

    data class OnRelogin(
        val account: Account,
        val logging: Boolean = false,
        val error: Throwable? = null
    ) : AccountOperation
}

sealed interface AccountSkinOperation {
    data object None : AccountSkinOperation

    data class ChangeSkin(val account: Account) : AccountSkinOperation
}

sealed interface OtherLoginOperation {
    data object None : OtherLoginOperation

    data class OnLogin(val server: AuthServer) : OtherLoginOperation

    data class OnFailed(val th: Throwable) : OtherLoginOperation

    data class SelectRole(
        val profiles: List<AuthResult.AvailableProfiles>,
        val selected: (AuthResult.AvailableProfiles) -> Unit
    ) : OtherLoginOperation
}

@Composable
fun PlayerFace(
    modifier: Modifier = Modifier,
    account: Account,
    avatarSize: Dp = 64.dp,
    refreshKey: Any? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val refreshWardrobe by AccountsManager.refreshWardrobe.collectAsStateWithLifecycle()
    val avatarBitmap by produceState<ImageBitmap?>(null, account, refreshKey, refreshWardrobe, density) {
        value = withContext(Dispatchers.IO) {
            getSkinAvatarFromAccount(
                context = context,
                account = account,
                size = avatarSize,
                density = density
            ).asImageBitmap()
        }
    }

    val avatar = avatarBitmap
    if (avatar != null) {
        Image(
            modifier = modifier.size(avatarSize),
            bitmap = avatar,
            contentDescription = null
        )
    } else {
        Spacer(modifier = modifier.size(avatarSize))
    }
}

@Composable
fun AccountItem(
    modifier: Modifier = Modifier,
    currentAccount: Account?,
    account: Account,
    avatarSize: Dp = 46.dp,
    color: Color = itemColor(),
    contentColor: Color = onItemColor(),
    enabled: Boolean = true,
    refreshKey: Any? = null,
    onSelected: (Account) -> Unit = {},
    openChangeSkinDialog: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onCopyUUID: () -> Unit = {},
    onDeleteClick: () -> Unit = {}
) {
    val active = currentAccount?.uniqueUUID == account.uniqueUUID
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember(account.uniqueUUID) { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        color = color,
        contentColor = contentColor,
        shape = FieldShape,
        border = if (active) BorderStroke(Size.stroke, colors.primary.copy(alpha = Alpha.strong)) else null
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f)
                    .selectable(
                        selected = active, enabled = enabled, role = Role.RadioButton,
                        interactionSource = null, indication = null,
                        onClick = { if (!active) onSelected(account) }
                    )
                    .heightIn(min = Size.touch)
                    .padding(horizontal = Space.md, vertical = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerFace(account = account, avatarSize = avatarSize, refreshKey = refreshKey)
                Column(Modifier.weight(1f)) {
                    Text(account.username, style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(getAccountTypeName(account), style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (active) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(Size.iconSm), tint = colors.primary)
            }
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = enabled) {
                    Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.generic_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.generic_refresh)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_refresh), null) },
                        enabled = account.accountType != AccountType.LOCAL.tag,
                        onClick = { menuOpen = false; onRefreshClick() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.account_wardrobe)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_checkroom), null) },
                        enabled = account.isSkinChangeAllowed(),
                        onClick = { menuOpen = false; openChangeSkinDialog() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.account_local_uuid_copy)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_copy_all_outlined), null) },
                        onClick = { menuOpen = false; onCopyUUID() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.generic_delete), color = colors.error) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_delete_outlined), null, tint = colors.error) },
                        onClick = { menuOpen = false; onDeleteClick() }
                    )
                }
            }
        }
    }
}

@Composable
fun LoginMenuDialog(
    onDismissRequest: () -> Unit,
    authServers: List<AuthServer>,
    onAuthServerLogin: (server: AuthServer) -> Unit,
    onAddAuthServer: () -> Unit,
    onDeleteAuthServer: (server: AuthServer) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(all = 12.dp)
                .heightIn(max = rememberDialogMaxHeight())
                .fillMaxHeight()
                .fillMaxWidth(0.94f),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(all = 6.dp)
                    .fillMaxWidth()
                    .heightIn(max = (maxHeight - 12.dp).coerceAtMost(rememberDialogMaxHeight())),
                shape = MaterialTheme.shapes.extraLarge,
                color = cardColor(false),
                contentColor = onCardColor(),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 12.dp,
                            end = 12.dp,
                            bottom = 12.dp
),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            InfoLayoutTextItem(
                                modifier = Modifier.fillMaxWidth(),
                                title = stringResource(R.string.account_add_new_server_button),
                                showArrow = true,
                                onClick = {
                                    onAddAuthServer()
                                    onDismissRequest()
                                }
                            )
                        }

                        items(authServers, key = { it.serverName }) { server ->
                            LoginItem(
                                modifier = Modifier.fillMaxWidth(),
                                title = server.serverName,
                                icon = {
                                    IconButton(
                                        modifier = Modifier.size(22.dp),
                                        onClick = {
                                            onDeleteAuthServer(server)
                                        }
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_delete_outlined),
                                            contentDescription = stringResource(R.string.generic_delete)
                                        )
                                    }
                                },
                                onClick = {
                                    onAuthServerLogin(server)
                                    onDismissRequest()
                                }
                            )
                        }
                    }

                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                        onClick = onDismissRequest
                    ) {
                        Text(stringResource(R.string.generic_close))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 480)
@Composable
private fun PreviewLoginMenuDialog() {
    MaterialExpressiveTheme {
        LoginMenuDialog(
            onDismissRequest = {},
            authServers = emptyList(),
            onAuthServerLogin = {},
            onAddAuthServer = {},
            onDeleteAuthServer = {}
        )
    }
}

@Composable
fun LoginItem(
    modifier: Modifier = Modifier,
    title: String,
    icon: @Composable () -> Unit = @Composable {
        Icon(
            modifier = Modifier.size(22.dp),
            painter = painterResource(R.drawable.ic_login),
            contentDescription = null
        )
    },
    onClick: () -> Unit
) {
    InfoLayoutTextItem(
        modifier = modifier,
        title = title,
        icon = icon,
        onClick = onClick
    )
}

@Preview(showBackground = true, widthDp = 400, heightDp = 120)
@Composable
private fun PreviewLoginItem() {
    MaterialExpressiveTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LoginItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 32.dp),
                title = stringResource(R.string.account_type_microsoft),
                onClick = {}
            )
        }
    }
}

@Composable
fun MicrosoftLoginTipDialog(
    onDismissRequest: () -> Unit = {},
    onConfirm: () -> Unit = {},
    openLink: (url: String) -> Unit = {}
) {
    SimpleAlertDialog(
        title = stringResource(R.string.account_supporting_microsoft_tip_title),
        text = {
            Text(
                text = stringResource(R.string.account_supporting_microsoft_tip_link_text),
                style = MaterialTheme.typography.bodyMedium
            )
            FlowRow {
                IconTextButton(
                    onClick = {
                        openLink(URL_MINECRAFT_PURCHASE)
                    },
                    painter = painterResource(R.drawable.ic_link),
                    contentDescription = null,
                    text = stringResource(R.string.account_supporting_microsoft_tip_link_purchase)
                )
                IconTextButton(
                    onClick = {
                        openLink("https://www.minecraft.net/msaprofile/mygames/editprofile")
                    },
                    painter = painterResource(R.drawable.ic_link),
                    contentDescription = null,
                    text = stringResource(R.string.account_supporting_microsoft_tip_link_make_gameid)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.account_supporting_microsoft_tip_hint_t1),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.account_supporting_microsoft_tip_hint_t2))
                    append(
                        stringResource(
                            R.string.account_supporting_microsoft_tip_hint_t3,
                            BuildKeys.LAUNCHER_NAME
                        )
                    )
                    append(stringResource(R.string.account_supporting_microsoft_tip_hint_t4))
                    append(stringResource(R.string.account_supporting_microsoft_tip_hint_t5))
                    append(stringResource(R.string.account_supporting_microsoft_tip_hint_t6))
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.account_supporting_microsoft_tip_hint_t7))
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(stringResource(R.string.account_supporting_microsoft_tip_hint_t8))
                    }
                },
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmText = stringResource(R.string.account_login),
        onConfirm = onConfirm,
        onCancel = onDismissRequest,
        onDismissRequest = onDismissRequest
    )
}

@Composable
fun MicrosoftLoginMethodDialog(
    onDismissRequest: () -> Unit,
    onSystemBrowser: () -> Unit,
    onInternalBrowser: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .heightIn(max = rememberDialogMaxHeight())
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(all = 6.dp)
                    .heightIn(max = (maxHeight - 12.dp).coerceAtMost(rememberDialogMaxHeight()))
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.extraLarge,
                color = cardColor(false),
                contentColor = onCardColor(),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(all = 16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.account_supporting_microsoft_tip_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.account_microsoft_login_method_summary),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        PositionButton(
                            modifier = Modifier.fillMaxWidth(),
                            position = ButtonPosition.Top,
                            onClick = onSystemBrowser
                        ) {
                            MarqueeText(text = stringResource(R.string.account_microsoft_login_method_system_browser))
                        }
                        PositionFilledTonalButton(
                            modifier = Modifier.fillMaxWidth(),
                            position = ButtonPosition.Middle,
                            onClick = onInternalBrowser
                        ) {
                            MarqueeText(text = stringResource(R.string.account_microsoft_login_method_internal_browser))
                        }
                        PositionFilledTonalButton(
                            modifier = Modifier.fillMaxWidth(),
                            position = ButtonPosition.Bottom,
                            onClick = onDismissRequest
                        ) {
                            MarqueeText(text = stringResource(R.string.generic_cancel))
                        }
                    }
                }
            }
        }
    }
}

private val localNamePattern = Pattern.compile("[^a-zA-Z0-9_]")

@Composable
fun LocalLoginDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (isUserNameInvalid: Boolean, userName: String, userUUID: String?) -> Unit,
    openLink: (url: String) -> Unit
) {

    var userName by rememberSaveable { mutableStateOf("") }

    var isUserNameInvalid by rememberSaveable { mutableStateOf(false) }

    var userEditedUUID by rememberSaveable { mutableStateOf(false) }

    var userUUID by rememberSaveable { mutableStateOf("") }

    val pendingUUID = remember(userName) {
        runCatching {
            getUUIDFromUserName(userName).toString()
        }.getOrElse {
            ""
        }.also { uuid ->
            if (!userEditedUUID) userUUID = uuid
        }
    }

    val isUserUUIDInvalid: Boolean = remember(userUUID) {
        userUUID.isNotEmpty() && runCatching {
            accountUUID(userUUID)
            false
        }.getOrElse {
            true
        }
    }

    var editUUID by rememberSaveable { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(decorFitsSystemWindows = false)
    ) {
        ImePanContainer(
            modifier = Modifier
                .heightIn(max = rememberDialogMaxHeight())
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(all = 6.dp)
                    .heightIn(max = (maxHeight - 12.dp).coerceAtMost(rememberDialogMaxHeight()))
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.extraLarge,
                color = cardColor(false),
                contentColor = onCardColor(),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.account_local_create_account),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.size(16.dp))

                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fadeEdge(state = scrollState)
                            .weight(1f, fill = false)
                            .verticalScrollWithBar(state = scrollState)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SingleLineTextCheck(
                            text = userName,
                            onSingleLined = { userName = it }
                        )

                        OwnOutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = userName,
                            onValueChange = {
                                userName = it
                            },
                            isError = isUserNameInvalid,
                            label = { Text(text = stringResource(R.string.account_label_username)) },
                            supportingText = {
                                val errorText = when {
                                    userName.isEmpty() -> stringResource(R.string.account_supporting_username_invalid_empty)
                                    userName.length <= 2 -> stringResource(R.string.account_supporting_username_invalid_short)
                                    userName.length > 16 -> stringResource(R.string.account_supporting_username_invalid_long)
                                    localNamePattern.matcher(userName)
                                        .find() -> stringResource(R.string.account_supporting_username_invalid_illegal_characters)

                                    else -> ""
                                }.also {
                                    isUserNameInvalid = it.isNotEmpty()
                                }
                                if (isUserNameInvalid) {
                                    Text(text = errorText)
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.large
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconTextButton(
                                onClick = {
                                    openLink(URL_MINECRAFT_PURCHASE)
                                },
                                painter = painterResource(R.drawable.ic_link),
                                contentDescription = null,
                                text = stringResource(R.string.account_supporting_microsoft_tip_link_purchase)
                            )

                            BaseIconTextButton(
                                onClick = {
                                    editUUID = !editUUID
                                },
                                icon = { iconModifier ->
                                    Icon(
                                        modifier = iconModifier.size(24.dp),
                                        painter = painterResource(if (editUUID) R.drawable.ic_arrow_drop_up_rounded else R.drawable.ic_arrow_drop_down_rounded),
                                        contentDescription = null
                                    )
                                },
                                text = stringResource(R.string.account_advanced)
                            )
                        }

                        if (editUUID)  {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Spacer(modifier = Modifier.size(8.dp))

                                SingleLineTextCheck(
                                    text = userUUID,
                                    onSingleLined = { userUUID = it }
                                )

                                OwnOutlinedTextField(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = userUUID,
                                    onValueChange = {
                                        userUUID = it
                                        userEditedUUID = true
                                    },
                                    isError = isUserUUIDInvalid,
                                    label = { Text(text = stringResource(R.string.account_local_uuid)) },
                                    supportingText = {
                                        if (isUserUUIDInvalid) {
                                            Text(text = stringResource(R.string.account_local_uuid_invalid))
                                        }
                                    },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.large
                                )

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(all = 8.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.account_local_uuid_tip_1),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            text = stringResource(R.string.account_local_uuid_tip_2),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            text = stringResource(R.string.account_local_uuid_tip_3),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            text = stringResource(R.string.account_local_uuid_tip_4),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.size(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = onDismissRequest
                        ) {
                            MarqueeText(text = stringResource(R.string.generic_cancel))
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (userName.isNotEmpty()) {
                                    if (userUUID.isNotEmpty()) {
                                        runCatching {
                                            val uuid = accountUUID(userUUID)
                                            val uuidString = accountUUID(uuid)
                                            onConfirm(isUserNameInvalid, userName, uuidString)
                                        }
                                    } else {

                                        onConfirm(
                                            isUserNameInvalid,
                                            userName,
                                            pendingUUID.takeIf { it.isNotEmpty() })
                                    }
                                }
                            }
                        ) {
                            MarqueeText(text = stringResource(R.string.generic_confirm))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OtherServerLoginDialog(
    server: AuthServer,
    onRegisterClick: (url: String) -> Unit = {},
    onDismissRequest: () -> Unit = {},
    onConfirm: (email: String, password: String) -> Unit = { _, _ -> }
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val confirmAction = {
        if (email.isNotEmpty() && password.isNotEmpty()) {
            onConfirm(email, password)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(decorFitsSystemWindows = false)
    ) {
        ImePanContainer(
            modifier = Modifier
                .heightIn(max = rememberDialogMaxHeight())
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(all = 6.dp)
                    .heightIn(max = (maxHeight - 12.dp).coerceAtMost(rememberDialogMaxHeight()))
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.extraLarge,
                color = cardColor(false),
                contentColor = onCardColor(),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = server.serverName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.size(16.dp))

                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fadeEdge(state = scrollState)
                            .weight(1f, fill = false)
                            .verticalScrollWithBar(state = scrollState)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val passwordFocus = remember { FocusRequester() }
                        val focusManager = LocalFocusManager.current

                        SingleLineTextCheck(
                            text = email,
                            onSingleLined = { email = it }
                        )

                        OwnOutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = email,
                            onValueChange = {
                                email = it
                            },
                            isError = email.isEmpty(),
                            label = { Text(text = stringResource(R.string.account_label_email)) },
                            supportingText = {
                                if (email.isEmpty()) {
                                    Text(text = stringResource(R.string.account_supporting_email_invalid_empty))
                                }
                            },
                            keyboardOptions = KeyboardOptions.Default.copy(
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = {

                                    passwordFocus.requestFocus()
                                }
                            ),
                            singleLine = true,
                            shape = MaterialTheme.shapes.large
                        )

                        Spacer(modifier = Modifier.size(8.dp))

                        var showPassword by rememberSaveable { mutableStateOf(false) }

                        SingleLineTextCheck(
                            text = password,
                            onSingleLined = { password = it }
                        )

                        OwnOutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(passwordFocus),
                            value = password,
                            onValueChange = {
                                password = it
                            },
                            isError = password.isEmpty(),
                            label = { Text(text = stringResource(R.string.account_label_password)) },
                            visualTransformation = if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Transparent,
                            ),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        painter = painterResource(
                                            if (showPassword) {
                                                R.drawable.ic_visibility_outlined
                                            } else {
                                                R.drawable.ic_visibility_off_outlined
                                            }
                                        ),
                                        contentDescription = stringResource(R.string.account_label_password)
                                    )
                                }
                            },
                            supportingText = {
                                if (password.isEmpty()) {
                                    Text(text = stringResource(R.string.account_supporting_password_invalid_empty))
                                }
                            },
                            keyboardOptions = KeyboardOptions.Default.copy(
                                imeAction = ImeAction.Done,
                                keyboardType = KeyboardType.Password
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {

                                    focusManager.clearFocus(true)
                                    confirmAction()
                                }
                            ),
                            singleLine = true,
                            shape = MaterialTheme.shapes.large
                        )
                        if (!server.register.isNullOrEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                IconTextButton(
                                    onClick = {
                                        onRegisterClick(server.register!!)
                                    },
                                    painter = painterResource(R.drawable.ic_link),
                                    contentDescription = null,
                                    text = stringResource(R.string.account_other_login_register)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.size(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = onDismissRequest
                        ) {
                            MarqueeText(text = stringResource(R.string.generic_cancel))
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = confirmAction
                        ) {
                            MarqueeText(text = stringResource(R.string.generic_confirm))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MicrosoftReloginDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    SimpleAlertDialog(
        title = stringResource(R.string.account_relogin_title),
        text = {
            Text(text = stringResource(R.string.account_relogin_microsoft_message))
        },
        confirmText = stringResource(R.string.account_relogin),
        onConfirm = onConfirm,
        onCancel = onDismissRequest,
        onDismissRequest = onDismissRequest
    )
}

@Composable
fun OtherAccountReloginDialog(
    account: Account,
    logging: Boolean,
    error: Throwable?,
    onDismissRequest: () -> Unit,
    onConfirm: (password: String) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!logging) onDismissRequest() },
        properties = DialogProperties(decorFitsSystemWindows = false)
    ) {
        ImePanContainer(
            modifier = Modifier
                .heightIn(max = rememberDialogMaxHeight())
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(all = 6.dp)
                    .heightIn(max = (maxHeight - 12.dp).coerceAtMost(rememberDialogMaxHeight()))
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.extraLarge,
                color = cardColor(false),
                contentColor = onCardColor(),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = account.accountType ?: stringResource(R.string.account_relogin_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.size(16.dp))

                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.account_relogin_password_message, account.username),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.size(12.dp))

                    OwnOutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = password,
                        onValueChange = { password = it },
                        enabled = !logging,
                        label = { Text(text = stringResource(R.string.account_label_password)) },
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Transparent,
                        ),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    painter = painterResource(
                                        if (showPassword) {
                                            R.drawable.ic_visibility_outlined
                                        } else {
                                            R.drawable.ic_visibility_off_outlined
                                        }
                                    ),
                                    contentDescription = stringResource(R.string.account_label_password)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Password
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (password.isNotEmpty() && !logging) onConfirm(password)
                            }
                        ),
                        singleLine = true,
                        shape = MaterialTheme.shapes.large
                    )

                    error?.let { th ->
                        Spacer(modifier = Modifier.size(8.dp))
                        AndroidStringText(
                            text = accountErrorText(th),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                    }

                    Spacer(modifier = Modifier.size(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            enabled = !logging,
                            onClick = onDismissRequest
                        ) {
                            MarqueeText(text = stringResource(R.string.generic_cancel))
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !logging && password.isNotEmpty(),
                            onClick = { onConfirm(password) }
                        ) {
                            MarqueeText(text = stringResource(R.string.account_relogin))
                        }
                    }
                }
            }
        }
    }
}

