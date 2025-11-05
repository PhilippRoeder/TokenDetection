package burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Annotations;
import burp.api.montoya.proxy.http.InterceptedRequest;
import burp.api.montoya.proxy.http.ProxyRequestHandler;
import burp.api.montoya.proxy.http.ProxyRequestReceivedAction;
import burp.api.montoya.proxy.http.ProxyRequestToBeSentAction;
import burp.api.montoya.ui.settings.SettingsPanelWithData;
import java.util.Objects;

public class TokenProxyHandler implements ProxyRequestHandler {

    private final SettingsPanelWithData settings;
    private final MontoyaApi api;
    private final TokenDetector detector;

    public TokenProxyHandler(SettingsPanelWithData settings, MontoyaApi api) {
        this.settings = Objects.requireNonNull(settings);
        this.api = Objects.requireNonNull(api);
        this.detector = new TokenDetector(settings);
    }

    private boolean markRequests() {
        return settings.getBoolean("markRequests");
    }

    @Override
    public ProxyRequestReceivedAction handleRequestReceived(InterceptedRequest interceptedRequest) {

        // If marking is disabled, return without annotations
        if (!markRequests()) {
            return ProxyRequestReceivedAction.continueWith(interceptedRequest);
        }

        // run detector
        Annotations detected = detector.detect(interceptedRequest);

        // If nothing found, provide an empty annotation
        Annotations annotationsToPass = (detected != null) ? detected : Annotations.annotations();

        return ProxyRequestReceivedAction.continueWith(interceptedRequest, annotationsToPass);
    }

    @Override
    public ProxyRequestToBeSentAction handleRequestToBeSent(InterceptedRequest interceptedRequest) {

        if (!markRequests()) {
            return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
        }

        Annotations detected = detector.detect(interceptedRequest);
        Annotations annotationsToPass = (detected != null) ? detected : Annotations.annotations();

        return ProxyRequestToBeSentAction.continueWith(interceptedRequest, annotationsToPass);
    }
}
