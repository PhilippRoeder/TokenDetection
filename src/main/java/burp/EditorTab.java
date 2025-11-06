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
        api.extension().setName("ToDecahedron");

        logHeader(api);

        // NEW: make settings globally available
        TokenSettings.init(api);

        api.proxy().registerRequestHandler(new TokenProxyHandler());
        api.http().registerHttpHandler(new TokenHttpHandler());

        // Option B: settings panel
        api.userInterface().registerSettingsPanel(new TokenSettingsPanel(api));
    }

    private void logHeader(MontoyaApi api) {
        api.logging().logToOutput("====================================================");
        api.logging().logToOutput(" Project Information");
        api.logging().logToOutput("====================================================");
        api.logging().logToOutput(" Author       : Philipp Röder");
        api.logging().logToOutput(" Contributors : Sebastian Vetter, Kartik Rastogi");
        api.logging().logToOutput(" Version      : " + loadVersion());
        api.logging().logToOutput("====================================================");
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



}
