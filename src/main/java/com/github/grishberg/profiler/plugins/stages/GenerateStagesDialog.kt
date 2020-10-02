package com.github.grishberg.profiler.plugins.stages

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.android.profiler.core.ThreadItem
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.ui.dialogs.CloseByEscapeDialog
import java.awt.BorderLayout
import java.awt.Frame
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.File
import java.util.*
import javax.swing.*
import javax.swing.BoxLayout
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.filechooser.FileNameExtensionFilter


private const val addString = "Add"
private const val removeString = "Remove"

class GenerateStagesDialog(
    owner: Frame,
    input: AnalyzerResult,
    thread: ThreadItem,
    logger: AppLogger
) : CloseByEscapeDialog(owner, "Packages filter", true), ListSelectionListener {
    private val packageTextField = JTextField(20)
    private val generateButton = JButton("Generate stages template")
    private val addButton = JButton(addString)
    private val removeButton = JButton(removeString)

    private val listModel = DefaultListModel<String>()
    private val list = JList(listModel)

    init {
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.visibleRowCount = 5
        list.addListSelectionListener(this)
        val listScrollPane = JScrollPane(list)

        val hireListener = AddElementListener(addButton)
        addButton.actionCommand = addString
        addButton.addActionListener(hireListener)
        addButton.isEnabled = false

        removeButton.actionCommand = removeString
        removeButton.addActionListener(RemoveListener())
        packageTextField.toolTipText = "com.example"

        packageTextField.addActionListener(hireListener)
        packageTextField.document.addDocumentListener(hireListener)

        //Create a panel that uses BoxLayout.
        val buttonPane = JPanel()
        buttonPane.layout = BoxLayout(
            buttonPane,
            BoxLayout.LINE_AXIS
        )
        buttonPane.add(packageTextField)
        buttonPane.add(Box.createHorizontalStrut(5))
        buttonPane.add(removeButton)
        buttonPane.add(Box.createHorizontalStrut(5))
        buttonPane.add(JSeparator(SwingConstants.VERTICAL))
        buttonPane.add(Box.createHorizontalStrut(5))
        buttonPane.add(addButton)
        buttonPane.add(generateButton)
        buttonPane.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        add(listScrollPane, BorderLayout.CENTER)
        add(buttonPane, BorderLayout.PAGE_END)

        generateButton.addActionListener {
            saveStagesTemplate(owner, input, thread, logger)
        }
        pack()
        requestFocus()
    }

    private fun saveStagesTemplate(
        owner: Frame,
        input: AnalyzerResult,
        thread: ThreadItem,
        logger: AppLogger
    ) {
        val fileChooser = JFileChooser()
        fileChooser.dialogTitle = "Specify a file to save stages"
        val filter = FileNameExtensionFilter("Stages json", "json")
        fileChooser.fileFilter = filter

        val userSelection = fileChooser.showSaveDialog(owner)

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            var fileToSave = fileChooser.selectedFile
            if (fileToSave.extension.toLowerCase() != "json") {
                fileToSave = File(fileToSave.absolutePath + ".json")
            }
            Stages.saveToFile(fileToSave, input, thread, Collections.list(listModel.elements()), logger)
            isVisible = false
        }
    }

    private inner class RemoveListener : ActionListener {
        override fun actionPerformed(e: ActionEvent) {
            //This method can be called only if
            //there's a valid selection
            //so go ahead and remove whatever's selected.
            var index: Int = list.getSelectedIndex()
            listModel.remove(index)
            val size: Int = listModel.getSize()
            if (size == 0) { //Nobody's left, disable firing.
                removeButton.setEnabled(false)
            } else { //Select an index.
                if (index == listModel.getSize()) {
                    //removed item in last position
                    index--
                }
                list.selectedIndex = index
                list.ensureIndexIsVisible(index)
            }
        }
    }

    //This listener is shared by the text field and the hire button.
    private inner class AddElementListener(private val button: JButton) : ActionListener, DocumentListener {
        private var alreadyEnabled = false

        //Required by ActionListener.
        override fun actionPerformed(e: ActionEvent?) {
            val name: String = packageTextField.text

            //User didn't type in a unique name...
            if (name == "" || alreadyInList(name)) {
                Toolkit.getDefaultToolkit().beep()
                packageTextField.requestFocusInWindow()
                packageTextField.selectAll()
                return
            }
            var index: Int = list.getSelectedIndex() //get selected index
            if (index == -1) { //no selection, so insert at beginning
                index = 0
            } else {           //add after the selected item
                index++
            }
            listModel.insertElementAt(packageTextField.text, index)
            //If we just wanted to add to the end, we'd do this:
            //listModel.addElement(employeeName.getText());

            //Reset the text field.
            packageTextField.requestFocusInWindow()
            packageTextField.text = ""

            //Select the new item and make it visible.
            list.selectedIndex = index
            list.ensureIndexIsVisible(index)
        }

        //This method tests for string equality. You could certainly
        //get more sophisticated about the algorithm.  For example,
        //you might want to ignore white space and capitalization.
        protected fun alreadyInList(name: String?): Boolean {
            return listModel.contains(name)
        }

        //Required by DocumentListener.
        override fun insertUpdate(e: DocumentEvent?) {
            enableButton()
        }

        //Required by DocumentListener.
        override fun removeUpdate(e: DocumentEvent) {
            handleEmptyTextField(e)
        }

        //Required by DocumentListener.
        override fun changedUpdate(e: DocumentEvent) {
            if (!handleEmptyTextField(e)) {
                enableButton()
            }
        }

        private fun enableButton() {
            if (!alreadyEnabled) {
                button.isEnabled = true
            }
        }

        private fun handleEmptyTextField(e: DocumentEvent): Boolean {
            if (e.getDocument().getLength() <= 0) {
                button.isEnabled = false
                alreadyEnabled = false
                return true
            }
            return false
        }
    }

    //This method is required by ListSelectionListener.
    override fun valueChanged(e: ListSelectionEvent) {
        if (e.valueIsAdjusting == false) {
            if (list.selectedIndex == -1) {
                //No selection, disable fire button.
                removeButton.setEnabled(false)
            } else {
                //Selection, enable the fire button.
                removeButton.setEnabled(true)
            }
        }
    }
}
