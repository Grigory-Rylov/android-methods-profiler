package com.github.grishberg.profiler.analyzer

import com.github.grishberg.profiler.chart.BookmarksRectangle
import com.github.grishberg.profiler.chart.ProfilerPanel
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

class FlatMethodsReportGenerator(
    private val data: ProfilerPanel.ProfilerPanelData
) : ReportGenerator {

    override fun generate(file: File, onlyConstructor: Boolean, minimumDurationInMs: Int) {
        val fos = FileOutputStream(file)

        BufferedWriter(OutputStreamWriter(fos)).use { bw ->
            bw.write("name\tglobal time\tthread time\tglobal self time\tthread self time")
            bw.newLine()

            data.profileData.forEach {
                val threadDuration =
                    it.profileData.threadEndTimeInMillisecond - it.profileData.threadStartTimeInMillisecond
                val globalDuration =
                    it.profileData.globalEndTimeInMillisecond - it.profileData.globalStartTimeInMillisecond
                if (threadDuration > minimumDurationInMs && (!onlyConstructor || isConstructor(it.profileData.name))) {
                    bw.write(
                        String.format(
                            "%s\t%.3f\t%.3f\t%.3f\t%.3f",
                            it.profileData.name, globalDuration, threadDuration,
                            it.profileData.globalSelfTime, it.profileData.threadSelfTime
                        )
                    )
                    bw.newLine()
                }

                val markerRectangle = findMarkerForElement(it.profileData)
                if (markerRectangle != null) {
                    bw.write("<marker>: ${markerRectangle.name}")
                    bw.newLine()
                }
            }
        }
    }

    private fun findMarkerForElement(profileData: ProfileDataImpl): BookmarksRectangle? {
        for (marker in data.markersData) {
            if (marker.isForElement(profileData)) {
                return marker
            }
        }
        return null
    }

    private fun isConstructor(name: String): Boolean {
        return name.endsWith(".<init>")
    }
}
