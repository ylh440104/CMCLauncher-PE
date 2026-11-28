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

sealed interface ChangeSkin {
    data object None : ChangeSkin

    data class ChangeSkinData(
        val cacheFile: File,
        val data: SkinModel
    ) : ChangeSkin

    data object ResetSkin : ChangeSkin

    sealed interface SkinModel {

        data object Rejected : SkinModel
        data class Model(val model: SkinModelType): SkinModel

        fun getModelType(): SkinModelType = when (this) {
            is Rejected -> SkinModelType.STEVE
            is Model -> model
        }

        fun getModelTypeNullable(): SkinModelType? = when (this) {
            is Rejected -> null
            is Model -> model
        }
    }
}

sealed interface ChangeCape {
    data object None : ChangeCape
    data class ChangeCapeData(
        val cape: PlayerProfile.Cape
    ) : ChangeCape
}

@Composable
fun ChangeSkinDialog(
    account: Account,
    availableCapes: List<PlayerProfile.Cape>? = null,
    skinState: ChangeSkin,
    onSkinStateChange: (ChangeSkin) -> Unit,
    capeState: ChangeCape,
    onCapeStateChange: (ChangeCape) -> Unit,
    isImportingSkin: Boolean,
    onSkinPicked: (Uri) -> Unit,
    onDismissRequest: () -> Unit,
    onResetSkin: () -> Unit,
    onApplySkin: (File, SkinModelType) -> Unit,
    onApplyCape: (PlayerProfile.Cape) -> Unit,
    onFetchCapes: () -> Unit
) {
    var showCapeSelector by remember { mutableStateOf(false) }

    var isFetchingCapes by remember { mutableStateOf(false) }

    var currentCapeToLoad by remember { mutableStateOf(EmptyCape) }
    var currentUsingCape by remember { mutableStateOf(EmptyCape) }

    LaunchedEffect(availableCapes) {
        if (account.isMicrosoftAccount()) {
            if (availableCapes != null) {
                isFetchingCapes = false
                val currentUsingCape0 = availableCapes.findUsing() ?: EmptyCape
                currentUsingCape = currentUsingCape0
                currentCapeToLoad = currentUsingCape0
            } else {
                isFetchingCapes = true
                onFetchCapes()
            }
        }
    }

    val previewCape by produceState<ByteArray?>(null, currentCapeToLoad, availableCapes) {
        if (!account.isMicrosoftAccount()) return@produceState

        value = withContext(Dispatchers.IO) {
            if (currentCapeToLoad != EmptyCape) {

                runCatching {
                    currentCapeToLoad
                        .getFile(PathManager.DIR_ACCOUNT_CAPE)
                        .inputStream()
                        .use { it.readBytes() }
                }.getOrNull()
            } else if (availableCapes == null && account.getCapeFile().exists()) {

                runCatching {
                    account.getCapeFile()
                        .inputStream()
                        .use { it.readBytes() }
                }.getOrNull()
            } else {
                null
            }
        }
    }

    val skinPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let(onSkinPicked)
        }

    val previewSkin by produceState<ByteArray?>(null, skinState) {
        value = withContext(Dispatchers.IO) {
            when (skinState) {
                ChangeSkin.None -> account.getSkinFile()
                    .takeIf { it.exists() }
                    ?.inputStream()
                    ?.use { it.readBytes() }
                is ChangeSkin.ChangeSkinData -> runCatching {
                    skinState.cacheFile
                        .inputStream()
                        .use { it.readBytes() }
                }.getOrNull()
                ChangeSkin.ResetSkin -> null
            }
        }
    }
    val previewArm = when (skinState) {
        ChangeSkin.None -> account.skinModelType.toArmModel()
        is ChangeSkin.ChangeSkinData -> skinState.data.getModelType().toArmModel()
        ChangeSkin.ResetSkin -> ArmModel.Classic
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(all = 16.dp)
                .heightIn(max = rememberDialogMaxHeight())
                .fillMaxHeight()
                .fillMaxWidth(0.94f),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(all = 6.dp)
                    .heightIn(max = (maxHeight - 12.dp).coerceAtMost(rememberDialogMaxHeight())),
                shape = MaterialTheme.shapes.extraLarge,
                color = cardColor(false),
                contentColor = onCardColor(),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(itemColor(false)),
                            contentAlignment = Alignment.Center
                        ) {
                            PetModelView(
                                modifier = Modifier.fillMaxSize(),
                                skin = previewSkin,
                                cape = previewCape,
                                arm = previewArm,
                                animation = PetAnimation.Walking,
                                animationSpeed = 0.8f,
                                gazeEnabled = false
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {

                            when (skinState) {
                                ChangeSkin.None, ChangeSkin.ResetSkin -> {
                                    InfoLayoutTextItem(
                                        modifier = Modifier.fillMaxWidth(),
                                        title = stringResource(R.string.account_change_skin),
                                        icon = {
                                            if (isImportingSkin) {
                                                StaticBusyIndicator(
                                                    modifier = Modifier.size(22.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    modifier = Modifier.size(22.dp),
                                                    painter = painterResource(R.drawable.ic_upload),
                                                    contentDescription = null
                                                )
                                            }
                                        },
                                        onClick = {
                                            skinPicker.launch(arrayOf("image/png"))
                                        },
                                        enabled = !isImportingSkin
                                    )
                                }

                                is ChangeSkin.ChangeSkinData -> {
                                    val model = skinState.data.getModelTypeNullable()

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.account_change_skin_arm_style),
                                            style = MaterialTheme.typography.bodyMedium
                                        )

                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {

                                            RadioCard(
                                                selected = model == SkinModelType.STEVE,
                                                enabled = skinState.data !is SkinModel.Rejected,
                                                text = stringResource(R.string.account_change_skin_arm_wide),
                                                onClick = {
                                                    onSkinStateChange(
                                                        skinState.copy(
                                                            data = SkinModel.Model(SkinModelType.STEVE)
                                                        )
                                                    )
                                                }
                                            )

                                            RadioCard(
                                                selected = model == SkinModelType.ALEX,
                                                enabled = skinState.data !is SkinModel.Rejected,
                                                text = stringResource(R.string.account_change_skin_arm_slim),
                                                onClick = {
                                                    onSkinStateChange(
                                                        skinState.copy(
                                                            data = SkinModel.Model(SkinModelType.ALEX)
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (account.isMicrosoftAccount()) {
                                InfoLayoutTextItem(
                                    modifier = Modifier.fillMaxWidth(),
                                    title = if (isFetchingCapes) {
                                        stringResource(R.string.account_change_cape_fetch_all)
                                    } else {
                                        stringResource(R.string.account_change_cape)
                                    },
                                    icon = {
                                        if (isFetchingCapes) {
                                            StaticBusyIndicator(
                                                modifier = Modifier.size(22.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                modifier = Modifier.size(22.dp),
                                                painter = painterResource(R.drawable.ic_styler),
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    onClick = {
                                        showCapeSelector = true
                                    },
                                    enabled = !isFetchingCapes
                                )
                            }

                            if (account.isLocalAccount() && account.hasSkinFile && skinState != ChangeSkin.ResetSkin) {
                                InfoLayoutTextItem(
                                    modifier = Modifier.fillMaxWidth(),
                                    title = stringResource(R.string.generic_reset),
                                    icon = {
                                        Icon(
                                            modifier = Modifier.size(22.dp),
                                            painter = painterResource(R.drawable.ic_restart_alt),
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        onSkinStateChange(ChangeSkin.ResetSkin)
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = onDismissRequest
                        ) {
                            Text(text = stringResource(R.string.generic_cancel))
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = skinState != ChangeSkin.None || capeState != ChangeCape.None,
                            onClick = {
                                when (skinState) {
                                    is ChangeSkin.ChangeSkinData -> {
                                        onApplySkin(skinState.cacheFile, skinState.data.getModelType())
                                    }

                                    is ChangeSkin.ResetSkin -> {
                                        onResetSkin()
                                    }

                                    ChangeSkin.None -> {}
                                }

                                if (capeState is ChangeCape.ChangeCapeData) {
                                    onApplyCape(capeState.cape)
                                }

                                onDismissRequest()
                            }
                        ) {
                            Text(text = stringResource(R.string.generic_confirm))
                        }
                    }
                }
            }
        }
    }

    if (showCapeSelector) {

        val cape = if (capeState is ChangeCape.ChangeCapeData) {
            capeState.cape
        } else {
            currentUsingCape
        }

        SelectCapeDialog(
            capes = buildList {
                add(EmptyCape)
                addAll(availableCapes ?: emptyList())
            },
            selectedCape = cape,
            onSelected = { cape ->

                val state = if (cape != currentUsingCape) {
                    ChangeCape.ChangeCapeData(cape)
                } else {
                    ChangeCape.None
                }
                onCapeStateChange(state)
                currentCapeToLoad = cape
                showCapeSelector = false
            },
            onDismiss = {
                showCapeSelector = false
            }
        )
    }
}

@Composable
fun SelectCapeDialog(
    capes: List<PlayerProfile.Cape>,
    selectedCape: PlayerProfile.Cape?,
    onSelected: (PlayerProfile.Cape) -> Unit,
    onDismiss: () -> Unit,
    capeSize: Dp = 32.dp,
) {
    val density = LocalDensity.current

    val capeLocals = remember(capes) {
        buildMap {
            capes.forEach { cape ->
                cape.capeLocalRes()?.let { local ->
                    put(cape, androidText(local))
                }
            }
        }
    }

    SimpleListDialog(
        title = stringResource(R.string.account_change_cape_select_cape),
        items = capes,
        onItemSelected = { cape ->
            onSelected(cape)
        },
        current = selectedCape,
        itemLayout = { cape, isCurrent, onClick ->
            val name = capeLocals[cape] ?: androidText(cape.alias)
            CapeListItem(
                modifier = Modifier.fillMaxWidth(),
                selected = isCurrent,
                cape = cape,
                name = name,
                size = capeSize,
                density = density,
                onClick = onClick,
            )
        },
        onDismissRequest = { selected ->
            if (!selected) {
                onDismiss()
            }
        }
    )
}

@Composable
fun CapeListItem(
    modifier: Modifier = Modifier,
    selected: Boolean,
    cape: PlayerProfile.Cape,
    name: AndroidStringText,
    size: Dp,
    density: Density,
    onClick: () -> Unit,
) {
    val avatar by produceState<ImageBitmap?>(null, cape, density) {
        value = withContext(Dispatchers.IO) {
            if (cape != EmptyCape) {
                getCapeAvatar(
                    cape = cape,
                    size = size,
                    density = density
                )?.asImageBitmap()
            } else null
        }
    }

    Row(
        modifier = modifier
            .clip(shape = MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )

        val avatarBitmap = avatar
        if (avatarBitmap != null) {
            val displayWidth = with(density) { avatarBitmap.width.toDp() }
            val displayHeight = with(density) { avatarBitmap.height.toDp() }
            Image(
                modifier = Modifier
                    .width(displayWidth)
                    .height(displayHeight),
                bitmap = avatarBitmap,
                contentDescription = null
            )

            Spacer(Modifier.width(12.dp))
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AndroidStringText(
                text = name,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

private val avatarPaint = Paint().apply { isFilterBitmap = false }

private fun getCapeAvatar(
    cape: PlayerProfile.Cape,
    size: Dp,
    density: Density
): Bitmap? {
    val capeFile = cape.getFile(PathManager.DIR_ACCOUNT_CAPE)
    if (capeFile.exists()) {
        runCatching {
            Files.newInputStream(capeFile.toPath()).use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                    ?: throw IOException("Failed to read the cape picture and try to parse it to a bitmap")
                return getCapeAvatar(bitmap, size, density)
            }
        }.onFailure { e ->
            Logger.error(TAG, "Failed to load cape avatar from locally!", e)
        }
    }
    return null
}

private fun getCapeAvatar(
    cape: Bitmap,
    size: Dp,
    density: Density
): Bitmap {
    val pixelSize = with(density) { size.roundToPx() }
    val scaleFactor = cape.width / 64.0f
    val start = scaleFactor.roundToInt()
    val capeWidth = (10 * scaleFactor).roundToInt()
    val capeHeight = (16 * scaleFactor).roundToInt()
    val targetWidth = (pixelSize.toFloat() * capeWidth / capeHeight).roundToInt()
    val avatar = createBitmap(targetWidth, pixelSize)
    val canvas = Canvas(avatar)

    canvas.drawBitmap(
        cape,
        Rect(start, start, start + capeWidth, start + capeHeight),
        RectF(0f, 0f, targetWidth.toFloat(), pixelSize.toFloat()),
        avatarPaint
    )
    return avatar
}

internal fun getSkinAvatarFromAccount(
    context: Context,
    account: Account,
    size: Dp,
    density: Density
): Bitmap {
    val skin = account.getSkinFile()
    if (skin.exists()) {
        runCatching {
            Files.newInputStream(skin.toPath()).use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                    ?: throw IOException("Failed to read the skin picture and try to parse it to a bitmap")
                return getSkinAvatar(bitmap, size, density)
            }
        }.onFailure { e ->
            Logger.error(TAG, "Failed to load skin avatar from locally!", e)
        }
    }
    return getDefaultAvatar(context, size, density)
}

@Throws(Exception::class)
private fun getDefaultAvatar(
    context: Context,
    size: Dp,
    density: Density
): Bitmap {
    return getSkinAvatar(
        skin = BitmapFactory.decodeStream(
            context.assets.open("skin/steve.png")
        ),
        size = size,
        density = density
    )
}

private fun getSkinAvatar(
    skin: Bitmap,
    size: Dp,
    density: Density
): Bitmap {
    val pixelSize = with(density) { size.roundToPx() }
    val faceOffset = (pixelSize / 18.0).roundToInt().toFloat()
    val scaleFactor = skin.width / 64.0f
    val faceSize = (8 * scaleFactor).roundToInt()
    val faceEndY = faceSize * 2
    val hatSrcX = (40 * scaleFactor).roundToInt()
    val avatar = createBitmap(pixelSize, pixelSize)
    val canvas = Canvas(avatar)

    val innerEnd = pixelSize - faceOffset
    canvas.drawBitmap(
        skin,
        Rect(faceSize, faceSize, faceEndY, faceEndY),
        RectF(faceOffset, faceOffset, innerEnd, innerEnd),
        avatarPaint
    )

    canvas.drawBitmap(
        skin,
        Rect(hatSrcX, faceSize, hatSrcX + faceSize, faceEndY),
        RectF(0f, 0f, pixelSize.toFloat(), pixelSize.toFloat()),
        avatarPaint
    )
    return avatar
}
