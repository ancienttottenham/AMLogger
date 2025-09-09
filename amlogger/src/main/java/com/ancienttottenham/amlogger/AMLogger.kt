package com.ancienttottenham.amlogger

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

object AMLogger {

    enum class Level(val priority: Int, val emoji: String, val colorCode: String) {
        VERBOSE(2, "💜", "\u001B[37m"),  // White
        DEBUG(3, "💚", "\u001B[36m"),    // Cyan
        INFO(4, "💙", "\u001B[32m"),     // Green
        WARNING(5, "💛", "\u001B[33m"),  // Yellow
        ERROR(6, "❤️", "\u001B[31m")     // Red
    }

    private var minLevel: Level = Level.VERBOSE
    private var dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private var logToConsole = true
    private var logToFile = false
    private var showEmojis = true
    private var showColors = true
    private var showThreadName = false
    private var showFileName = true
    private var showFunctionName = true
    private var showLineNumber = true

    // File logging
    private var logFile: File? = null
    private var maxFileSize: Long = 10 * 1024 * 1024 // 10MB
    private var maxBackupFiles = 5

    // Async logging
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private var loggingScope: CoroutineScope? = null

    // Filters and formatting
    private val filters = mutableListOf<(LogEntry) -> Boolean>()
    private var logFormat = LogFormat.DETAILED

    data class LogEntry(
        val level: Level,
        val message: String,
        val tag: String,
        val timestamp: Date,
        val thread: String,
        val fileName: String,
        val functionName: String,
        val lineNumber: Int,
        val exception: Throwable? = null
    )

    enum class LogFormat {
        MINIMAL,    // Just message
        COMPACT,    // Time + Level + Message
        DETAILED,   // Full info with file/function
        CUSTOM      // User-defined format
    }

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
        var fileMaxSize: Long = 10 * 1024 * 1024 // 10MB
        var maxBackupFiles: Int = 5
        var logDirectory: File? = null

        fun build() = apply {
            AMLogger.minLevel = minLevel
            AMLogger.logToConsole = logToConsole
            AMLogger.logToFile = logToFile
            AMLogger.showEmojis = showEmojis
            AMLogger.showColors = showColors
            AMLogger.showThreadName = showThreadName
            AMLogger.showFileName = showFileName
            AMLogger.showFunctionName = showFunctionName
            AMLogger.showLineNumber = showLineNumber
            AMLogger.dateFormat = SimpleDateFormat(dateFormat, Locale.getDefault())
            AMLogger.logFormat = logFormat
            AMLogger.maxFileSize = fileMaxSize
            AMLogger.maxBackupFiles = maxBackupFiles

            if (logToFile && logDirectory != null) {
                setupFileLogging(logDirectory!!)
            }

            setupAsyncLogging()
        }
    }

    /**
     * Configure the logger with a fluent API
     */
    fun configure(block: Configuration.() -> Unit) {
        Configuration().apply(block).build()
    }

    private fun setupFileLogging(directory: File) {
        try {
            if (!directory.exists()) {
                directory.mkdirs()
            }
            logFile = File(directory, "app_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.log")
        } catch (e: Exception) {
            Log.e("AMLogger", "Failed to setup file logging", e)
        }
    }

    private fun setupAsyncLogging() {
        loggingScope?.cancel() // Cancel any existing scope
        loggingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        loggingScope?.launch {
            while (isActive) {
                try {
                    val entry = logQueue.poll()
                    if (entry != null) {
                        processLogEntry(entry)
                    } else {
                        delay(10) // Small delay when queue is empty
                    }
                } catch (e: Exception) {
                    Log.e("AMLogger", "Error in async logging", e)
                }
            }
        }
    }

    private fun processLogEntry(entry: LogEntry) {
        if (entry.level.priority < minLevel.priority) return

        // Apply filters
        if (filters.any { !it(entry) }) return

        // Format message
        val formattedMessage = formatLogEntry(entry)

        // Output to console (Logcat)
        if (logToConsole) {
            val tag = if (entry.tag.isNotEmpty()) entry.tag else "AMLogger"
            when (entry.level) {
                Level.VERBOSE -> Log.v(tag, formattedMessage, entry.exception)
                Level.DEBUG -> Log.d(tag, formattedMessage, entry.exception)
                Level.INFO -> Log.i(tag, formattedMessage, entry.exception)
                Level.WARNING -> Log.w(tag, formattedMessage, entry.exception)
                Level.ERROR -> Log.e(tag, formattedMessage, entry.exception)
            }
        }

        // Output to file
        if (logToFile && logFile != null) {
            writeToFile(entry, formattedMessage)
        }

        // Console output with colors (for testing)
        if (showColors) {
            val coloredMessage = "${entry.level.colorCode}$formattedMessage\u001B[0m"
            println(coloredMessage)
        }
    }

    private fun formatLogEntry(entry: LogEntry): String {
        return when (logFormat) {
            LogFormat.MINIMAL -> entry.message

            LogFormat.COMPACT -> {
                val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(entry.timestamp)
                val emoji = if (showEmojis) "${entry.level.emoji} " else ""
                "$emoji$time ${entry.level.name}: ${entry.message}"
            }

            LogFormat.DETAILED -> {
                val components = mutableListOf<String>()

                // Emoji
                if (showEmojis) {
                    components.add(entry.level.emoji)
                }

                // Timestamp
                components.add(dateFormat.format(entry.timestamp))

                // Level
                components.add("[${entry.level.name}]")

                // Thread
                if (showThreadName) {
                    components.add("[${entry.thread}]")
                }

                // Location info
                val locationParts = mutableListOf<String>()
                if (showFileName) locationParts.add(entry.fileName)
                if (showFunctionName) locationParts.add(entry.functionName)
                if (showLineNumber) locationParts.add(":${entry.lineNumber}")

                if (locationParts.isNotEmpty()) {
                    components.add("(${locationParts.joinToString("")})")
                }

                // Message
                components.add("→ ${entry.message}")

                components.joinToString(" ")
            }

            LogFormat.CUSTOM -> entry.message // User should handle custom formatting
        }
    }

    private fun writeToFile(entry: LogEntry, formattedMessage: String) {
        try {
            logFile?.let { file ->
                // Check file size and rotate if needed
                if (file.length() > maxFileSize) {
                    rotateLogFiles(file)
                }

                val plainMessage = formattedMessage.replace(Regex("\u001B\\[[0-9;]*m"), "") // Remove ANSI codes
                file.appendText("$plainMessage\n")
            }
        } catch (e: Exception) {
            Log.e("AMLogger", "Failed to write to log file", e)
        }
    }

    private fun rotateLogFiles(currentFile: File) {
        try {
            val baseName = currentFile.nameWithoutExtension
            val extension = currentFile.extension
            val directory = currentFile.parentFile

            // Shift existing backup files
            for (i in maxBackupFiles - 1 downTo 1) {
                val oldBackup = File(directory, "${baseName}.${i}.${extension}")
                val newBackup = File(directory, "${baseName}.${i + 1}.${extension}")
                if (oldBackup.exists()) {
                    oldBackup.renameTo(newBackup)
                }
            }

            // Move current file to .1 backup
            val firstBackup = File(directory, "${baseName}.1.${extension}")
            currentFile.renameTo(firstBackup)

            // Create new current file
            currentFile.createNewFile()

        } catch (e: Exception) {
            Log.e("AMLogger", "Failed to rotate log files", e)
        }
    }

    private fun getCallerInfo(): Triple<String, String, Int> {
        val stackTrace = Thread.currentThread().stackTrace

        // Find the first stack frame that's not in this logger class
        for (i in 2 until stackTrace.size) {
            val element = stackTrace[i]
            if (!element.className.contains("AMLogger")) {
                val fileName = element.fileName ?: "Unknown"
                val methodName = element.methodName ?: "unknown"
                val lineNumber = element.lineNumber
                return Triple(fileName, methodName, lineNumber)
            }
        }
        return Triple("Unknown", "unknown", 0)
    }

    private fun log(level: Level, message: String, tag: String = "", exception: Throwable? = null) {
        val (fileName, functionName, lineNumber) = getCallerInfo()
        val entry = LogEntry(
            level = level,
            message = message,
            tag = tag,
            timestamp = Date(),
            thread = Thread.currentThread().name,
            fileName = fileName,
            functionName = functionName,
            lineNumber = lineNumber,
            exception = exception
        )

        logQueue.offer(entry)
    }

    // Main logging methods
    fun verbose(message: String, tag: String = "", exception: Throwable? = null) {
        log(Level.VERBOSE, message, tag, exception)
    }

    fun debug(message: String, tag: String = "", exception: Throwable? = null) {
        log(Level.DEBUG, message, tag, exception)
    }

    fun info(message: String, tag: String = "", exception: Throwable? = null) {
        log(Level.INFO, message, tag, exception)
    }

    fun warning(message: String, tag: String = "", exception: Throwable? = null) {
        log(Level.WARNING, message, tag, exception)
    }

    fun error(message: String, tag: String = "", exception: Throwable? = null) {
        log(Level.ERROR, message, tag, exception)
    }

    // Convenience methods
    fun v(message: String, tag: String = "", exception: Throwable? = null) = verbose(message, tag, exception)
    fun d(message: String, tag: String = "", exception: Throwable? = null) = debug(message, tag, exception)
    fun i(message: String, tag: String = "", exception: Throwable? = null) = info(message, tag, exception)
    fun w(message: String, tag: String = "", exception: Throwable? = null) = warning(message, tag, exception)
    fun e(message: String, tag: String = "", exception: Throwable? = null) = error(message, tag, exception)

    // Advanced features
    fun addFilter(filter: (LogEntry) -> Boolean) {
        filters.add(filter)
    }

    fun removeAllFilters() {
        filters.clear()
    }

    fun flush() {
        // Process remaining queue items
        while (logQueue.isNotEmpty()) {
            logQueue.poll()?.let { processLogEntry(it) }
        }
    }

    fun getLogFileSize(): Long = logFile?.length() ?: 0

    fun clearLogFile() {
        logFile?.writeText("")
    }

    fun shutdown() {
        flush()
        loggingScope?.cancel()
        loggingScope = null
    }

    // Performance measurement
    inline fun <T> measure(tag: String = "", block: () -> T): T {
        val start = System.nanoTime()
        val result = block()
        val duration = (System.nanoTime() - start) / 1_000_000.0 // Convert to milliseconds
        debug("⏱️ Execution time: %.2f ms".format(duration), tag)
        return result
    }

    // Network/API logging helper
    fun logRequest(url: String, method: String, headers: Map<String, String>? = null, body: String? = null) {
        val message = buildString {
            append("🌐 $method $url")
            headers?.let {
                append("\nHeaders: $it")
            }
            body?.let {
                append("\nBody: $it")
            }
        }
        debug(message, "Network")
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
            append("$emoji $statusCode $url (${responseTime}ms)")
            body?.let { append("\nResponse: $it") }
        }

        val level = if (statusCode >= 400) Level.WARNING else Level.DEBUG
        log(level, message, "Network")
    }
}