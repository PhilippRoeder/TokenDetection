package burp;

import burp.api.montoya.core.Annotations;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TokenDetector {

    /**
     * Returns the first matching annotation for enabled rules (settings-driven).
     * Search order:
     *   1) All headers (values)
     *   2) URL (full URL and query string)
     *   3) Body
     * If no rule matches, returns null.
     */
    public static Annotations detect(HttpRequest request) {
        List<Rule> enabled = TokenSettings.loadEnabled();
        if (enabled.isEmpty()) return null;

        // Cache body once (may be large)
        String body = request.bodyToString();

        // Cache URL and query once
        String fullUrl = "";
        String query = "";
        try {
            // request.url() should be available in Montoya; use its string representation.
            // If your HttpRequest API exposes a different method, adapt accordingly.
            fullUrl = request.url().toString();
            try {
                URI uri = new URI(fullUrl);
                String q = uri.getQuery();
                query = (q == null) ? "" : q;
            } catch (URISyntaxException e) {
                // if parsing fails, fall back to trying to extract query by splitting
                int qidx = fullUrl.indexOf('?');
                if (qidx >= 0 && qidx + 1 < fullUrl.length()) {
                    query = fullUrl.substring(qidx + 1);
                } else {
                    query = "";
                }
            }
        } catch (Exception ignored) {
            // leave fullUrl and query empty if anything goes wrong
            fullUrl = "";
            query = "";
        }

        for (Rule rule : enabled) {
            // Compile per-rule; validation already happens on save, so exceptions are unlikely
            Pattern p;
            try {
                p = Pattern.compile(rule.regex);
            } catch (Exception ex) {
                continue; // skip malformed rule just in case
            }

            final String notes = "Token: " + safe(rule.name) + " | Detection Rule (Regex): " + safe(rule.regex);

            // 1) Headers
            for (HttpHeader header : request.headers()) {
                Matcher mh = p.matcher(header.value());
                if (mh.find()) {
                    return Annotations.annotations(notes, TokenSettings.toHighlight(rule.colour));
                }
            }

            // 2) URL (full) and 2a) query string specifically
            if (!fullUrl.isEmpty()) {
                Matcher mu = p.matcher(fullUrl);
                if (mu.find()) {
                    return Annotations.annotations(notes, TokenSettings.toHighlight(rule.colour));
                }
            }

            if (!query.isEmpty()) {
                Matcher mq = p.matcher(query);
                if (mq.find()) {
                    return Annotations.annotations(notes, TokenSettings.toHighlight(rule.colour));
                }
            }

            // 3) Body
            if (!body.isEmpty()) {
                Matcher mb = p.matcher(body);
                if (mb.find()) {
                    return Annotations.annotations(notes, TokenSettings.toHighlight(rule.colour));
                }
            }
        }
        return null;
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "(unknown)" : s;
    }
}
