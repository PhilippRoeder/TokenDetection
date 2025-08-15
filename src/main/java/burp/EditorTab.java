package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.settings.SettingsPanel;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class EditorTab implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Token Detector");
        api.logging().logToOutput("Author: Philipp Röder");
        api.logging().logToOutput("Version: " + loadVersion());

        // NEW: make settings globally available
        TokenSettings.init(api);

        api.proxy().registerRequestHandler(new TokenProxyHandler());
        api.http().registerHttpHandler(new TokenHttpHandler());

        // Option B: settings panel
        api.userInterface().registerSettingsPanel(new TokenSettingsPanel(api));
    }

    private String loadVersion() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("version.properties")) {
            Properties props = new Properties();
            if (input != null) {
                props.load(input);
                return props.getProperty("version", "Unknown");
            }
        } catch (Exception ignored) {}
        return "Error";
    }

    /* ===== Types moved here so they're available everywhere ===== */
    public enum Colour { RED, GREEN, BLUE, YELLOW, MAGENTA, CYAN, ORANGE, GRAY }

    public static class Rule implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public boolean enabled;
        public String name;
        public Colour colour;
        public String regex;
        public Rule(boolean enabled, String name, Colour colour, String regex) {
            this.enabled = enabled;
            this.name = name;
            this.colour = colour;
            this.regex = regex;
        }
    }

    /* ===== Settings panel that uses TokenSettings ===== */
    private static class TokenSettingsPanel implements SettingsPanel {
        private final JPanel panel;

        TokenSettingsPanel(MontoyaApi api) {
            List<Rule> rules = TokenSettings.loadAll();
            RulesPanel rulesPanel = new RulesPanel(rules, () -> TokenSettings.saveAll(rules));
            api.userInterface().applyThemeToComponent(rulesPanel);
            this.panel = rulesPanel;
        }

        @Override public JComponent uiComponent() { return panel; }
        @Override public Set<String> keywords() {
            return Set.of("token","regex","pattern","colour","detector","highlight");
        }
    }

    /* ===== Swing table UI (unchanged except save callback) ===== */
    static class RulesPanel extends JPanel {
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
            colourCol.setCellEditor(new DefaultCellEditor(new JComboBox<>(Colour.values())));

            table.setFillsViewportHeight(true);
            table.setAutoCreateRowSorter(true);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
            buttons.add(new JButton(new AbstractAction("Add") {
                @Override public void actionPerformed(ActionEvent e) {
                    model.addRow(new Rule(true, "New rule", Colour.GRAY, ""));
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

    static class RulesTableModel extends AbstractTableModel {
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
                case 2 -> Colour.class;
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
                case 2 -> r.colour = (Colour) aValue;
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
}
