package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

import java.io.InputStream;
import java.util.Properties;

public class EditorTab implements BurpExtension {


    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Token Detector");
        api.logging().logToOutput("Author: Philipp Röder");

        String version = loadVersion();
        api.logging().logToOutput("Version: " + version);

        api.proxy().registerRequestHandler(new TokenProxyHandler());
        api.http().registerHttpHandler(new TokenHttpHandler());

    }

    private String loadVersion() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("version.properties")) {
            Properties props = new Properties();
            if (input != null) {
                props.load(input);
                return props.getProperty("version", "Unknown");
            } else {
                return "Not Found";
            }
        } catch (Exception e) {
            return "Error";
        }
    }
}
