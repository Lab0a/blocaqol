package com.blocaqol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Gestion de l'authentification - appels API au serveur d'auth.
 */
public class AuthManager {

	private static volatile String token;
	private static volatile String username;
	private static volatile boolean checked = false;

	public static boolean isAuthenticated() {
		return token != null && !token.isEmpty();
	}

	public static String getUsername() {
		return username;
	}

	public static void logout() {
		token = null;
		username = null;
		checked = true;
	}

	public static void setAuthenticated(String t, String u) {
		token = t;
		username = u;
		checked = true;
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
					return new AuthResult(true, null, t, u);
				}
			}

			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			String error = json.has("error") ? json.get("error").getAsString() : "Erreur inconnue";
			return new AuthResult(false, error, null, null);

		} catch (Exception e) {
			BlocaQoL.LOGGER.warn("Erreur login: {}", e.getMessage());
			return new AuthResult(false, "Erreur: " + e.getMessage(), null, null);
		}
	}

	private static String escapeJson(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	public record AuthResult(boolean success, String error, String token, String username) {}
}
