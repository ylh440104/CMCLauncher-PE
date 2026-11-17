package com.cmc.launcher.bridge

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.content.Context
import com.cmc.launcher.context.GlobalContext
import com.cmc.launcher.utils.logging.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object FliteTts {
    private const val TAG = "FliteTTS"

    private const val FAST_CHECK_SECONDS = 2L

    private const val STATE_UNINIT = 0
    private const val STATE_INITIALIZING = 1
    private const val STATE_READY = 2
    private const val STATE_FAILED = 3

    private val ttsThread = HandlerThread(TAG)
        .apply { start() }

    private val pending = ArrayDeque<PendingSpeech>()

    @Volatile
    private var state = STATE_UNINIT

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var readySignal = CountDownLatch(1)

    @Volatile
    private var candidateEngines: List<String> = emptyList()
    private val triedEngines: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val utteranceCounter = AtomicLong()

    private data class PendingSpeech(val text: String, val gain: Float)

    @JvmStatic
    fun init(): Boolean {
        synchronized(this) {
            when (state) {
                STATE_READY -> return true
                STATE_FAILED -> return false
                STATE_INITIALIZING -> return true
                else -> {
                    readySignal = CountDownLatch(1)
                    state = STATE_INITIALIZING
                    val signal = readySignal
                    Handler(ttsThread.looper).post { constructTts(signal, null) }
                }
            }
        }

        val arrived = try {
            readySignal.await(FAST_CHECK_SECONDS, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            false
        }
        return if (arrived) state == STATE_READY else true
    }

    @JvmStatic
    fun speak(message: ByteArray, gain: Float): Float {
        if (state == STATE_FAILED) return -1f
        val text = runCatching { String(message, Charsets.UTF_8) }.getOrNull() ?: return -1f
        if (text.isBlank()) return -1f
        synchronized(this) {
            val instance = tts
            if (state == STATE_READY && instance != null) {
                enqueue(instance, text, gain)
            } else {
                pending.addLast(PendingSpeech(text, gain))
            }
        }
        return 0f
    }

    @JvmStatic
    fun cancel() {
        synchronized(this) { pending.clear() }
        tts?.runCatching { stop() }
    }

    @JvmStatic
    @Synchronized
    fun shutdown() {
        state = STATE_UNINIT
        releaseInstance()
        pending.clear()
        readySignal.countDown()
        triedEngines.clear()
        candidateEngines = emptyList()
    }

    private fun enqueue(instance: TextToSpeech, text: String, gain: Float) {
        val utteranceId = "zl-flite-${utteranceCounter.incrementAndGet()}"
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, gain) }
        if (instance.speak(text, TextToSpeech.QUEUE_ADD, params, utteranceId) != TextToSpeech.SUCCESS) {
            Logger.error(TAG, "speak enqueue failed: $utteranceId")
        }
    }

    private fun drainPending() {
        val instance = tts ?: return
        while (pending.isNotEmpty()) {
            val speech = pending.removeFirst()
            enqueue(instance, speech.text, speech.gain)
        }
    }

    private fun constructTts(signal: CountDownLatch, engine: String?) {
        val ref = AtomicReference<TextToSpeech>()
        try {
            val listener = TextToSpeech.OnInitListener { code ->
                if (state == STATE_INITIALIZING) {
                    if (code == TextToSpeech.SUCCESS) {
                        synchronized(this) {
                            if (state == STATE_INITIALIZING) {
                                ref.get()?.let { tts = it }
                                state = STATE_READY
                                drainPending()
                            }
                        }
                        Logger.info(TAG, "TextToSpeech ready (engine=${engine ?: "default"})")
                        signal.countDown()
                    } else {
                        Logger.error(TAG, "engine init failed: engine=${engine ?: "default"} status=$code")

                        tryNextEngine(signal, ref.get(), engine)
                    }
                }

            }
            val context = GlobalContext.applicationContext
            val instance = if (engine == null) {
                TextToSpeech(context, listener)
            } else {
                TextToSpeech(context, listener, engine)
            }
            ref.set(instance)
            instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {}

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Logger.error(TAG, "utterance failed: $utteranceId")
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {}
            })
            synchronized(this) {
                if (state != STATE_INITIALIZING) {

                    instance.shutdown()
                    return
                }
                tts = instance
                candidateEngines = runCatching { instance.engines.map { it.name } }.getOrDefault(emptyList())
            }
        } catch (e: Throwable) {
            Logger.error(TAG, "create TextToSpeech failed", e)
            tryNextEngine(signal, null, engine)
        }
    }

    private fun tryNextEngine(signal: CountDownLatch, failed: TextToSpeech?, failedEngine: String?) {
        triedEngines.add(failedEngine ?: "default")
        val next = candidateEngines.firstOrNull { it !in triedEngines }
        if (next == null) {
            state = STATE_FAILED
            pending.clear()
            Logger.error(TAG, "no usable TTS engine (tried=$triedEngines), narrator disabled")
            signal.countDown()
            return
        }
        Logger.info(TAG, "falling back to TTS engine: $next")
        Handler(ttsThread.looper).post {
            failed?.runCatching { shutdown() }
            constructTts(signal, next)
        }
    }

    private fun releaseInstance() {
        val instance = tts ?: return
        tts = null
        Handler(ttsThread.looper).post {
            try {
                instance.shutdown()
            } catch (e: Throwable) {
                Logger.error(TAG, "shutdown TextToSpeech failed", e)
            }
        }
    }
}
