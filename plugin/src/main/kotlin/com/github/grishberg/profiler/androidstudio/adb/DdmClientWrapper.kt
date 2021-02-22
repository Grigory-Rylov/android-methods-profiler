package com.github.grishberg.profiler.androidstudio.adb

import com.android.ddmlib.Client
import com.github.grishberg.android.adb.ClientWrapper
import com.github.grishberg.profiler.common.AppLogger
import java.util.concurrent.TimeUnit

class DdmClientWrapper(
    private val client: Client,
    logger: AppLogger
) : ClientWrapper {
    override fun dumpHprof() = Unit

    override fun executeGarbageCollector() = client.executeGarbageCollector()

    override fun requestMethodProfilingStatus() = Unit

    override fun startMethodTracer() = client.startMethodTracer()

    override fun startOpenGlTracing(): Boolean = false

    override fun startSamplingProfiler(samplingInterval: Int, timeUnit: TimeUnit) =
        client.startSamplingProfiler(samplingInterval, timeUnit)

    override fun stopMethodTracer() = client.stopMethodTracer()

    override fun stopOpenGlTracing() = false

    override fun stopSamplingProfiler() = client.stopSamplingProfiler()
}
