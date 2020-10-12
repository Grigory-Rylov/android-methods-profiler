package com.github.grishberg.profiler.plugins.stages

import java.io.File

interface StagesFactory {
    fun loadFromFile(file: File): Stages
}
