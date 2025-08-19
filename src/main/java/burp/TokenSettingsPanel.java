package burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.settings.SettingsPanel;

import javax.swing.*;
import java.util.List;
import java.util.Set;

/* ===== Settings panel that uses TokenSettings ===== */
class TokenSettingsPanel implements SettingsPanel {
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
