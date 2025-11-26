package com.ulpro.animalrecognizer
import android.content.Context
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val crashReport = generateCrashReport(throwable)
            saveCrashReportToFile(crashReport)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun generateCrashReport(throwable: Throwable): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = dateFormat.format(Date())
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return "Date: $date\n\n${writer}"
    }

    private fun saveCrashReportToFile(crashReport: String) {
        val crashDir = File(context.getExternalFilesDir(null), "crash_reports")
        if (!crashDir.exists()) {
            crashDir.mkdirs()
        }
        val crashFile = File(crashDir, "crash_report_${System.currentTimeMillis()}.txt")
        FileWriter(crashFile).use {
            it.write(crashReport)
        }
    }
}