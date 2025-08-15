package burp;

import burp.api.montoya.core.Annotations;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TokenDetector {

    /**
     * Returns the first matching annotation for enabled rules (settings-driven).
     * Search order:
     *   1) All headers (values)
     *   2) Body
     * If no rule matches, returns null.
     */
    public static Annotations detect(HttpRequest request) {
        List<EditorTab.Rule> enabled = TokenSettings.loadEnabled();
        if (enabled.isEmpty()) return null;

        // Cache body once (may be large)
        String body = request.bodyToString();

        for (EditorTab.Rule rule : enabled) {
            // Compile per-rule; validation already happens on save, so exceptions are unlikely
            Pattern p;
            try {
                p = Pattern.compile(rule.regex);
            } catch (Exception ex) {
                continue; // skip malformed rule just in case
            }

            // 1) Headers
            for (HttpHeader header : request.headers()) {
                Matcher mh = p.matcher(header.value());
                if (mh.find()) {
                    return Annotations.annotations(null, TokenSettings.toHighlight(rule.colour));
                }
            }

            // 2) Body
            if (!body.isEmpty()) {
                Matcher mb = p.matcher(body);
                if (mb.find()) {
                    return Annotations.annotations(null, TokenSettings.toHighlight(rule.colour));
                }
            }
        }
        return null;
    }
}
