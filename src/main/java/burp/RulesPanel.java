package burp;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

/* ===== Swing table UI (unchanged except save callback) ===== */
class RulesPanel extends JPanel {
    private final RulesTableModel model;
    private final JTable table;
    private final Runnable onSave;

    RulesPanel(List<Rule> rules, Runnable onSave) {
        super(new BorderLayout(8,8));
        this.onSave = onSave;
        this.model = new RulesTableModel(rules);
        this.table = new JTable(model);

        TableColumn enabledCol = table.getColumnModel().getColumn(0);
        enabledCol.setCellEditor(new DefaultCellEditor(new JCheckBox()));

        TableColumn colourCol = table.getColumnModel().getColumn(2);
        colourCol.setCellEditor(new DefaultCellEditor(new JComboBox<>(EditorTab.Colour.values())));

        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));

        buttons.add(new JButton(new AbstractAction("Add") {
            @Override public void actionPerformed(ActionEvent e) {
                model.addRow(new Rule(true, "New rule", EditorTab.Colour.GRAY, ""));
            }
        }));

        buttons.add(new JButton(new AbstractAction("Delete selected") {
            @Override public void actionPerformed(ActionEvent e) {
                int[] sel = table.getSelectedRows();
                for (int i = sel.length - 1; i >= 0; i--) {
                    int mi = table.convertRowIndexToModel(sel[i]);
                    model.removeRow(mi);
                }
            }
        }));

        buttons.add(new JButton(new AbstractAction("Enable") {
            @Override public void actionPerformed(ActionEvent e) { setSelectedEnabled(true); }
        }));

        buttons.add(new JButton(new AbstractAction("Disable") {
            @Override public void actionPerformed(ActionEvent e) { setSelectedEnabled(false); }
        }));
        buttons.add(Box.createHorizontalStrut(16));

        buttons.add(new JButton(new AbstractAction("Save") {
            @Override public void actionPerformed(ActionEvent e) {
                String err = model.validateAllRegexes();
                if (err != null) {
                    JOptionPane.showMessageDialog(RulesPanel.this, err,
                            "Invalid regex", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                onSave.run();
                JOptionPane.showMessageDialog(RulesPanel.this, "Saved.", "Settings",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        }));

        add(buttons, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JLabel hint = new JLabel("Double-click cells to edit. Regex is Java Pattern syntax.");
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, hint.getFont().getSize() - 1f));
        add(hint, BorderLayout.SOUTH);
    }

    private void setSelectedEnabled(boolean value) {
        int[] sel = table.getSelectedRows();
        for (int row : sel) {
            int mi = table.convertRowIndexToModel(row);
            model.setValueAt(value, mi, 0);
        }
    }
}
