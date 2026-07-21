package protocol.json;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class JsonWriter {
    private JsonWriter() {
    }

    public static String write(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return '"' + escape(string) + '"';
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            return writeObject(map);
        }
        if (value instanceof Iterable<?> iterable) {
            return writeArray(iterable);
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
    }

    private static String writeObject(Map<?, ?> map) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<?, ?> entry = iterator.next();
            builder.append(write(String.valueOf(entry.getKey())));
            builder.append(':');
            builder.append(write(entry.getValue()));
            if (iterator.hasNext()) {
                builder.append(',');
            }
        }
        builder.append('}');
        return builder.toString();
    }

    private static String writeArray(Iterable<?> iterable) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        Iterator<?> iterator = iterable.iterator();
        while (iterator.hasNext()) {
            builder.append(write(iterator.next()));
            if (iterator.hasNext()) {
                builder.append(',');
            }
        }
        builder.append(']');
        return builder.toString();
    }

    public static String escape(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }
}
