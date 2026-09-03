package net.zanoria.lobby.pruefung;

import java.util.ArrayList;
import java.util.List;

/** Was waehrend eines gefahrenen Rumpfes wirklich aufgerufen wurde, in Reihenfolge. */
public final class Mitschrift {

    private final List<String> zeilen = new ArrayList<>();

    void schreibe(String zeile) {
        zeilen.add(zeile);
    }

    public List<String> zeilen() {
        return List.copyOf(zeilen);
    }

    public boolean enthaelt(String teil) {
        return zeilen.stream().anyMatch(z -> z.contains(teil));
    }

    @Override
    public String toString() {
        return zeilen.toString();
    }
}
