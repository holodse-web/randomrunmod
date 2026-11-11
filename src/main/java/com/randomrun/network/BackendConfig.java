package com.randomrun.network;

import com.google.gson.JsonParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public final class BackendConfig {
    private static final URI BASE = load();
    private BackendConfig() {}
    private static URI load() {
        String address = System.getProperty("randomrun.api", "http://127.0.0.1:8787");
        try (var input = BackendConfig.class.getResourceAsStream("/randomrun-online.json")) {
            if (System.getProperty("randomrun.api") == null && input != null) address = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().get("apiUrl").getAsString();
        } catch (Exception error) { throw new IllegalStateException("Invalid RandomRun server configuration", error); }
        URI value = URI.create(address);
        boolean local = "127.0.0.1".equals(value.getHost()) || "localhost".equals(value.getHost()) || "[::1]".equals(value.getHost());
        if (!("https".equals(value.getScheme()) || local && "http".equals(value.getScheme())) || value.getHost() == null || value.getUserInfo() != null || value.getQuery() != null || value.getFragment() != null || !(value.getPath().isEmpty() || "/".equals(value.getPath()))) throw new IllegalArgumentException("RandomRun requires an HTTPS API address");
        return URI.create(address.replaceAll("/+$", "") + "/v1/");
    }
    public static URI endpoint(String path) {
        if (path == null || !path.startsWith("/") || path.startsWith("//") || path.contains(":") || path.contains("..") || path.contains("?") || path.contains("#") || path.contains("%") || path.contains("\\")) throw new IllegalArgumentException("Invalid API path");
        return BASE.resolve(path.substring(1));
    }
    public static URI events() { return URI.create(endpoint("/events").toString().replaceFirst("^http", "ws")); }
}
