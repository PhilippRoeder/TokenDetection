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
        // Make first three columns compact; let "Regex pattern" eat remaining space
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        // Enabled (checkbox) — very narrow and fixed-ish
        TableColumn c0 = table.getColumnModel().getColumn(0);
        c0.setMinWidth(36);
        c0.setPreferredWidth(40);
        c0.setMaxWidth(60);

        // Name — small by default, but user can expand if needed
        TableColumn c1 = table.getColumnModel().getColumn(1);
        c1.setMinWidth(80);
        c1.setPreferredWidth(110);
        // no max width, so the user can grow it

        // Colour — narrow, mostly fixed
        TableColumn c2 = table.getColumnModel().getColumn(2);
        c2.setMinWidth(70);
        c2.setPreferredWidth(90);
        c2.setMaxWidth(150);

        // Regex pattern — get the rest of the space
        TableColumn c3 = table.getColumnModel().getColumn(3);
        c3.setPreferredWidth(800);

        // (Optional) slightly tighter rows
        table.setRowHeight(22);

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

        buttons.add(new JButton(new AbstractAction("Remove") {
            @Override public void actionPerformed(ActionEvent e) {
                int[] sel = table.getSelectedRows();
                for (int i = sel.length - 1; i >= 0; i--) {
                    int mi = table.convertRowIndexToModel(sel[i]);
                    model.removeRow(mi);
                }
            }
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
            }
        }));

        add(buttons, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JLabel hint = new JLabel("Press Space or Double-click cells to edit. Regex is Java Pattern syntax.");
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
