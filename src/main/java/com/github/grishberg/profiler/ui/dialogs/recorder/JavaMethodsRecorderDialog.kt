package com.github.grishberg.profiler.ui.dialogs.recorder

import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.common.JNumberField
import com.github.grishberg.profiler.common.settings.SettingsRepository
import com.github.grishberg.profiler.ui.LabeledGridBuilder
import com.github.grishberg.profiler.ui.dialogs.CloseByEscapeDialog
import com.github.grishberg.tracerecorder.RecordMode
import kotlinx.coroutines.CoroutineScope
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import javax.swing.*
import javax.swing.border.BevelBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.EtchedBorder

private const val TAG = "JavaMethodsRecorderDialog"
private const val TEXT_PADDING = 8
private const val TITLE = "Record new trace"
private const val FIELD_LENGTH = 30

class JavaMethodsRecorderDialog(
    private val coroutineScope: CoroutineScope,
    private val dispatchers: CoroutinesDispatchers,
    owner: Frame,
    private val settings: SettingsRepository,
    private val logger: AppLogger
) : CloseByEscapeDialog(
    owner,
    TITLE, true
), JavaMethodsRecorderDialogView {
    private val packageNameField: JTextField
    private val activityNameField: JTextField
    private val fileNamePrefixField: JTextField
    private val remoteDeviceAddressField: JTextField = JTextField(20)
    private val stagesTracePrefixField: JTextField = JTextField(10)
    private val showRemotePanelCheckbox = JCheckBox("Connect to remote device")
    private val showDevicePanelCheckbox = JCheckBox("Connect to device by serial number")
    private val moreOptionsPanelCheckbox = JCheckBox("Additional options")
    private val systraceStagesCheckbox = JCheckBox("Systrace Stages")

    // TODO(geaden): Most certainly this field should be JComboBox instead of JTextField
    //   however it requires more effort and changes from trace-recorder are needed
    private val serialNumberField: JTextField = JTextField(20)
    private val statusLabel: JLabel
    private val startButton: JButton
    private val stopButton: JButton
    private val defaultLabelColor: Color
    private val samplingField = JNumberField(10)
    private val profilerBufferSizeField = JNumberField(10)
    private val logic: JavaMethodsDialogLogic
    private val recordModeComBox =
        JComboBox(arrayOf(RecordMode.METHOD_SAMPLE, RecordMode.METHOD_TRACES))

    override var packageName: String
        get() = packageNameField.text.trim()
        set(value) {
            packageNameField.text = value
        }

    override var activityName: String
        get() = activityNameField.text.trim()
        set(value) {
            activityNameField.text = value
        }

    override var fileNamePrefix: String
        get() = fileNamePrefixField.text.trim()
        set(value) {
            fileNamePrefixField.text = value
        }

    override var sampling: Int
        get() = samplingField.value
        set(value) {
            samplingField.value = value
        }

    override var recordMode: RecordMode
        get() = recordModeComBox.selectedItem as RecordMode
        set(value) {
            recordModeComBox.selectedItem = value
        }

    override var profilerBufferSizeInMb: Int
        get() = profilerBufferSizeField.value
        set(value) {
            profilerBufferSizeField.value = value
        }

    override var remoteDeviceAddress: String
        get() = remoteDeviceAddressField.text.trim()
        set(value) {
            remoteDeviceAddressField.text = value
            showRemotePanelCheckbox.isSelected = value.isNotEmpty()
        }

    override var serialNumber: String?
        get() = serialNumberField.text.trim().takeIf { showDevicePanelCheckbox.isSelected }
        set(value) {
            serialNumberField.text = value
            showDevicePanelCheckbox.isSelected = !value.isNullOrEmpty()
        }

    override var isSystraceStageEnabled: Boolean
        get() = systraceStagesCheckbox.isSelected
        set(value) {
            systraceStagesCheckbox.isSelected = value
            if (value) {
                moreOptionsPanelCheckbox.isSelected = true
            }
        }
    override var systraceStagePrefix: String?
        get() {
            val text = stagesTracePrefixField.text.trim()
            return if (text.isEmpty()) {
                null
            } else {
                text
            }
        }
        set(value) {
            stagesTracePrefixField.text = value
        }

    init {

        val panelBuilder = LabeledGridBuilder()
        val contentPanel = JPanel()
        contentPanel.layout = BorderLayout()

        packageNameField = JTextField(FIELD_LENGTH)
        packageNameField.toolTipText = "Enter application package. Required"

        activityNameField = JTextField(FIELD_LENGTH)
        activityNameField.toolTipText = "Enter entry point activity. Optional"

        fileNamePrefixField = JTextField(FIELD_LENGTH)
        fileNamePrefixField.toolTipText = "Adds file name prefix. Optional."

        val content = panelBuilder.content

        val buttons = JPanel()
        buttons.layout = FlowLayout()

        startButton = JButton("Start")
        startButton.addActionListener(StartRecordAction())
        buttons.add(startButton)

        stopButton = JButton("Stop")

        buttons.add(stopButton)
        stopButton.isEnabled = false

        statusLabel = JLabel(" ")
        defaultLabelColor = statusLabel.background

        // additional settings
        val additionalPanel = JPanel()
        additionalPanel.layout = BorderLayout()
        additionalPanel.border = EmptyBorder(8, 8, 8, 8)
        additionalPanel.add(JLabel("Sampling in microseconds:"), BorderLayout.LINE_START)

        logic = JavaMethodsDialogLogic(coroutineScope, dispatchers, this, settings, logger)

        recordModeComBox.addItemListener {
            if (it.stateChange != ItemEvent.SELECTED) {
                return@addItemListener
            }

            logic.selectedMode = recordModeComBox.selectedItem as RecordMode
        }

        samplingField.addActionListener {
            logic.onStartPressed()
        }
        stopButton.addActionListener {
            logic.onStopPressed()
        }
        fileNamePrefixField.addActionListener {
            logic.onStartPressed()
        }
        packageNameField.addActionListener {
            logic.onStartPressed()
        }
        activityNameField.addActionListener {
            logic.onStartPressed()
        }
        remoteDeviceAddressField.addActionListener {
            logic.onStartPressed()
        }
        serialNumberField.addActionListener {
            logic.onStartPressed()
        }
        stagesTracePrefixField.addActionListener {
            logic.onStartPressed()
        }

        panelBuilder.addLabeledComponent("package: ", packageNameField)
        panelBuilder.addLabeledComponent("activity: ", activityNameField)
        panelBuilder.addLabeledComponent("file name prefix: ", fileNamePrefixField)
        panelBuilder.addLabeledComponent("recording mode: ", recordModeComBox)
        panelBuilder.addLabeledComponent("buffer size (Mb): ", profilerBufferSizeField)
        panelBuilder.addSingleComponent(buildRemoteDevicePanel())
        panelBuilder.addSingleComponent(buildSerialNumberPanel())
        panelBuilder.addSingleComponent(buildMoreOptionsPanel())
        panelBuilder.addSingleComponent(buttons)

        additionalPanel.add(samplingField, BorderLayout.CENTER)
        panelBuilder.addSingleComponent(additionalPanel)

        val statusPanel = JPanel()
        statusPanel.layout = BorderLayout()
        statusPanel.border = BevelBorder(BevelBorder.LOWERED)
        statusPanel.add(statusLabel, BorderLayout.CENTER)

        val panelFont = statusLabel.font
        statusPanel.preferredSize = Dimension(width, panelFont.size + TEXT_PADDING)
        contentPanel.add(content, BorderLayout.CENTER)
        contentPanel.add(statusPanel, BorderLayout.SOUTH)

        contentPane = contentPanel
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        pack()
    }

    private fun buildMoreOptionsPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()

        panel.add(moreOptionsPanelCheckbox, BorderLayout.NORTH)

        val hiddenPanel = JPanel()
        hiddenPanel.layout = BorderLayout()
        hiddenPanel.border = BorderFactory.createEtchedBorder(EtchedBorder.RAISED)
        hiddenPanel.isVisible = false
        hiddenPanel.add(buildSystraceStagesPanel(), BorderLayout.NORTH)

        moreOptionsPanelCheckbox.addChangeListener {
            hiddenPanel.isVisible = moreOptionsPanelCheckbox.isSelected
            pack()
        }
        panel.add(hiddenPanel, BorderLayout.SOUTH)
        return panel
    }

    private fun buildSystraceStagesPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()

        panel.add(systraceStagesCheckbox, BorderLayout.NORTH)

        val hiddenPanel = JPanel()
        hiddenPanel.layout = BorderLayout()
        hiddenPanel.border = BorderFactory.createEtchedBorder(EtchedBorder.RAISED)
        hiddenPanel.isVisible = false
        val contentPanel = JPanel()
        hiddenPanel.add(contentPanel, BorderLayout.CENTER)

        contentPanel.layout = BorderLayout()
        contentPanel.border = EmptyBorder(8, 8, 8, 8)
        contentPanel.add(Label("Stages trace prefix"), BorderLayout.NORTH)
        contentPanel.add(stagesTracePrefixField, BorderLayout.CENTER)
        stagesTracePrefixField.toolTipText =
            "Systrace starting with this prefix will be considered stages"

        systraceStagesCheckbox.addChangeListener {
            hiddenPanel.isVisible = systraceStagesCheckbox.isSelected
            pack()
        }
        panel.add(hiddenPanel, BorderLayout.SOUTH)
        return panel
    }

    private fun buildRemoteDevicePanel(): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()
        remoteDeviceAddressField.toolTipText =
            "Remote device address. Optional. If not empty - will try to connect " +
                    "to remote device"

        panel.add(showRemotePanelCheckbox, BorderLayout.NORTH)

        val hiddenPanel = JPanel()
        hiddenPanel.layout = BorderLayout()
        hiddenPanel.border = BorderFactory.createEtchedBorder(EtchedBorder.RAISED)
        hiddenPanel.isVisible = false

        val contentPanel = JPanel()
        hiddenPanel.add(contentPanel, BorderLayout.CENTER)

        contentPanel.layout = BorderLayout()
        contentPanel.border = EmptyBorder(8, 8, 8, 8)
        contentPanel.add(Label("IP address:"), BorderLayout.NORTH)
        contentPanel.add(remoteDeviceAddressField, BorderLayout.CENTER)

        showRemotePanelCheckbox.addChangeListener {
            hiddenPanel.isVisible = showRemotePanelCheckbox.isSelected
            pack()
        }
        panel.add(hiddenPanel, BorderLayout.SOUTH)
        return panel
    }

    // TODO(geaden): The code is almost similar to buildRemoteDevicePanel
    //    so it should be extracted into common abstraction.
    private fun buildSerialNumberPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()
        serialNumberField.toolTipText = "Device serial number to record trace for"

        panel.add(showDevicePanelCheckbox, BorderLayout.NORTH)

        val hiddenPanel = JPanel()
        hiddenPanel.layout = BorderLayout()
        hiddenPanel.border = BorderFactory.createEtchedBorder(EtchedBorder.RAISED)
        hiddenPanel.isVisible = showDevicePanelCheckbox.isSelected

        val contentPanel = JPanel()
        hiddenPanel.add(contentPanel, BorderLayout.CENTER)

        contentPanel.layout = BorderLayout()
        contentPanel.border = EmptyBorder(8, 8, 8, 8)
        contentPanel.add(Label("Serial Number:"), BorderLayout.NORTH)
        contentPanel.add(serialNumberField, BorderLayout.CENTER)

        showDevicePanelCheckbox.toolTipText = "If not selected the first connected device is used"
        showDevicePanelCheckbox.addChangeListener {
            hiddenPanel.isVisible = showDevicePanelCheckbox.isSelected
            pack()
        }
        panel.add(hiddenPanel, BorderLayout.SOUTH)
        return panel
    }

    override fun onDialogClosed() {
        logger.d("$TAG: dialog before closing")
        logic.onDialogClosed()
        logger.d("$TAG: dialog closed")
    }

    fun getResult(): RecordedResult? = logic.result

    fun showDialog() {
        logger.d("$TAG: dialog shown")
        logic.onDialogShown()
        isVisible = true
    }

    override fun showErrorDialog(message: String, title: String) {
        JOptionPane.showMessageDialog(
            this, message, title, JOptionPane.ERROR_MESSAGE
        )
    }

    override fun showInfoDialog(message: String) {
        JOptionPane.showMessageDialog(this, message)
    }

    override fun enableStopButton(enabled: Boolean) {
        stopButton.isEnabled = enabled
    }

    override fun enableSampling() {
        samplingField.isEnabled = true
    }

    override fun disableSampling() {
        samplingField.isEnabled = false
    }

    override fun setStatusTextAndColor(text: String, color: Color) {
        SwingUtilities.invokeLater {
            statusLabel.foreground = color
            statusLabel.text = text
        }
    }

    override fun focusStopButton() {
        SwingUtilities.invokeLater { stopButton.requestFocus() }
    }

    override fun closeDialog() {
        SwingUtilities.invokeLater { isVisible = false }
    }

    override fun initialState() {
        SwingUtilities.invokeLater {
            stopButton.isEnabled = false
            startButton.isEnabled = true
            recordModeComBox.isEnabled = true
            fileNamePrefixField.isEnabled = true
            packageNameField.isEnabled = true
            packageNameField.requestFocus()
            activityNameField.isEnabled = true
            statusLabel.foreground = defaultLabelColor
            statusLabel.text = " "
        }
    }

    private inner class StartRecordAction : ActionListener {
        override fun actionPerformed(e: ActionEvent?) {
            logic.onStartPressed()
        }
    }

    override fun inProgressState() {
        stopButton.isEnabled = true
        startButton.isEnabled = false
        fileNamePrefixField.isEnabled = false
        packageNameField.isEnabled = false
        activityNameField.isEnabled = false
        recordModeComBox.isEditable = false
    }
}
