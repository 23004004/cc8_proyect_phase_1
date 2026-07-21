package protocol.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonParser {
    private final String input;
    private int index;

    public JsonParser(String input) {
        this.input = input == null ? "" : input;
    }

    public Object parse() {
        skipWhitespace();
        Object value = parseValue();
        skipWhitespace();
        if (index != input.length()) {
            throw error("Unexpected trailing content");
        }
        return value;
    }

    private Object parseValue() {
        if (index >= input.length()) {
            throw error("Unexpected end of input");
        }

        char ch = input.charAt(index);
        return switch (ch) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't' -> parseLiteral("true", Boolean.TRUE);
            case 'f' -> parseLiteral("false", Boolean.FALSE);
            case 'n' -> parseLiteral("null", null);
            default -> {
                if (ch == '-' || Character.isDigit(ch)) {
                    yield parseNumber();
                }
                throw error("Unexpected character: " + ch);
            }
        };
    }

    private Map<String, Object> parseObject() {
        expect('{');
        skipWhitespace();
        Map<String, Object> result = new LinkedHashMap<>();
        if (peek('}')) {
            index++;
            return result;
        }

        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            result.put(key, parseValue());
            skipWhitespace();
            if (peek('}')) {
                index++;
                return result;
            }
            expect(',');
        }
    }

    private List<Object> parseArray() {
        expect('[');
        skipWhitespace();
        List<Object> result = new ArrayList<>();
        if (peek(']')) {
            index++;
            return result;
        }

        while (true) {
            skipWhitespace();
            result.add(parseValue());
            skipWhitespace();
            if (peek(']')) {
                index++;
                return result;
            }
            expect(',');
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder builder = new StringBuilder();
        while (index < input.length()) {
            char ch = input.charAt(index++);
            if (ch == '"') {
                return builder.toString();
            }
            if (ch == '\\') {
                if (index >= input.length()) {
                    throw error("Invalid escape sequence");
                }
                char escaped = input.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> builder.append(escaped);
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> builder.append(parseUnicode());
                    default -> throw error("Invalid escape sequence");
                }
            } else {
                builder.append(ch);
            }
        }
        throw error("Unterminated string");
    }

    private char parseUnicode() {
        if (index + 4 > input.length()) {
            throw error("Invalid unicode escape");
        }
        int codePoint = 0;
        for (int i = 0; i < 4; i++) {
            char ch = input.charAt(index++);
            int digit = Character.digit(ch, 16);
            if (digit < 0) {
                throw error("Invalid unicode escape");
            }
            codePoint = (codePoint << 4) + digit;
        }
        return (char) codePoint;
    }

    private Object parseLiteral(String literal, Object value) {
        if (!input.startsWith(literal, index)) {
            throw error("Invalid literal");
        }
        index += literal.length();
        return value;
    }

    private Number parseNumber() {
        int start = index;
        if (peek('-')) {
            index++;
        }
        if (peek('0')) {
            index++;
        } else {
            readDigits();
        }
        String number = input.substring(start, index);
        try {
            return Long.parseLong(number);
        } catch (NumberFormatException ex) {
            throw error("Invalid number");
        }
    }

    private void readDigits() {
        if (index >= input.length() || !Character.isDigit(input.charAt(index))) {
            throw error("Invalid number");
        }
        while (index < input.length() && Character.isDigit(input.charAt(index))) {
            index++;
        }
    }

    private void skipWhitespace() {
        while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
            index++;
        }
    }

    private void expect(char expected) {
        if (index >= input.length() || input.charAt(index) != expected) {
            throw error("Expected '" + expected + "'");
        }
        index++;
    }

    private boolean peek(char expected) {
        return index < input.length() && input.charAt(index) == expected;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at index " + index);
    }
}
