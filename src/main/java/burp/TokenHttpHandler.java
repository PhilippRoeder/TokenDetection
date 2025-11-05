package burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Annotations;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.ui.settings.SettingsPanelWithData;
import java.util.Objects;

public class TokenHttpHandler implements HttpHandler {

    private final SettingsPanelWithData settings;
    private final MontoyaApi api;
    private final TokenDetector detector;

    public TokenHttpHandler(SettingsPanelWithData settings, MontoyaApi api) {
        this.settings = Objects.requireNonNull(settings);
        this.api = Objects.requireNonNull(api);
        this.detector = new TokenDetector(settings);
    }

    private boolean markRequests() {
        return settings.getBoolean("markRequests");
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent httpRequestToBeSent) {

        if (!markRequests()) {
            return RequestToBeSentAction.continueWith(httpRequestToBeSent);
        }

        Annotations detected = detector.detect(httpRequestToBeSent);
        Annotations annotationsToPass = (detected != null) ? detected : Annotations.annotations();

        return RequestToBeSentAction.continueWith(httpRequestToBeSent, annotationsToPass);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived httpResponseReceived) {
        return ResponseReceivedAction.continueWith(httpResponseReceived);
    }
}
