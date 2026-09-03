package net.zanoria.lobby.pruefung;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * Eine mitschreibende {@link Proxy}-Attrappe fuer Bukkit-Schnittstellen.
 *
 * <p>⚠️ <b>Sie ist laut.</b> Was nicht eingetragen ist, wird NICHT still mit {@code null}
 * beantwortet, sondern bricht mit dem Methodennamen ab. Eine stille Attrappe beantwortet jede
 * vergessene Frage mit {@code null}, der Rumpf laeuft in einen Zweig, den niemand gemeint hat,
 * und der Fall ist gruen.
 */
public final class Attrappe {

    private final Map<String, Object> antworten = new HashMap<>();
    private final Mitschrift mitschrift;
    private final String name;

    private Attrappe(String name, Mitschrift mitschrift) {
        this.name = name;
        this.mitschrift = mitschrift;
    }

    public static Attrappe von(String name, Mitschrift mitschrift) {
        return new Attrappe(name, mitschrift);
    }

    /** Traegt eine Antwort fuer einen Methodennamen ein. */
    public Attrappe antwortet(String methode, Object wert) {
        antworten.put(methode, wert);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T als(Class<T> schnittstelle) {
        return (T) Proxy.newProxyInstance(
                schnittstelle.getClassLoader(),
                new Class<?>[]{schnittstelle},
                (proxy, methode, argumente) -> {
                    mitschrift.schreibe(name + "#" + methode.getName());

                    if (antworten.containsKey(methode.getName())) {
                        return antworten.get(methode.getName());
                    }
                    // void darf schweigen - dort gibt es nichts zu beantworten.
                    if (methode.getReturnType() == void.class) {
                        return null;
                    }
                    if (methode.getName().equals("toString")) {
                        return name;
                    }
                    throw new IllegalStateException(
                            "Die Attrappe " + name + " wurde nach " + methode.getName()
                                    + " gefragt und hat darauf keine Antwort. Trag sie mit"
                                    + " .antwortet(...) ein. Ein stilles null waere hier ein"
                                    + " gruener Fall ueber einen Zweig, den niemand gemeint hat.");
                });
    }
}
