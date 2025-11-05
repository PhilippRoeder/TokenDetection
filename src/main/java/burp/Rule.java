package burp;

public class Rule implements java.io.Serializable {
    public boolean enabled;
    public String name;
    public EditorTab.Colour colour;
    public String regex;
    public Rule(boolean enabled, String name, EditorTab.Colour colour, String regex) {
        this.enabled = enabled;
        this.name = name;
        this.colour = colour;
        this.regex = regex;
    }
}