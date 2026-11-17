package com.cmc.launcher.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

sealed interface AndroidStringText {

    data class Text(val value: String) : AndroidStringText

    data class Annotated(val value: AnnotatedString) : AndroidStringText

    data class StringRes(
        @field:androidx.annotation.StringRes
        val key: Int,
        val args: Array<out Any>?
    ) : AndroidStringText {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as StringRes

            if (key != other.key) return false
            if (!args.contentEquals(other.args)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = key
            result = 31 * result + (args?.contentHashCode() ?: 0)
            return result
        }
    }

    data class Appended(
        val texts: List<AndroidStringText>
    ) : AndroidStringText
}

fun androidText(string: String) = AndroidStringText.Text(string)

fun androidText(annotated: AnnotatedString) = AndroidStringText.Annotated(annotated)

fun androidText(@StringRes key: Int) = AndroidStringText.StringRes(key, null)

fun androidText(
    @StringRes
    key: Int,
    vararg args: Any
) = AndroidStringText.StringRes(key, args)

fun androidText(vararg texts: AndroidStringText) = AndroidStringText.Appended(texts.toList())

inline fun buildAppendedText(
    block: AndroidStringTextBuilder.() -> Unit
): AndroidStringText = AndroidStringTextBuilder().apply(block).build()

@DslMarker
private annotation class AndroidStringTextDsl
@AndroidStringTextDsl
class AndroidStringTextBuilder {
    private val texts = mutableListOf<AndroidStringText>()

    fun append(text: String) {
        texts.add(AndroidStringText.Text(text))
    }

    fun append(text: AnnotatedString) {
        texts.add(AndroidStringText.Annotated(text))
    }

    fun append(@StringRes resId: Int) {
        texts.add(AndroidStringText.StringRes(resId, null))
    }

    fun append(@StringRes resId: Int, vararg args: Any) {
        texts.add(AndroidStringText.StringRes(resId, args))
    }

    fun append(other: AndroidStringText) {
        texts.add(other)
    }

    fun build(): AndroidStringText = AndroidStringText.Appended(texts.toList())
}

@Composable
fun AndroidStringText(
    text: AndroidStringText,
    modifier: Modifier = Modifier,
    autoSize: TextAutoSize? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text = resolveAndroidString(text),
        modifier = modifier,
        autoSize = autoSize,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        style = style,
    )
}

@Composable
fun resolveAndroidString(text: AndroidStringText): AnnotatedString {
    return when (text) {
        is AndroidStringText.Text -> AnnotatedString(text.value)
        is AndroidStringText.Annotated -> text.value
        is AndroidStringText.StringRes -> {
            val args = text.args
            AnnotatedString(
                if (args == null) {
                    stringResource(text.key)
                } else {
                    val resolvedArgs = args.map { arg ->
                        if (arg is AndroidStringText) {
                            resolveAndroidString(arg).text
                        } else {
                            arg
                        }
                    }.toTypedArray()
                    stringResource(text.key, *resolvedArgs)
                }
            )
        }
        is AndroidStringText.Appended -> {
            buildAnnotatedString {
                text.texts.forEach {
                    append(resolveAndroidString(it))
                }
            }
        }
    }
}

fun AndroidStringText.toAndroidString(context: Context): String = when (this) {
    is AndroidStringText.Text -> value
    is AndroidStringText.Annotated -> value.toString()
    is AndroidStringText.StringRes -> if (args == null) {
        context.getString(key)
    } else {
        val resolvedArgs = args.map { arg ->
            if (arg is AndroidStringText) {
                arg.toAndroidString(context)
            } else {
                arg
            }
        }.toTypedArray()
        context.getString(key, *resolvedArgs)
    }
    is AndroidStringText.Appended -> texts.joinToString("") { it.toAndroidString(context) }
}
