package com.github.grishberg.profiler.ui.dialogs;

import com.github.grishberg.profiler.chart.BookmarksRectangle;
import com.github.grishberg.profiler.ui.BookMarkInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class NewBookmarkDialog extends CloseByEscapeDialog implements ChangeListener, ActionListener {
    public static final int ALPHA = 100;
    private JColorChooser colorChooser;
    private Color selectedColor = new Color(201, 137, 255, 117);
    private JTextField bookmarkName;
    @Nullable
    private BookMarkInfo result;

    public NewBookmarkDialog(Frame owner) {
        super(owner, "New Bookmark", true);
        JPanel content = new JPanel();
        content.setBorder(new EmptyBorder(4, 4, 4, 4));
        content.setLayout(new BorderLayout());

        bookmarkName = new JTextField(10);
        bookmarkName.addActionListener(this);
        content.add(bookmarkName, BorderLayout.PAGE_START);
        colorChooser = new JColorChooser(selectedColor);
        colorChooser.getSelectionModel().addChangeListener(this);
        content.add(colorChooser, BorderLayout.CENTER);

        JButton okButton = new JButton("OK");
        content.add(okButton, BorderLayout.PAGE_END);
        okButton.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                closeAfterSuccess();
            }
        });
        setContentPane(content);

        addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent ce) {
                bookmarkName.requestFocusInWindow();
            }
        });
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    }

    @Override
    public void onDialogClosed() {
        result = null;
        clearAndHide();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        closeAfterSuccess();
    }

    private void closeAfterSuccess() {
        result = new BookMarkInfo(bookmarkName.getText(), selectedColor);
        clearAndHide();
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        Color color = colorChooser.getColor();
        selectedColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), ALPHA);
    }

    public void clearAndHide() {
        bookmarkName.setEnabled(true);
        bookmarkName.setText(null);
        setVisible(false);
    }

    public BookMarkInfo getBookMarkInfo() {
        return result;
    }

    public void disableTitle() {
        bookmarkName.setEnabled(false);
    }

    public void showNewBookmarkDialog(JComponent relativeTo) {
        setLocationRelativeTo(relativeTo);
        setVisible(true);
    }

    /**
     * Show dialog for editing.
     */
    public void showEditBookmarkDialog(BookmarksRectangle src) {
        selectedColor = src.getColor();
        colorChooser.setColor(selectedColor);
        bookmarkName.setText(src.getName());
        setVisible(true);
    }
}
