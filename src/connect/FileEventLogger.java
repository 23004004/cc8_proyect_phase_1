package connect;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

final class FileEventLogger {
    private final Logger logger;

    FileEventLogger(String loggerName, String filePath) {
        this.logger = Logger.getLogger(loggerName);
        configure(filePath);
    }

    void info(String message) {
        logger.info(Objects.requireNonNullElse(message, ""));
    }

    void warning(String message) {
        logger.warning(Objects.requireNonNullElse(message, ""));
    }

    private void configure(String filePath) {
        try {
            Path path = Path.of(filePath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            FileHandler handler = new FileHandler(filePath, true);
            handler.setFormatter(new SimpleFormatter());
            logger.setUseParentHandlers(false);
            logger.addHandler(handler);
            logger.setLevel(Level.INFO);
        } catch (IOException ex) {
            System.err.println("No se pudo inicializar " + filePath + ": " + ex.getMessage());
        }
    }
}
