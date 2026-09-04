package net.zanoria.lobby.sonde;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Liest am LAUFENDEN Server, was ZanoriaLobby wirklich angemeldet hat.
 *
 * <p>⚠️ Sie laeuft absichtlich VERZOEGERT (nicht in onEnable): die Reihenfolge, in der Bukkit
 * Plugins aktiviert, ist nicht garantiert, und eine Sonde, die vor ZanoriaLobby liest, meldet
 * "nichts angemeldet" - ein Befund, der nur ueber die Reihenfolge etwas sagt.
 *
 * <p>⚠️ Sie schreibt Zeilen mit festen Praefixen; ausgewertet wird das PROTOKOLL, nicht ein
 * Rueckgabewert. Damit sieht der Auswerter dasselbe wie ein Mensch, der zusieht.
 */
public final class Sonde extends JavaPlugin {

    @Override
    public void onEnable() {
        // 3 Sekunden nach dem Start - alle Plugins sind dann sicher aktiviert.
        Bukkit.getScheduler().runTaskLater(this, this::messen, 60L);
    }

    private void messen() {
        try {
            hoerer();
            ereignisbindung();
            befehle();
        } catch (Throwable t) {
            System.out.println("SONDE-FEHLER: " + t);
            t.printStackTrace();
        } finally {
            System.out.println("SONDE-FERTIG");
        }
    }

    /** Welche Hoererklassen von ZanoriaLobby wirklich in einer Handlerliste stehen. */
    private void hoerer() {
        Map<String, Integer> gezaehlt = new TreeMap<>();
        for (RegisteredListener rl : HandlerList.getRegisteredListeners(
                Bukkit.getPluginManager().getPlugin("ZanoriaLobby"))) {
            String klasse = rl.getListener().getClass().getName();
            gezaehlt.merge(klasse, 1, Integer::sum);
        }
        gezaehlt.forEach((k, v) -> System.out.println("SONDE-HOERER: " + k + " x " + v));
        System.out.println("SONDE-HOERER-SUMME: " + gezaehlt.size() + " Klasse(n)");
    }

    /**
     * Je deklariertem {@code @EventHandler}: hat der laufende Server ihn an die Handlerliste
     * SEINES Ereignisses gebunden?
     *
     * <p>⚠️ Der Sollwert wird aus derselben Klasse abgeleitet, aus der auch der Istwert kommt -
     * eine gepflegte Paarliste waere beim naechsten Griff selbst die Luege.
     */
    private void ereignisbindung() {
        int deklariert = 0;
        int gebunden = 0;
        Set<String> klassen = new LinkedHashSet<>();
        for (RegisteredListener rl : HandlerList.getRegisteredListeners(
                Bukkit.getPluginManager().getPlugin("ZanoriaLobby"))) {
            klassen.add(rl.getListener().getClass().getName());
        }
        for (String name : klassen) {
            Class<?> k;
            try {
                k = Class.forName(name, false,
                        Bukkit.getPluginManager().getPlugin("ZanoriaLobby").getClass().getClassLoader());
            } catch (ClassNotFoundException e) {
                System.out.println("SONDE-EREIGNIS: KLASSE NICHT LADBAR " + name);
                continue;
            }
            for (Method m : k.getDeclaredMethods()) {
                if (m.getAnnotation(org.bukkit.event.EventHandler.class) == null) continue;
                deklariert++;
                if (m.getParameterCount() != 1
                        || !Event.class.isAssignableFrom(m.getParameterTypes()[0])) {
                    System.out.println("SONDE-EREIGNIS: UNGEBUNDEN (falsche Signatur) "
                            + name + "#" + m.getName());
                    continue;
                }
                @SuppressWarnings("unchecked")
                Class<? extends Event> typ = (Class<? extends Event>) m.getParameterTypes()[0];
                boolean gefunden = false;
                try {
                    Method hl = typ.getMethod("getHandlerList");
                    HandlerList liste = (HandlerList) hl.invoke(null);
                    for (RegisteredListener rl : liste.getRegisteredListeners()) {
                        if (rl.getListener().getClass().getName().equals(name)) { gefunden = true; break; }
                    }
                } catch (Exception e) {
                    System.out.println("SONDE-EREIGNIS: KEINE HANDLERLISTE " + typ.getName());
                }
                if (gefunden) {
                    gebunden++;
                    System.out.println("SONDE-EREIGNIS: " + name + "#" + m.getName()
                            + " <- " + typ.getName());
                } else {
                    System.out.println("SONDE-EREIGNIS: UNGEBUNDEN " + name + "#" + m.getName()
                            + " <- " + typ.getName());
                }
            }
        }
        System.out.println("SONDE-EREIGNISBINDUNG: " + deklariert + " deklariert, "
                + gebunden + " gebunden, " + (deklariert - gebunden) + " ungebunden");
    }

    /** Ist /builder angemeldet, und traegt es das Recht? */
    private void befehle() {
        Map<String, String> ergebnis = new LinkedHashMap<>();
        for (String name : new String[]{"builder", "lobbynpc"}) {
            PluginCommand c = Bukkit.getPluginCommand(name);
            ergebnis.put(name, c == null
                    ? "NICHT ANGEMELDET"
                    : "permission=" + c.getPermission()
                      + " executor=" + (c.getExecutor() == null ? "null"
                            : c.getExecutor().getClass().getName()));
        }
        ergebnis.forEach((k, v) -> System.out.println("SONDE-BEFEHL: " + k + " -> " + v));
    }
}
