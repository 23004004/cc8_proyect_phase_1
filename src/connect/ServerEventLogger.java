package connect;

public final class ServerEventLogger {
    private final ConsoleEventLogger logger = new ConsoleEventLogger("SERVER");

    public void info(String message) {
        logger.info(message);
    }

    public void warning(String message) {
        logger.warning(message);
    }
}
