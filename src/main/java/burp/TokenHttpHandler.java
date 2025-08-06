package burp;

import burp.api.montoya.core.Annotations;
import burp.api.montoya.http.handler.*;

public class TokenHttpHandler implements HttpHandler {
    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent httpRequestToBeSent) {
        Annotations annotations =TokenDetector.detect(httpRequestToBeSent);
        return RequestToBeSentAction.continueWith(httpRequestToBeSent, annotations);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived httpResponseReceived) {
        return null;
    }
}
