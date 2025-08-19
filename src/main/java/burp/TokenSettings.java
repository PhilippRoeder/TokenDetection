package burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.persistence.Preferences;
import burp.api.montoya.core.HighlightColor;

import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.Base64;
import java.util.stream.Collectors;

public final class TokenSettings {
    private TokenSettings(){}
    private static final String PREF_KEY = "token_detector.rules.v1";
    private static Preferences PREFS;

    /** Call once from EditorTab.initialize(api) */
    public static synchronized void init(MontoyaApi api) {
        PREFS = api.persistence().preferences();
    }

    private static void ensureInit() {
        if (PREFS == null) {
            throw new IllegalStateException("TokenSettings not initialized. Call TokenSettings.init(api) in initialize().");
        }
    }

    /* -------- Load / Save (supports both Optional<String> and String Montoya builds) -------- */
    public static List<Rule> loadAll() {
        ensureInit();
        try {
            // Try call site compiled against String return type:
            return loadRules(((String) (Object) PREFS.getString(PREF_KEY)));
        } catch (ClassCastException e) {
            // Fallback if the API returns Optional<String>
            @SuppressWarnings("unchecked")
            Optional<String> opt = (Optional<String>) (Object) PREFS.getString(PREF_KEY);
            return loadRules(opt);
        }
    }

    public static List<Rule> loadEnabled() {
        return loadAll().stream().filter(r -> r.enabled).collect(Collectors.toList());
    }

    public static void saveAll(List<Rule> rules) {
        ensureInit();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(new ArrayList<>(rules));
            String encoded = Base64.getEncoder().encodeToString(baos.toByteArray());
            PREFS.setString(PREF_KEY, encoded);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Failed to save rules: " + e.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /* -------- Decode helpers (both signatures so either API compiles) -------- */
    private static List<Rule> loadRules(String encodedStr) {
        return loadRules(Optional.ofNullable(encodedStr));
    }
    private static List<Rule> loadRules(Optional<String> encodedOpt) {
        if (encodedOpt != null && encodedOpt.isPresent() && !encodedOpt.get().isEmpty()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(encodedOpt.get());
                try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                    @SuppressWarnings("unchecked")
                    List<Rule> list = (List<Rule>) ois.readObject();
                    return (list != null && !list.isEmpty()) ? list : defaults();
                }
            } catch (Exception ignored) {
                // fall back to defaults
            }
        }
        return defaults();
    }

    /* -------- Defaults shown on first run -------- */

    private static List<Rule> defaults() {
        List<Rule> list = new ArrayList<>();

        // LTPA2 cookie (usually Base64-ish, stored in Cookie header)
        list.add(new Rule(
                true,
                "LTPA2 token",
                EditorTab.Colour.RED,
                "(?i)\\bLtpaToken2=[^;]+"
        ));

        // JWT (compact JWS/JWT: header.payload.signature, URL-safe base64url chars)
        list.add(new Rule(
                true,
                "JWT token",
                EditorTab.Colour.ORANGE,
                "eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"
        ));

        // PASETO (vX.local/public.<payload>[.<footer>])
        list.add(new Rule(
                true,
                "PASETO token",
                EditorTab.Colour.GREEN,
                "v[0-9]\\.(local|public)\\.[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)?"
        ));

        return list;
    }

    /* -------- Color mapping for use in annotations -------- */
    public static HighlightColor toHighlight(EditorTab.Colour c) {
        return switch (c) {
            case RED     -> HighlightColor.RED;
            case ORANGE  -> HighlightColor.ORANGE;
            case YELLOW  -> HighlightColor.YELLOW;
            case GREEN   -> HighlightColor.GREEN;
            case CYAN    -> HighlightColor.CYAN;
            case BLUE    -> HighlightColor.BLUE;
            case MAGENTA -> HighlightColor.MAGENTA;
            case GRAY    -> HighlightColor.GRAY;
        };
    }
}

