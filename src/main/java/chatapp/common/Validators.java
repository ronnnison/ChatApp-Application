package chatapp.common;

import java.util.regex.Pattern;

/** Input rules used by BOTH the client (instant feedback) and the server (real enforcement). */
public final class Validators {
    private Validators() { }

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,20}$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /** @return an error message, or null when the registration data is valid. */
    public static String validateRegistration(Payloads.Credentials c) {
        if (c == null) return "Missing data.";
        if (c.fullName == null || c.fullName.trim().length() < 2 || c.fullName.trim().length() > 60)
            return "Full name must be 2-60 characters.";
        if (c.username == null || !USERNAME.matcher(c.username).matches())
            return "Username must be 3-20 letters, digits or underscore.";
        if (c.email == null || c.email.length() > 100 || !EMAIL.matcher(c.email).matches())
            return "Enter a valid email address.";
        return validatePassword(c.password);
    }

    public static String validatePassword(String pw) {
        if (pw == null || pw.length() < 8 || pw.length() > 64)
            return "Password must be 8-64 characters.";
        boolean letter = false, digit = false;
        for (char ch : pw.toCharArray()) {
            if (Character.isLetter(ch)) letter = true;
            if (Character.isDigit(ch)) digit = true;
        }
        return (letter && digit) ? null : "Password needs at least one letter and one digit.";
    }
}
