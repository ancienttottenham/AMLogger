package com.ancienttottenham.amlogger

import android.util.Log
import com.ancienttottenham.amlogger.core.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Public façade that preserves the original API.
 */
object AMLogger {

    enum class Level(val priority: Int, val emoji: String, val colorCode: String) {
        VERBOSE(2, "💜", "\u001B[37m"),
        DEBUG(3, "💚", "\u001B[36m"),
        INFO(4, "💙", "\u001B[32m"),
        WARNING(5, "💛", "\u001B[33m"),
        ERROR(6, "❤️", "\u001B[31m")
    }

    enum class LogFormat { MINIMAL, COMPACT, DETAILED, CUSTOM }

    class Configuration {
        var minLevel: Level = Level.VERBOSE
        var logToConsole: Boolean = true
        var logToFile: Boolean = false
        var showEmojis: Boolean = true
        var showColors: Boolean = true
        var showThreadName: Boolean = false
        var showFileName: Boolean = true
        var showFunctionName: Boolean = true
        var showLineNumber: Boolean = true
        var dateFormat: String = "yyyy-MM-dd HH:mm:ss.SSS"
        var logFormat: LogFormat = LogFormat.DETAILED
        var fileMaxSize: Long = 10L * 1024 * 1024
        var maxBackupFiles: Int = 5
        var logDirectory: File? = null

        internal fun toCore(now: Date = Date()): CoreConfig {
            val sinks = buildList<SinkConfig> {
                if (logToConsole) add(LogcatSinkConfig)
                if (logToFile) {
                    val dir = logDirectory
                        ?: throw IllegalArgumentException("logDirectory must be provided when logToFile = true")
                    add(
                        FileSinkConfig(
                            directory = dir,
                            maxBytes = fileMaxSize,
                            maxBackups = maxBackupFiles,
                            filenamePrefix = "app_",
                            filenameDatePattern = "yyyyMMdd",
                            gzipBackups = true
                        )
                    )
                }
            }

            return CoreConfig(
                minPriority = minLevel.priority,
                dateFormat = SimpleDateFormat(dateFormat, Locale.getDefault()),
                format = when (logFormat) {
                    LogFormat.MINIMAL -> CoreConfig.Format.MINIMAL
                    LogFormat.COMPACT -> CoreConfig.Format.COMPACT
                    LogFormat.DETAILED -> CoreConfig.Format.DETAILED
                    LogFormat.CUSTOM -> CoreConfig.Format.DETAILED
                },
                showThread = showThreadName,
                showFile = showFileName,
                showFunction = showFunctionName,
                showLine = showLineNumber,
                showEmojis = showEmojis,
                showColors = showColors,
                sinks = sinks
            )
        }
    }

    private var configured = false
    private val filters = mutableListOf<(LogEntry) -> Boolean>()

    data class LogEntry(
        val level: Level,
        val message: String,
        val tag: String,
        val timestamp: Date,
        val thread: String,
        val fileName: String?,
        val functionName: String?,
        val lineNumber: Int?,
        val exception: Throwable? = null
    )

    fun configure(block: Configuration.() -> Unit) {
        val cfg = Configuration().apply(block)
        AMLoggerCore.init(cfg.toCore())
        configured = true
    }

    fun addFilter(filter: (LogEntry) -> Boolean) {
        filters.add(filter)
        AMLoggerCore.setPredicate { core ->
            val entry = core.toPublic()
            filters.all { it(entry) }
        }
    }

    fun removeAllFilters() {
        filters.clear()
        AMLoggerCore.setPredicate(null)
    }

    fun flush() = AMLoggerCore.flush()

    fun getLogFileSize(): Long = AMLoggerCore.currentFileLength()

    fun clearLogFile() = AMLoggerCore.truncateCurrentFile()

    fun shutdown() = AMLoggerCore.shutdown()

    fun verbose(message: String, tag: String = "", exception: Throwable? = null) =
        log(Level.VERBOSE, message, tag, exception)

    fun debug(message: String, tag: String = "", exception: Throwable? = null) =
        log(Level.DEBUG, message, tag, exception)

    fun info(message: String, tag: String = "", exception: Throwable? = null) =
        log(Level.INFO, message, tag, exception)

    fun warning(message: String, tag: String = "", exception: Throwable? = null) =
        log(Level.WARNING, message, tag, exception)

    fun error(message: String, tag: String = "", exception: Throwable? = null) =
        log(Level.ERROR, message, tag, exception)

    fun v(message: String, tag: String = "", exception: Throwable? = null) = verbose(message, tag, exception)
    fun d(message: String, tag: String = "", exception: Throwable? = null) = debug(message, tag, exception)
    fun i(message: String, tag: String = "", exception: Throwable? = null) = info(message, tag, exception)
    fun w(message: String, tag: String = "", exception: Throwable? = null) = warning(message, tag, exception)
    fun e(message: String, tag: String = "", exception: Throwable? = null) = error(message, tag, exception)

    inline fun <T> measure(tag: String = "", block: () -> T): T {
        val start = System.nanoTime()
        val result = block()
        val duration = (System.nanoTime() - start) / 1_000_000.0
        debug("⏱️ Execution time: %.2f ms".format(duration), tag)
        return result
    }

    fun logRequest(url: String, method: String, headers: Map<String, String>? = null, body: String? = null, tag: String = "") {
        val message = buildString {
            append("🌐 ").append(method).append(' ').append(url)
            headers?.let { append("\nHeaders: ").append(it) }
            body?.let { append("\nBody: ").append(it) }
        }
        debug(message,  tag)
    }

    fun logResponse(url: String, statusCode: Int, responseTime: Long, body: String? = null) {
        val emoji = when (statusCode) {
            in 200..299 -> "✅"
            in 300..399 -> "↩️"
            in 400..499 -> "⚠️"
            in 500..599 -> "❌"
            else -> "❓"
        }
        val message = buildString {
            append(emoji).append(' ').append(statusCode).append(' ').append(url)
            append(" (").append(responseTime).append("ms)")
            body?.let { append("\nResponse: ").append(it) }
        }
        val level = if (statusCode >= 400) Level.WARNING else Level.DEBUG
        log(level, message, "AMLoggerNetwork", null)
    }

    private fun ensureConfigured() {
        check(configured) { "AMLogger is not configured. Call AMLogger.configure { ... } first." }
    }

    private fun log(level: Level, message: String, tag: String, exception: Throwable?) {
        ensureConfigured()
        val (file, fn, line) = caller()
        AMLoggerCore.log(
            CoreLogEntry(
                ts = Date(),
                priority = level.priority,
                tag = tag.ifBlank { null },
                msg = message,
                throwable = exception,
                file = file,
                fn = fn,
                line = line,
                thread = Thread.currentThread().name,
                ansiColor = level.colorCode
            )
        )
    }

    private fun String.withEmoji(level: Level): String = "${level.emoji} $this"

    private fun caller(): Triple<String?, String?, Int?> {
        val st = Thread.currentThread().stackTrace
        for (i in 3 until st.size) {
            val e = st[i]
            if (!e.className.contains("AMLogger")) {
                return Triple(e.fileName, e.methodName, e.lineNumber)
            }
        }
        return Triple(null, null, null)
    }

    private fun CoreLogEntry.toPublic(): LogEntry =
        LogEntry(
            level = when (priority) {
                2 -> Level.VERBOSE
                3 -> Level.DEBUG
                4 -> Level.INFO
                5 -> Level.WARNING
                else -> Level.ERROR
            },
            message = msg,
            tag = tag ?: "",
            timestamp = ts,
            thread = thread ?: "",
            fileName = file,
            functionName = fn,
            lineNumber = line,
            exception = throwable
        )
}
