package burp;

import burp.api.montoya.core.Annotations;
import burp.api.montoya.core.HighlightColor;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.settings.SettingsPanelWithData;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.Objects;

/**
 * TokenDetector reads regex patterns and color names from a SettingsPanelWithData
 * and uses them to detect tokens in requests. Invalid user patterns fall back to defaults.
 */
public class TokenDetector {

    public static final String KEY_PASETO = "PASETO_PATTERN";
    public static final String KEY_PASETO_COLOR = "PASETO_COLOR";
    public static final String KEY_LTPA2 = "LTPA2_PATTERN";
    public static final String KEY_LTPA2_COLOR = "LTPA2_COLOR";
    public static final String KEY_JWT = "JWT_PATTERN";
    public static final String KEY_JWT_COLOR = "JWT_COLOR";

    private static final String DEFAULT_PASETO =
            "v[0-9]\\\\.(local|public)\\\\.[A-Za-z0-9_-]+(?:\\\\.[A-Za-z0-9_-]+)?";
    private static final String DEFAULT_LTPA2 =
            "(?i)LtpaToken2=";
    private static final String DEFAULT_JWT =
            "eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+";

    private final SettingsPanelWithData settings;

    public TokenDetector(SettingsPanelWithData settings) {
        this.settings = Objects.requireNonNull(settings);
    }

    /**
     * Detect tokens and return an annotation with the proper color.
     * If no token is found, return null.
     */
    public Annotations detect(HttpRequest request) {
        // PASETO
        Pattern pasetoPattern = compileOrDefault(KEY_PASETO, DEFAULT_PASETO);
        if (detectWithPatternInHeadersOrBody(pasetoPattern, request)) {
            HighlightColor color = colorFromString(settings.getString(KEY_PASETO_COLOR), HighlightColor.GREEN);
            return Annotations.annotations("PASETO token detected", color);
        }

        // LTPA2
        Pattern ltpaPattern = compileOrDefault(KEY_LTPA2, DEFAULT_LTPA2);
        if (detectLtpa2(ltpaPattern, request)) {
            HighlightColor color = colorFromString(settings.getString(KEY_LTPA2_COLOR), HighlightColor.RED);
            return Annotations.annotations("LtpaToken2 detected", color);
        }

        // JWT
        Pattern jwtPattern = compileOrDefault(KEY_JWT, DEFAULT_JWT);
        if (detectWithAuthHeaderOrBody(jwtPattern, request)) {
            HighlightColor color = colorFromString(settings.getString(KEY_JWT_COLOR), HighlightColor.ORANGE);
            return Annotations.annotations("JWT detected", color);
        }

        return null;
    }

    /* -------------------
       Detection helpers
       ------------------- */

    private boolean detectWithPatternInHeadersOrBody(Pattern pattern, HttpRequest request) {
        if (pattern == null) return false;

        for (HttpHeader header : request.headers()) {
            if (pattern.matcher(header.value()).find()) return true;
        }

        return pattern.matcher(safeString(request.bodyToString())).find();
    }

    private boolean detectWithAuthHeaderOrBody(Pattern pattern, HttpRequest request) {
        if (pattern == null) return false;

        for (HttpHeader header : request.headers()) {
            if (header.name().equalsIgnoreCase("Authorization") &&
                    pattern.matcher(header.value()).find()) {
                return true;
            }
        }

        return pattern.matcher(safeString(request.bodyToString())).find();
    }

    private boolean detectLtpa2(Pattern pattern, HttpRequest request) {
        if (pattern == null) return false;

        String cookieHeader = safeString(request.headerValue("Cookie"));
        if (pattern.matcher(cookieHeader).find()) return true;

        String body = safeString(request.bodyToString());
        return pattern.matcher(body).find();
    }

    /* -------------------
       Utilities
       ------------------- */

    private Pattern compileOrDefault(String settingsKey, String defaultRegex) {
        String userPattern = settings.getString(settingsKey);
        if (userPattern != null) userPattern = userPattern.trim();

        if (userPattern != null && !userPattern.isEmpty()) {
            try {
                return Pattern.compile(userPattern);
            } catch (PatternSyntaxException ignored) {}
        }

        try {
            return Pattern.compile(defaultRegex);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    private static String safeString(String in) {
        return in == null ? "" : in;
    }

    /**
     * Convert a string (e.g., "RED") to HighlightColor.
     * Returns fallback if invalid or null.
     */
    private HighlightColor colorFromString(String colorName, HighlightColor fallback) {
        if (colorName == null) return fallback;

        switch (colorName.trim().toUpperCase()) {
            case "NONE": return HighlightColor.NONE;
            case "RED": return HighlightColor.RED;
            case "ORANGE": return HighlightColor.ORANGE;
            case "YELLOW": return HighlightColor.YELLOW;
            case "GREEN": return HighlightColor.GREEN;
            case "CYAN": return HighlightColor.CYAN;
            case "BLUE": return HighlightColor.BLUE;
            case "PINK": return HighlightColor.PINK;
            case "MAGENTA": return HighlightColor.MAGENTA;
            case "GRAY": return HighlightColor.GRAY;
            default: return fallback;
        }
    }
}
