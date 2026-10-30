package com.seatly.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordUtilTest {
    @Test
    void storesSaltedHashAndVerifiesCorrectPassword() {
        String first = PasswordUtil.hash("Student@123");
        String second = PasswordUtil.hash("Student@123");

        assertNotEquals(first, second, "Random salts should produce different stored hashes.");
        assertTrue(PasswordUtil.verify("Student@123", first));
        assertFalse(PasswordUtil.verify("WrongPassword", first));
        assertFalse(PasswordUtil.verify("Student@123", "not-a-hash"));
    }
}
