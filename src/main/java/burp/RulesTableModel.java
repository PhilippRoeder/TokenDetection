package burp;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.List;
import java.util.regex.Pattern;

class RulesTableModel extends AbstractTableModel {
    private final List<Rule> rules;
    private static final String[] COLS = {"Enabled", "Name", "Colour", "Regex pattern"};

    RulesTableModel(List<Rule> rules) { this.rules = rules; }

    @Override public int getRowCount() { return rules.size(); }
    @Override public int getColumnCount() { return COLS.length; }
    @Override public String getColumnName(int col) { return COLS[col]; }
    @Override public Class<?> getColumnClass(int col) {
        return switch (col) {
            case 0 -> Boolean.class;
            case 1 -> String.class;
            case 2 -> EditorTab.Colour.class;
            case 3 -> String.class;
            default -> Object.class;
        };
    }
    @Override public boolean isCellEditable(int row, int col) { return true; }

    @Override
    public Object getValueAt(int row, int col) {
        Rule r = rules.get(row);
        return switch (col) {
            case 0 -> r.enabled;
            case 1 -> r.name;
            case 2 -> r.colour;
            case 3 -> r.regex;
            default -> null;
        };
    }

    @Override
    public void setValueAt(Object aValue, int row, int col) {
        Rule r = rules.get(row);
        switch (col) {
            case 0 -> r.enabled = (Boolean) aValue;
            case 1 -> r.name = (String) aValue;
            case 2 -> r.colour = (EditorTab.Colour) aValue;
            case 3 -> {
                String pattern = (String) aValue;
                try { Pattern.compile(pattern); }
                catch (Exception ex) {
                    JOptionPane.showMessageDialog(null,
                            "Invalid regex for row " + (row + 1) + ":\n" + ex.getMessage(),
                            "Regex error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                r.regex = pattern;
            }
        }
        fireTableRowsUpdated(row, row);
    }

    void addRow(Rule r) {
        int idx = rules.size();
        rules.add(r);
        fireTableRowsInserted(idx, idx);
    }
    void removeRow(int row) {
        rules.remove(row);
        fireTableRowsDeleted(row, row);
    }
    String validateAllRegexes() {
        for (int i = 0; i < rules.size(); i++) {
            try { Pattern.compile(rules.get(i).regex); }
            catch (Exception ex) { return "Row " + (i + 1) + " has invalid regex: " + ex.getMessage(); }
        }
        return null;
    }
}
