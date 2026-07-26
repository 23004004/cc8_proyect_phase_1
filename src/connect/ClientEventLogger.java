package connect;

public final class ClientEventLogger {
    private final ConsoleEventLogger logger = new ConsoleEventLogger("CLIENT");

    public void info(String message) {
        logger.info(message);
    }

    public void warning(String message) {
        logger.warning(message);
    }
}
