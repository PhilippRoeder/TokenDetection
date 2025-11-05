package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.settings.SettingsPanelBuilder;
import burp.api.montoya.ui.settings.SettingsPanelPersistence;
import burp.api.montoya.ui.settings.SettingsPanelSetting;
import burp.api.montoya.ui.settings.SettingsPanelWithData;

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
    private SettingsPanelWithData settings;

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Token Detector");

        logHeader(api);
        setupSettings(api);
        registerHandlers(api);
    }

    private void logHeader(MontoyaApi api) {
        api.logging().logToOutput("====================================================");
        api.logging().logToOutput(" Project Information");
        api.logging().logToOutput("====================================================");
        api.logging().logToOutput(" Author       : Philipp Röder, Sebastian Vetter");
        api.logging().logToOutput(" Contributors : Kartik Rastogi");
        api.logging().logToOutput(" Version      : " + loadVersion(api));
        api.logging().logToOutput("====================================================");
        api.logging().logToOutput(" Further logging below on found token...");
        api.logging().logToOutput("====================================================");
    }

    /**
     * Builds and registers the settings panel for the extension.
     */
    private void setupSettings(MontoyaApi api) {
        settings = SettingsPanelBuilder.settingsPanel()
                .withPersistence(SettingsPanelPersistence.USER_SETTINGS)
                .withTitle("Token Detection Settings")
                .withDescription(
                    "Toggle request marking. \n" +
                    "For colors, only the following names are supported: NONE, RED, ORANGE, YELLOW, GREEN, CYAN, BLUE, PINK, MAGENTA, GRAY."
                )
                .withSettings(
                        // Global toggle
                        SettingsPanelSetting.booleanSetting("markRequests", true),

                        // PASETO
                        SettingsPanelSetting.stringSetting(
                                "PASETO_PATTERN",
                                "v[0-9]\\.(local|public)\\.[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)?"
                        ),
                        SettingsPanelSetting.stringSetting(
                                "PASETO_COLOR",
                                "GREEN"
                        ),

                        // LTPA2
                        SettingsPanelSetting.stringSetting(
                                "LTPA2_PATTERN",
                                "(?i)LtpaToken2="
                        ),
                        SettingsPanelSetting.stringSetting(
                                "LTPA2_COLOR",
                                "RED"
                        ),

                        // JWT
                        SettingsPanelSetting.stringSetting(
                                "JWT_PATTERN",
                                "eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"
                        ),
                        SettingsPanelSetting.stringSetting(
                                "JWT_COLOR",
                                "ORANGE"
                        )
                )
                .build();

        api.userInterface().registerSettingsPanel(settings);
    }

    private void registerHandlers(MontoyaApi api) {
        api.proxy().registerRequestHandler(new TokenProxyHandler(settings, api));
        api.http().registerHttpHandler(new TokenHttpHandler(settings, api));
    }

    private String loadVersion(MontoyaApi api) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("version.properties")) {
            if (input != null) {
                Properties props = new Properties();
                props.load(input);
                return props.getProperty("version", "Unknown");
            } else {
                api.logging().logToOutput("[!] version.properties not found – using fallback.");
                return "Unknown";
            }
        } catch (Exception e) {
            api.logging().logToOutput("[!] Failed to load version.properties: " + e.getMessage());
            return "Unknown";
        }
    }

    public SettingsPanelWithData getSettingsPanel() {
        return settings;
    }
}
