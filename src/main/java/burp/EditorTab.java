package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.util.*;


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

    private String loadVersion(MontoyaApi api) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("version.properties")) {
            if (input != null) {
                Properties props = new Properties();
                props.load(input);
                return props.getProperty("version", "Unknown");
            } else {
                api.logging().logToOutput("[!] version.properties not found – using fallback.");
                return "Unknown";}
        } catch (Exception e) {
            api.logging().logToOutput("[!] Failed to load version.properties: " + e.getMessage());
            return "Unknown";
        }
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

    /* ===== Types moved here so they're available everywhere ===== */
    public enum Colour { RED, GREEN, BLUE, YELLOW, MAGENTA, CYAN, ORANGE, GRAY }
}
