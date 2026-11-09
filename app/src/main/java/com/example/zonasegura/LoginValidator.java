package com.example.zonasegura;

import java.util.regex.Pattern;

final class LoginValidator {
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    enum EmailError { NONE, EMPTY, INVALID }

    static final class Result {
        final EmailError emailError;
        final boolean passwordMissing;

        Result(EmailError emailError, boolean passwordMissing) {
            this.emailError = emailError;
            this.passwordMissing = passwordMissing;
        }

        boolean isValid() {
            return emailError == EmailError.NONE && !passwordMissing;
        }
    }

    static Result validate(String email, String password) {
        String normalizedEmail = email == null ? "" : email.trim();
        EmailError emailError;
        if (normalizedEmail.isEmpty()) {
            emailError = EmailError.EMPTY;
        } else if (!EMAIL.matcher(normalizedEmail).matches()) {
            emailError = EmailError.INVALID;
        } else {
            emailError = EmailError.NONE;
        }
        return new Result(emailError, password == null || password.isEmpty());
    }

    private LoginValidator() { }
}
