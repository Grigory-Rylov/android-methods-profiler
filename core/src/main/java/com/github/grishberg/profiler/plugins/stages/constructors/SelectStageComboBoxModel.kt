package com.github.grishberg.profiler.plugins.stages.constructors

import com.github.grishberg.profiler.plugins.stages.Stage
import javax.swing.DefaultComboBoxModel

class SelectStageComboBoxModel(data: List<Stage>): DefaultComboBoxModel<Stage>(data.toTypedArray())
