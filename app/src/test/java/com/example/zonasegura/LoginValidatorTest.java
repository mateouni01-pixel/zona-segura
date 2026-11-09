package com.example.zonasegura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LoginValidatorTest {
    @Test
    public void acceptsTrimmedEmailAndNonEmptyPassword() {
        LoginValidator.Result result = LoginValidator.validate("  vecina@example.org  ", "clave segura");
        assertTrue(result.isValid());
    }

    @Test
    public void rejectsMissingFields() {
        LoginValidator.Result result = LoginValidator.validate("  ", "");
        assertEquals(LoginValidator.EmailError.EMPTY, result.emailError);
        assertTrue(result.passwordMissing);
        assertFalse(result.isValid());
    }

    @Test
    public void rejectsMalformedEmailWithoutRejectingPassword() {
        LoginValidator.Result result = LoginValidator.validate("correo-invalido", "cualquier clave");
        assertEquals(LoginValidator.EmailError.INVALID, result.emailError);
        assertFalse(result.passwordMissing);
        assertFalse(result.isValid());
    }
}
