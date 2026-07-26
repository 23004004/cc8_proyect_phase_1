package connect;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

final class ConsoleEventLogger {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final String source;

    ConsoleEventLogger(String source) {
        this.source = source;
    }

    void info(String message) {
        String simplified = simplify(message);
        if (!simplified.isBlank()) {
            System.out.println(prefix("INFO") + simplified);
        }
    }

    void warning(String message) {
        String simplified = simplify(message);
        if (!simplified.isBlank()) {
            System.out.println(prefix("WARN") + simplified);
        }
    }

    private String prefix(String level) {
        return "[" + TIME_FORMAT.format(LocalTime.now()) + "][" + source + "][" + level + "] ";
    }

    private String simplify(String message) {
        String value = message == null ? "" : message.trim();
        if (value.isBlank()
                || value.startsWith("INPUT ")
                || value.startsWith("INPUT_SENT")
                || value.startsWith("INTERACT ")
                || value.startsWith("INTERACT_SENT")
                || value.startsWith("JOIN_SENT")
                || value.startsWith("TCP_ACCEPTED")
                || value.startsWith("GAME_COUNTDOWN")
                || value.startsWith("CLIENT_STOPPED")) {
            return "";
        }
        if (value.startsWith("SERVER_STARTED")) {
            return "Servidor iniciado.";
        }
        if (value.startsWith("CONNECTING")) {
            return "Conectando a " + field(value, "host") + ":" + field(value, "port") + ".";
        }
        if (value.startsWith("JOIN_ACCEPTED")) {
            String name = field(value, "name");
            String playerId = field(value, "playerId");
            return name.isBlank()
                    ? "Conectado como jugador #" + playerId + "."
                    : "Jugador conectado: " + name + " (#" + playerId + ").";
        }
        if (value.startsWith("JOIN_REJECTED")) {
            return "JOIN rechazado: " + field(value, "reason") + ".";
        }
        if (value.startsWith("MATCH_RUNNING") || value.startsWith("GAME_STARTED")) {
            return "Partida iniciada.";
        }
        if (value.startsWith("MATCH_FINISHED")) {
            return "Partida finalizada. Ganador #" + field(value, "winnerId") + ".";
        }
        if (value.startsWith("GAME_OVER")) {
            String winnerName = field(value, "winnerName");
            return winnerName.isBlank() ? "Partida finalizada." : "Partida finalizada. Ganador: " + winnerName + ".";
        }
        if (value.startsWith("LEAVE") || value.startsWith("LEAVE_SENT")) {
            return "Jugador salio: #" + field(value, "playerId") + ".";
        }
        if (value.startsWith("CLIENT_DISCONNECTED_BEFORE_JOIN")) {
            return "";
        }
        return value;
    }

    private String field(String message, String key) {
        String prefix = key + "=";
        int start = message.indexOf(prefix);
        if (start < 0) {
            return "";
        }
        start += prefix.length();
        int end = message.indexOf(' ', start);
        return end < 0 ? message.substring(start) : message.substring(start, end);
    }
}
