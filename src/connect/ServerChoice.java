package connect;

public record ServerChoice(
        String host,
        int port,
        String name,
        int gameId,
        String state,
        int playerCount,
        int maximumPlayers
) {
    public String key() {
        return host + ":" + port + ":" + gameId;
    }

    @Override
    public String toString() {
        return name + " | " + host + ":" + port + " | " + state + " | " + playerCount + "/" + maximumPlayers;
    }
}
