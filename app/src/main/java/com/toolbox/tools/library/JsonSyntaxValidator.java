package com.toolbox.tools.library;

final class JsonSyntaxValidator {
    private final String input;
    private int index;
    private int depth;

    private JsonSyntaxValidator(String input) {
        this.input = input;
    }

    static boolean isValid(String value) {
        if (value == null || value.length() > 1_048_576) return false;
        try {
            JsonSyntaxValidator parser = new JsonSyntaxValidator(value);
            parser.skipWhitespace();
            parser.readValue();
            parser.skipWhitespace();
            return parser.index == parser.input.length();
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private void readValue() {
        if (++depth > 64) fail();
        skipWhitespace();
        if (index >= input.length()) fail();
        char c = input.charAt(index);
        if (c == '{') readObject();
        else if (c == '[') readArray();
        else if (c == '"') readString();
        else if (c == 't') readLiteral("true");
        else if (c == 'f') readLiteral("false");
        else if (c == 'n') readLiteral("null");
        else readNumber();
        depth--;
    }

    private void readObject() {
        expect('{');
        skipWhitespace();
        if (peek('}')) { index++; return; }
        while (true) {
            skipWhitespace();
            readString();
            skipWhitespace();
            expect(':');
            readValue();
            skipWhitespace();
            if (peek('}')) { index++; return; }
            expect(',');
        }
    }

    private void readArray() {
        expect('[');
        skipWhitespace();
        if (peek(']')) { index++; return; }
        while (true) {
            readValue();
            skipWhitespace();
            if (peek(']')) { index++; return; }
            expect(',');
        }
    }

    private void readString() {
        expect('"');
        while (index < input.length()) {
            char c = input.charAt(index++);
            if (c == '"') return;
            if (c == '\\') {
                if (index >= input.length()) fail();
                char escaped = input.charAt(index++);
                if (escaped == 'u') {
                    for (int i = 0; i < 4; i++) {
                        if (index >= input.length()
                                || Character.digit(input.charAt(index++), 16) < 0) {
                            fail();
                        }
                    }
                } else if ("\"\\/bfnrt".indexOf(escaped) < 0) {
                    fail();
                }
            } else if (c < 0x20) {
                fail();
            }
        }
        fail();
    }

    private void readNumber() {
        int start = index;
        if (peek('-')) index++;
        if (peek('0')) {
            index++;
        } else {
            readDigits(true);
        }
        if (peek('.')) {
            index++;
            readDigits(true);
        }
        if (peek('e') || peek('E')) {
            index++;
            if (peek('+') || peek('-')) index++;
            readDigits(true);
        }
        if (index == start) fail();
    }

    private void readDigits(boolean required) {
        int start = index;
        while (index < input.length() && Character.isDigit(input.charAt(index))) {
            index++;
        }
        if (required && index == start) fail();
    }

    private void readLiteral(String literal) {
        if (!input.startsWith(literal, index)) fail();
        index += literal.length();
    }

    private void skipWhitespace() {
        while (index < input.length()) {
            char c = input.charAt(index);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') index++;
            else return;
        }
    }

    private boolean peek(char expected) {
        return index < input.length() && input.charAt(index) == expected;
    }

    private void expect(char expected) {
        if (!peek(expected)) fail();
        index++;
    }

    private static void fail() {
        throw new IllegalArgumentException("invalid JSON");
    }
}
