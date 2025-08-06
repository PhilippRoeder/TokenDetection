package burp;

import burp.api.montoya.core.Annotations;
import burp.api.montoya.core.HighlightColor;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.proxy.http.ProxyRequestReceivedAction;

import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TokenDetector {

    private static final Pattern PASETO_PATTERN =
            Pattern.compile("v[0-9]\\.(local|public)\\.[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)?");
    private static final Pattern LTPA2_PATTERN =
            Pattern.compile("(?i)LtpaToken2=");
    private static final Pattern JWT_PATTERN = Pattern.compile(
            "eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"
    );

    public static Annotations detect(HttpRequest request){
        Annotations annotations = null;
        if(detectPaseto(request)){
            annotations = Annotations.annotations(null, HighlightColor.GREEN);
            return annotations;
        }
        if(detectLtpa2(request)){
            annotations = Annotations.annotations(null, HighlightColor.RED);
            return annotations;
        }
        if(detectJWT(request)){
            annotations = Annotations.annotations(null, HighlightColor.ORANGE);
            return annotations;
        }
        return annotations;
    }


    private static boolean detectPaseto (HttpRequest request){
        // 1) Headers (e.g. Authorization: Bearer <token>)
        for (HttpHeader header : request.headers()) {
            Matcher m = PASETO_PATTERN.matcher(header.value());
            if (m.find()) {
                return true;
            }
        }

        // 2) Body (JSON, form‑encoded, etc.)
        Matcher m = PASETO_PATTERN.matcher(request.bodyToString());
        return m.find();
    }
    private static boolean detectLtpa2 (HttpRequest request){
        String cookie = request.headerValue("Cookie");
        Matcher m = LTPA2_PATTERN.matcher(cookie);
        return m.find();
    }

    private static boolean detectJWT(HttpRequest request){
        boolean found=false;
        for (HttpHeader header : request.headers()) {
            if (header.name().equalsIgnoreCase("Authorization")) {
                String value = header.value();
                Matcher matcher1 = JWT_PATTERN.matcher(value);
                if (matcher1.find()) {
                    return true;
                }
            }
        }

        String body = request.bodyToString();
        Matcher matcher2 = JWT_PATTERN.matcher(body);
        return matcher2.find();

    }
}
