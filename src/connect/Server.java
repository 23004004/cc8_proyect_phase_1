package connect;

import model.GameConfig;

public final class Server {
    private final GameConfig config;

    public Server(GameConfig config) {
        this.config = config;
    }

    public void start() {
        System.out.println("Servidor listo en el puerto " + config.serverPort() + ".");
    }
}
