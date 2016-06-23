package ru.proghouse.robocam;

/**
 * Created by Alexey Valuev on 05.04.2016.
 */
public class StringHelper {
    public static int intFromString(String string, int defaultValue) {
        if (string == null || string.length() == 0) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(string);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean booleanFromString(String string, boolean defaultValue) {
        if (string == null || string.length() == 0) {
            return defaultValue;
        }
        if (string.trim().equals("true") || string.equals("1"))
            return true;
        else if (string.trim().equals("false") || string.equals("0"))
            return false;
        return defaultValue;
    }

    public static String stringFromString(String string, String defaultValue) {
        if (string == null || string.length() == 0) {
            return defaultValue;
        }
        return string;
    }

    public static float floatFromString(String string, float defaultValue) {
        if (string == null || string.length() == 0) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(string);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
