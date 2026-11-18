package de.bsnsoft.esj.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Base64;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Holds {@link Base64Text} to the decoder of the runtime: the same texts accepted, the same
 * bytes, and only whole four-character groups handed on.
 *
 * <p>The reference decodes malformed texts by the hundred thousand, which is the load that
 * makes a runtime replace the decoder by its vectorised routine; the pom of this module
 * starts the tests with that routine switched off, so that on a processor with AVX-512
 * VBMI the reference does not write past its own arrays (CVE-2026-114938).
 */
class Base64TextTest {

    private static final char[] CHARACTERS = {'A', 'g', '+', '/', '=', '-', '_', ' ', 'Q'};

    private static byte[] reference(String text) {
        try {
            return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] subject(String text) {
        try {
            return Base64Text.decode(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void agrees(String text) {
        byte[] expected = reference(text);
        byte[] actual = subject(text);
        if (expected == null) {
            assertNull(actual, () -> "the runtime refuses '" + text + "', which was decoded");
            assertNull(Base64Text.whole(text), () -> "'" + text + "' was handed on");
        } else {
            if (actual == null) {
                fail("the runtime decodes '" + text + "', which was refused");
            }
            assertArrayEquals(expected, actual, () -> "the bytes of '" + text + "'");
            assertEquals(0, Base64Text.whole(text).length() % 4,
                    () -> "'" + text + "' was handed on as an incomplete group");
        }
    }

    @Test
    void everyShortTextIsAcceptedExactlyWhereTheRuntimeAcceptsIt() {
        for (int length = 0; length <= 6; length++) {
            int[] index = new int[length];
            while (true) {
                StringBuilder text = new StringBuilder(length);
                for (int i : index) {
                    text.append(CHARACTERS[i]);
                }
                agrees(text.toString());
                int k = length - 1;
                while (k >= 0 && ++index[k] == CHARACTERS.length) {
                    index[k] = 0;
                    k--;
                }
                if (k < 0) {
                    break;
                }
            }
        }
    }

    @Test
    void longTextsWithADefectNearTheEndAgreeAsWell() {
        Random random = new Random(20261118L);
        Base64.Encoder encoder = Base64.getEncoder();
        for (int round = 0; round < 20_000; round++) {
            byte[] bytes = new byte[random.nextInt(200)];
            random.nextBytes(bytes);
            StringBuilder text = new StringBuilder(encoder.encodeToString(bytes));
            switch (random.nextInt(5)) {
                case 0 -> { }
                case 1 -> text.append(CHARACTERS[random.nextInt(CHARACTERS.length)]);
                case 2 -> {
                    while (text.length() > 0 && text.charAt(text.length() - 1) == '=') {
                        text.setLength(text.length() - 1);
                    }
                }
                case 3 -> text.setLength(Math.max(0, text.length() - 1 - random.nextInt(3)));
                default -> {
                    if (text.length() > 0) {
                        text.setCharAt(random.nextInt(text.length()),
                                CHARACTERS[random.nextInt(CHARACTERS.length)]);
                    }
                }
            }
            agrees(text.toString());
        }
    }

    @Test
    void thePublishedTriggersAreRefusedBeforeTheyReachTheRuntime() {
        for (String text : new String[]{"A".repeat(128) + "=", "A".repeat(64) + "=",
                "A".repeat(129), "A".repeat(130) + "="}) {
            assertNull(Base64Text.whole(text), text.length() + " characters");
            assertThrows(IllegalArgumentException.class, () -> Base64Text.decode(text));
        }
    }

    @Test
    void anUnpaddedTextIsPaddedBeforeItIsHandedOn() {
        assertEquals("QUI=", Base64Text.whole("QUI"));
        assertEquals("QQ==", Base64Text.whole("QQ"));
        assertEquals("QUJD", Base64Text.whole("QUJD"));
        assertArrayEquals(new byte[]{'A', 'B'}, Base64Text.decode("QUI"));
    }
}
