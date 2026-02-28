package com.blocaqol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gestion de l'authentification - appels API au serveur d'auth.
 */
public class AuthManager {

	private static volatile String token;
	private static volatile String username;
	private static volatile boolean checked = false;
	private static volatile List<String> connectedPlayers = Collections.emptyList();
	private static volatile boolean allowAutofish = false;

	public static boolean isAuthenticated() {
		return token != null && !token.isEmpty();
	}

	public static String getUsername() {
		return username;
	}

	public static List<String> getConnectedPlayers() {
		return connectedPlayers;
	}

	public static boolean isAllowAutofish() {
		return allowAutofish;
	}

	public static void logout() {
		logout(null);
	}

	/** Déconnexion. Si apiUrl fourni, notifie l'API pour retirer de la liste des connectés. */
	public static void logout(String apiUrl) {
		String t = token;
		token = null;
		username = null;
		connectedPlayers = Collections.emptyList();
		allowAutofish = false;
		checked = true;
		if (t != null && !t.isEmpty() && apiUrl != null && !apiUrl.isEmpty()) {
			notifyLogoutAsync(apiUrl, t);
		}
	}

	private static void notifyLogoutAsync(String apiUrl, String tok) {
		new Thread(() -> {
			try {
				String url = apiUrl.replaceAll("/$", "") + "/auth/logout";
				HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
				HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.header("Authorization", "Bearer " + tok)
					.timeout(Duration.ofSeconds(3))
					.POST(HttpRequest.BodyPublishers.noBody())
					.build();
				client.send(request, HttpResponse.BodyHandlers.discarding());
			} catch (Exception e) {
				BlocaQoL.LOGGER.debug("Logout notify: {}", e.getMessage());
			}
		}).start();
	}

	public static void setAuthenticated(String t, String u, boolean canAutofish) {
		token = t;
		username = u;
		allowAutofish = canAutofish;
		checked = true;
	}

	public static void setAllowAutofish(boolean allow) {
		allowAutofish = allow;
	}

	public static void setConnectedPlayers(List<String> players) {
		connectedPlayers = players != null ? List.copyOf(players) : Collections.emptyList();
	}

	public static boolean hasChecked() {
		return checked;
	}

	/**
	 * Tente de se connecter. À appeler depuis un thread secondaire.
	 */
	public static AuthResult login(String apiUrl, String user, String password) {
		try {
			String url = apiUrl.replaceAll("/$", "") + "/auth/login";
			String body = String.format("{\"username\":\"%s\",\"password\":\"%s\"}",
				escapeJson(user), escapeJson(password));

			HttpClient client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.build();

			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Content-Type", "application/json")
				.timeout(Duration.ofSeconds(10))
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() == 200) {
				JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
				if (json.get("success").getAsBoolean()) {
					String t = json.get("token").getAsString();
					String u = json.has("username") ? json.get("username").getAsString() : user;
					List<String> players = parseConnectedPlayers(json);
					boolean allow = !json.has("allowAutofish") || json.get("allowAutofish").getAsBoolean();
					return new AuthResult(true, null, t, u, players, allow);
				}
			}

			if (response.statusCode() == 401) {
				return new AuthResult(false, "Mot de passe incorrect", null, null, null, false);
			}

			String error = "Erreur inconnue";
			try {
				JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
				error = json.has("error") ? json.get("error").getAsString() : error;
			} catch (Exception ignored) {}
			return new AuthResult(false, error, null, null, null, false);

		} catch (Exception e) {
			BlocaQoL.LOGGER.warn("Erreur login: {}", e.getMessage());
			return new AuthResult(false, "Erreur: " + e.getMessage(), null, null, null, false);
		}
	}

	private static List<String> parseConnectedPlayers(JsonObject json) {
		if (!json.has("connectedPlayers") || !json.get("connectedPlayers").isJsonArray()) return Collections.emptyList();
		List<String> list = new ArrayList<>();
		for (JsonElement el : json.getAsJsonArray("connectedPlayers")) {
			if (el.isJsonPrimitive()) list.add(el.getAsString());
		}
		return list;
	}

	/**
	 * Récupère la liste des joueurs connectés au mod. À appeler depuis un thread secondaire.
	 * L'API doit exposer GET /auth/connected avec header Authorization: Bearer &lt;token&gt;
	 */
	public static List<String> fetchConnectedPlayers(String apiUrl) {
		if (token == null || token.isEmpty()) return Collections.emptyList();
		try {
			String url = apiUrl.replaceAll("/$", "") + "/auth/connected";
			HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Authorization", "Bearer " + token)
				.timeout(Duration.ofSeconds(5))
				.GET()
				.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {
				JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
				if (json.has("allowAutofish")) {
					setAllowAutofish(json.get("allowAutofish").getAsBoolean());
				}
				JsonArray arr = json.has("connectedPlayers") ? json.getAsJsonArray("connectedPlayers")
					: json.has("players") ? json.getAsJsonArray("players") : null;
				if (arr != null) {
					List<String> list = new ArrayList<>();
					for (JsonElement el : arr) {
						if (el.isJsonPrimitive()) list.add(el.getAsString());
					}
					return list;
				}
			}
		} catch (Exception e) {
			BlocaQoL.LOGGER.debug("Erreur fetch connected: {}", e.getMessage());
		}
		return Collections.emptyList();
	}

	private static String escapeJson(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	public record AuthResult(boolean success, String error, String token, String username, List<String> connectedPlayers, boolean allowAutofish) {
		public AuthResult(boolean success, String error, String token, String username, List<String> connectedPlayers) {
			this(success, error, token, username, connectedPlayers, true);
		}
	}
}
