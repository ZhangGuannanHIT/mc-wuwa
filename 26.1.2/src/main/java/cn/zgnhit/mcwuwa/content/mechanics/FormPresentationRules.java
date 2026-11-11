package cn.zgnhit.mcwuwa.content.mechanics;

/** Public visual identity only. Never changes the player's physics or damage state. */
public final class FormPresentationRules {
    private FormPresentationRules() { }
    public static String model(String character, String form) {
        if ("aemeath".equals(character) && "mech".equals(form)) return "aemeath_mecha";
        if ("hsin".equals(character) && "fox".equals(form)) return "hsin_fox";
        return "";
    }
}
