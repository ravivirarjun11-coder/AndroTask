package com.borgorninja.androtask

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Lightweight in-memory logger.
 * Captures recording, playback and gesture-dispatch events so bugs can be
 * exported as a plain-text file and shared directly.
 */
object AppLogger {

    enum class Level { D, I, W, E }

    data class Entry(
        val time:    Long,
        val level:   Level,
        val tag:     String,
        val message: String
    )

    // CopyOnWriteArrayList: safe to read from UI thread while service writes
    private val buffer = CopyOnWriteArrayList<Entry>()
    private val fmt    = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private const val MAX = 2_000   // keep last 2 k entries in memory

    @JvmStatic fun d(tag: String, msg: String) = append(Level.D, tag, msg)
    @JvmStatic fun i(tag: String, msg: String) = append(Level.I, tag, msg)
    @JvmStatic fun w(tag: String, msg: String) = append(Level.W, tag, msg)
    @JvmStatic fun e(tag: String, msg: String) = append(Level.E, tag, msg)

    private fun append(level: Level, tag: String, msg: String) {
        Log.println(when(level){
            Level.D -> Log.DEBUG; Level.I -> Log.INFO
            Level.W -> Log.WARN;  Level.E -> Log.ERROR
        }, "AndroTask/$tag", msg)
        buffer.add(Entry(System.currentTimeMillis(), level, tag, msg))
        if (buffer.size > MAX) buffer.removeAt(0)
    }

    fun entries(): List<Entry> = buffer.toList()

    fun clear() { buffer.clear() }

    fun export(): String {
        val sb = StringBuilder()
        sb.appendLine("=== AndroTask Debug Log ===")
        sb.appendLine("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine("Entries : ${buffer.size}")
        sb.appendLine("=".repeat(40))
        buffer.forEach { e ->
            sb.appendLine("[${fmt.format(Date(e.time))}] [${e.level}] [${e.tag}] ${e.message}")
        }
        return sb.toString()
    }
}
