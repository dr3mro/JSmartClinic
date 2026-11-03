package org.amrosman.jsmartclinic.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** Resource-bundle access for the small set of localized shell strings. */
public final class I18n {

    private static Locale locale = Locale.ENGLISH;
    private static ResourceBundle bundle = load(locale);

    private I18n() {
    }

    public static void setLanguage(String code) {
        locale = "ar".equalsIgnoreCase(code) ? Locale.forLanguageTag("ar") : Locale.ENGLISH;
        bundle = load(locale);
    }

    public static boolean rightToLeft() {
        return "ar".equals(locale.getLanguage());
    }

    public static String t(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    public static String t(String key, Object... args) {
        return MessageFormat.format(t(key), args);
    }

    private static ResourceBundle load(Locale locale) {
        try {
            return ResourceBundle.getBundle("i18n.messages", locale);
        } catch (MissingResourceException e) {
            return ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH);
        }
    }
}
