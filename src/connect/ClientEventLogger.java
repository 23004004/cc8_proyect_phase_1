package connect;

public final class ClientEventLogger {
    private final FileEventLogger logger = new FileEventLogger(ClientEventLogger.class.getName(), "logs/client.log");

    public void info(String message) {
        logger.info(message);
    }

    public void warning(String message) {
        logger.warning(message);
    }
}
