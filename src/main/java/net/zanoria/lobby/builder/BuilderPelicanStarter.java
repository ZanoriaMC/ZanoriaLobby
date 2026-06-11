package net.zanoria.lobby.builder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Sends a "start" power action directly to the Wings daemon for the builder server.
 * Uses Wings API on port 8081 with the Wings token (avoids Pelican Panel auth issues).
 */
public final class BuilderPelicanStarter {

    private final String panelUrl;  // Wings API URL, e.g. "http://172.18.0.1:8081"
    private final String apiKey;    // Wings token from /etc/pelican/config.yml
    private final String serverUuid;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public BuilderPelicanStarter(String panelUrl, String apiKey, String serverUuid) {
        this.panelUrl   = panelUrl == null ? "" : panelUrl.replaceAll("/$", "");
        this.apiKey     = apiKey == null ? "" : apiKey;
        this.serverUuid = serverUuid == null ? "" : serverUuid;
    }

    /**
     * Returns true if the panel accepted the request (202/204).
     */
    public boolean startServer() {
        try {
            String body = "{\"action\":\"start\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(panelUrl + "/api/servers/" + serverUuid + "/power"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 204 || response.statusCode() == 202;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean isConfigured() {
        return !panelUrl.isBlank() && !apiKey.isBlank() && !serverUuid.isBlank();
    }
}
