package io.toolbox.contracts.runtime;

import java.util.Random;

public final class RuntimeContractsPropertyTest {
    private static final long SEED = 0x5AFE100L;
    private static final int CASES = 5000;
    private static final char[] ALPHABET = (
            "abcdefghijklmnopqrstuvwxyz"
                    + "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                    + "0123456789._- !$/@#"
                    + "é中Ω"
    ).toCharArray();

    private RuntimeContractsPropertyTest() {}

    public static void main(String[] args) {
        differentialStableIdOracle();
        metamorphicStableIdRelations();
        System.out.println("PUBLIC_RUNTIME_CONTRACT_PROPERTY_TESTS = PASS");
        System.out.println("PROPERTY_CASES=" + CASES);
        System.out.println("PROPERTY_SEED=" + SEED);
    }

    private static void differentialStableIdOracle() {
        Random random = new Random(SEED);
        for (int i = 0; i < CASES; i++) {
            int length = random.nextInt(Contracts.MAX_STABLE_ID_LENGTH + 24);
            StringBuilder value = new StringBuilder(length);
            for (int j = 0; j < length; j++) {
                value.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            }
            if ((i & 7) == 0) value.insert(0, ' ');
            if ((i & 15) == 0) value.append(' ');
            assertMatchesReference(value.toString());
        }

        assertMatchesReference(null);
        assertMatchesReference("");
        assertMatchesReference("   ");
        assertMatchesReference("tool.valid");
        assertMatchesReference("tool..invalid");
        assertMatchesReference("tool.invalid-");
        assertMatchesReference("Tool.invalid");
        assertMatchesReference("tool.é");
        assertMatchesReference("a".repeat(Contracts.MAX_STABLE_ID_LENGTH));
        assertMatchesReference("a".repeat(Contracts.MAX_STABLE_ID_LENGTH + 1));
    }

    private static void metamorphicStableIdRelations() {
        for (int i = 0; i < 250; i++) {
            String base = "tool.property.t" + i;
            String normalized = Contracts.requireStableId("  " + base + "  ", "toolId");
            check(base.equals(normalized), "trim relation must preserve valid stable ID");

            String extended = base + ".child";
            if (extended.length() <= Contracts.MAX_STABLE_ID_LENGTH) {
                check(extended.equals(Contracts.requireStableId(extended, "toolId")), "valid segment extension relation");
            }

            boolean rejectedUppercase = false;
            try {
                Contracts.requireStableId("T" + base.substring(1), "toolId");
            } catch (Contracts.ContractException expected) {
                rejectedUppercase = "CONTRACT_INVALID".equals(expected.code());
            }
            check(rejectedUppercase, "uppercase first character must be rejected");
        }
    }

    private static void assertMatchesReference(String raw) {
        boolean expected = referenceValidStableId(raw);
        boolean actual;
        try {
            Contracts.requireStableId(raw, "candidate");
            actual = true;
        } catch (Contracts.ContractException rejected) {
            actual = false;
        }
        check(expected == actual, "stable-id oracle mismatch for input=" + printable(raw));
    }

    private static boolean referenceValidStableId(String raw) {
        if (raw == null) return false;
        String value = raw.trim();
        if (value.isEmpty() || value.length() > Contracts.MAX_STABLE_ID_LENGTH) return false;
        if (!isLowerAscii(value.charAt(0))) return false;

        boolean previousWasSeparator = false;
        for (int i = 1; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (isLowerAscii(ch) || isDigit(ch)) {
                previousWasSeparator = false;
                continue;
            }
            if (ch == '.' || ch == '_' || ch == '-') {
                if (previousWasSeparator || i == value.length() - 1) return false;
                previousWasSeparator = true;
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean isLowerAscii(char ch) {
        return ch >= 'a' && ch <= 'z';
    }

    private static boolean isDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    private static String printable(String raw) {
        if (raw == null) return "<null>";
        return raw.replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
