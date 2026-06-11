package net.zanoria.lobby;

enum QueueMode {
    RELICWARS_1V1("RelicWars 1v1"),
    RELICWARS_2V2("RelicWars 2v2"),
    RELICWARS_4V4("RelicWars 4v4");

    private final String displayName;

    QueueMode(String displayName) {
        this.displayName = displayName;
    }

    String displayName() {
        return displayName;
    }
}
