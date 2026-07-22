package connect;

public final class ServerEventLogger {
    private final FileEventLogger logger = new FileEventLogger(ServerEventLogger.class.getName(), "logs/server.log");

    public void info(String message) {
        logger.info(message);
    }

    public void warning(String message) {
        logger.warning(message);
    }
}
