package com.github.grishberg.profiler.common

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.profiler.analyzer.TraceAnalyzer
import com.github.grishberg.profiler.analyzer.converter.DeObfuscatorConverter
import com.github.grishberg.profiler.chart.BOOKMARK_EXTENSION
import com.github.grishberg.profiler.chart.Bookmarks
import com.github.grishberg.profiler.common.settings.SettingsRepository
import com.github.grishberg.profiler.ui.Main
import proguard.retrace.DeObfuscator
import java.awt.Frame
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.jvm.Throws


data class TraceContainer(val traceFile: File, val result: AnalyzerResult, val bookmarks: Bookmarks)

class FileSystem(
    private val parentFrame: Frame,
    private val settings: SettingsRepository,
    private val log: AppLogger
) {
    val fileFilters = mutableListOf<FileNameExtensionFilter>().apply {
        add(FileNameExtensionFilter("Android Studio trace files", "trace"))
        add(FileNameExtensionFilter("compressed AS trace files", "zip"))
        add(FileNameExtensionFilter("AS trace with bookmarks", "twb"))
    }
    val mappingFilters = mutableListOf<FileNameExtensionFilter>().apply {
        add(FileNameExtensionFilter("text mapping file", "txt"))
    }

    private val tempDirectory = System.getProperty("java.io.tmpdir")
    private val analyzer = TraceAnalyzer(log)

    private var mappingFile: File? = null
    private var cachedDeobfuscator: DeObfuscator? = null

    fun openMappingFile(file: File) {
        mappingFile = file
    }

    /**
     * Should run in worker thread.
     */
    fun readFile(file: File): TraceContainer {
        if (mappingFile != null && cachedDeobfuscator == null) {
            val deObfuscator = DeObfuscator(mappingFile)
            analyzer.nameConverter = DeObfuscatorConverter(deObfuscator)
            cachedDeobfuscator = deObfuscator
        }

        if (file.extension == "trace") {
            return readTraceFile(file)
        }
        if (file.extension == "twb" || file.extension == "zip") {
            return readTraceWithBookmarks(file)
        }
        throw IllegalStateException("Wrong file format")
    }

    private fun readTraceWithBookmarks(fileZip: File): TraceContainer {
        val uuid = UUID.randomUUID()
        val destDir = File(tempDirectory, "android-methods-profiler/$uuid")
        destDir.mkdirs()

        val buffer = ByteArray(1024)
        val zis = ZipInputStream(FileInputStream(fileZip))
        var zipEntry = zis.nextEntry
        val extractedFiles = mutableListOf<File>()
        while (zipEntry != null) {
            val newFile: File = newFile(destDir, zipEntry)
            val fos = FileOutputStream(newFile)
            var len: Int
            while (zis.read(buffer).also { len = it } > 0) {
                fos.write(buffer, 0, len)
            }
            fos.close()
            extractedFiles.add(newFile)
            zipEntry = zis.nextEntry
        }
        zis.closeEntry()
        zis.close()

        val traceFiles = extractedFiles.filter { it.extension.toLowerCase() == "trace" }
        if (traceFiles.isEmpty()) {
            throw IllegalStateException("There is no any .trace files")
        }
        val traceFile = traceFiles.first()
        val bookmarksFiles = extractedFiles.filter { it.extension.toLowerCase() == BOOKMARK_EXTENSION }
        val bookmarks =
            if (bookmarksFiles.isNotEmpty()) {
                Bookmarks(bookmarksFiles.first(), settings, log)
            } else {
                Bookmarks(traceFile.name, settings, log)
            }
        val trace = analyzer.analyze(traceFile)
        return TraceContainer(traceFile, trace, bookmarks)
    }

    @Throws(IOException::class)
    fun newFile(destinationDir: File, zipEntry: ZipEntry): File {
        val destFile = File(destinationDir, zipEntry.name)
        val destDirPath = destinationDir.canonicalPath
        val destFilePath = destFile.canonicalPath
        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw IOException("Entry is outside of the target dir: " + zipEntry.name)
        }
        return destFile
    }

    private fun readTraceFile(traceFile: File): TraceContainer {
        val analyzerResult = analyzer.analyze(traceFile)

        val bookmarks = Bookmarks(traceFile.name, settings, log)
        return TraceContainer(traceFile, analyzerResult, bookmarks)
    }

    fun exportTraceWithBookmarks(container: TraceContainer) {
        val fileChooser = JFileChooser(settings.getStringValue(Main.SETTINGS_TRACES_FILE_DIALOG_DIRECTORY))
        fileChooser.dialogTitle = "Specify a file to save"
        val filter = FileNameExtensionFilter("Trace with bookmarks", "twb")
        fileChooser.fileFilter = filter

        val userSelection = fileChooser.showSaveDialog(parentFrame)

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            var fileToSave = fileChooser.selectedFile
            settings.setStringValue(Main.SETTINGS_REPORTS_FILE_DIALOG_DIRECTORY, fileToSave.parent)
            if (fileToSave.extension.toLowerCase() != "twb") {
                fileToSave = File(fileToSave.absolutePath + ".twb")
            }
            saveTraceWithBookmarks(fileToSave, container)
        }
    }

    private fun saveTraceWithBookmarks(outFile: File, container: TraceContainer) {
        container.bookmarks.save()
        val traceFile = container.traceFile
        val bookmarks = container.bookmarks

        val srcFiles = arrayOf(traceFile, bookmarks.file)
        val fos = FileOutputStream(outFile)
        val zipOut = ZipOutputStream(fos)
        for (fileToZip in srcFiles) {
            val fis = FileInputStream(fileToZip)
            val zipEntry = ZipEntry(fileToZip.name)
            zipOut.putNextEntry(zipEntry)
            val bytes = ByteArray(1024)
            var length: Int
            while (fis.read(bytes).also { length = it } >= 0) {
                zipOut.write(bytes, 0, length)
            }
            fis.close()
        }
        zipOut.close()
        fos.close()
    }
}
