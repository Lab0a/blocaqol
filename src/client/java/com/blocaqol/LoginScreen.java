package com.blocaqol;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginScreen extends Screen {

	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
	private static final int MAX_ATTEMPTS_PER_HOUR = 5;
	private static final ConcurrentLinkedQueue<Long> attemptTimestamps = new ConcurrentLinkedQueue<>();

	private TextFieldWidget usernameField;
	private TextFieldWidget passwordField;
	private ButtonWidget loginButton;
	private Text errorMessage = Text.empty();
	private boolean loading = false;

	private final BlocaQoLConfig config;

	public LoginScreen(BlocaQoLConfig config) {
		super(Text.literal("BlocaQoL - Connexion"));
		this.config = config;
	}

	@Override
	protected void init() {
		int centerX = width / 2;
		int fieldWidth = 200;
		int fieldHeight = 20;
		int y = height / 2 - 60;

		usernameField = new TextFieldWidget(textRenderer, centerX - fieldWidth / 2, y, fieldWidth, fieldHeight, Text.literal("Username"));
		usernameField.setPlaceholder(Text.literal("Nom d'utilisateur"));
		usernameField.setMaxLength(64);
		addDrawableChild(usernameField);
		setInitialFocus(usernameField);

		y += 30;
		passwordField = new TextFieldWidget(textRenderer, centerX - fieldWidth / 2, y, fieldWidth, fieldHeight, Text.literal("Password"));
		passwordField.setPlaceholder(Text.literal("Mot de passe"));
		passwordField.setMaxLength(128);
		passwordField.setRenderTextProvider((text, cursor) -> text.isEmpty() ? OrderedText.EMPTY : Text.literal("*".repeat(text.length())).asOrderedText());
		addDrawableChild(passwordField);

		y += 40;
		loginButton = ButtonWidget.builder(Text.literal("Se connecter"), this::onLogin)
			.dimensions(centerX - 100, y, 200, 20)
			.build();
		addDrawableChild(loginButton);

		y += 30;
		addDrawableChild(ButtonWidget.builder(Text.literal("Jouer sans le mod (déconnexion)"), btn -> {
			AuthManager.logout();
			AutoFish.setEnabled(false);
			PlayerSkinCache.clear();
			if (client != null) client.setScreen(null);
		}).dimensions(centerX - 100, y, 200, 20).build());
	}

	private void onLogin(ButtonWidget btn) {
		String user = usernameField.getText().trim();
		String pass = passwordField.getText();

		if (user.isEmpty() || pass.isEmpty()) {
			errorMessage = Text.literal("§cRemplissez tous les champs");
			return;
		}

		long now = System.currentTimeMillis();
		long oneHourAgo = now - 3600_000;
		attemptTimestamps.removeIf(ts -> ts < oneHourAgo);
		if (attemptTimestamps.size() >= MAX_ATTEMPTS_PER_HOUR) {
			errorMessage = Text.literal("§cTrop de tentatives. Réessayez dans 1 heure.");
			return;
		}
		attemptTimestamps.add(now);

		loading = true;
		loginButton.active = false;
		errorMessage = Text.literal("§7Connexion en cours...");

		EXECUTOR.execute(() -> {
			AuthManager.AuthResult result = AuthManager.login(config.authApiUrl, user, pass);

			Util.getMainWorkerExecutor().execute(() -> {
				loading = false;
				loginButton.active = true;

				if (result.success()) {
					attemptTimestamps.remove(now);
					AuthManager.setAuthenticated(result.token(), result.username(), result.allowAutofish());
					if (result.connectedPlayers() != null) AuthManager.setConnectedPlayers(result.connectedPlayers());
					BlocaQoLClient.registerFishingKeysIfAllowed();
					errorMessage = Text.literal("§aConnecté !");
					if (client != null) {
						client.inGameHud.setOverlayMessage(Text.literal("✓ Connecté").formatted(Formatting.GREEN), false);
						client.setScreen(null);
					}
				} else {
					String err = result.error() != null ? result.error() : "Erreur";
					if (err.toLowerCase().contains("password") || err.toLowerCase().contains("mot de passe")
						|| err.toLowerCase().contains("incorrect") || err.toLowerCase().contains("invalid")
						|| err.toLowerCase().contains("credentials") || err.toLowerCase().contains("401")) {
						err = "Mot de passe incorrect";
					}
					errorMessage = Text.literal("§c" + err);
				}
			});
		});
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Évite "Can only blur once per frame" (Lunar/overlay) : fond sombre au lieu de blur
		context.fill(0, 0, width, height, 0xC0101010);
		super.render(context, mouseX, mouseY, delta);

		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 90, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, errorMessage, width / 2, height / 2 - 5, 0xFFFFFF);
	}

	@Override
	public void tick() {
		super.tick();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
