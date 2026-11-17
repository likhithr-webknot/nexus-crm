package com.nexus.crm.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

@Service
@RequiredArgsConstructor
public class SarvamVoiceService {

	private final JsonMapper json;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	private final AiKeyService keys;

	public boolean configured() {
		return !key().isBlank();
	}

	/** The key an admin saved in settings, otherwise the server's SARVAM_API_KEY. */
	private String key() {
		return keys.active(AiKeyService.Provider.SARVAM).key();
	}

	public String transcribe(MultipartFile file) {
		requireKey();
		if (file == null || file.isEmpty() || file.getSize() > 4_000_000) {
			throw invalid("Record up to 30 seconds of audio and try again.");
		}
		String contentType = file.getContentType();
		if (contentType == null
				|| !List.of("audio/webm", "audio/mp4", "audio/wav", "audio/mpeg", "audio/ogg", "audio/x-m4a")
					.contains(contentType.split(";")[0]))
			throw invalid("This audio format is not supported.");
		try {
			String boundary = "nexus_" + UUID.randomUUID().toString().replace("-", "");
			byte[] before = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\nsaaras:v4\r\n"
					+ "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"recording\"\r\n"
					+ "Content-Type: " + contentType + "\r\n\r\n")
				.getBytes(StandardCharsets.UTF_8);
			byte[] after = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.sarvam.ai/speech-to-text"))
				.timeout(Duration.ofSeconds(45))
				.header("api-subscription-key", key())
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(before, file.getBytes(), after)))
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw providerError(response.statusCode());
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> data = json.readValue(response.body(), Map.class);
			String transcript = data.get("transcript") instanceof String value ? value.strip() : "";
			if (transcript.isEmpty()) {
				throw invalid("No speech was detected. Try recording again.");
			}
			return transcript;
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw invalid("Transcription was interrupted.");
		}
		catch (java.io.IOException error) {
			throw invalid("Voice transcription is unavailable. Try again.");
		}
	}

	public byte[] speak(String text) {
		requireKey();
		if (text == null || text.isBlank() || text.length() > 2500) {
			throw invalid("Speech text must be between 1 and 2500 characters.");
		}
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.sarvam.ai/text-to-speech"))
				.timeout(Duration.ofSeconds(45))
				.header("api-subscription-key", key())
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("text", text, "language_code",
						"en-IN", "model", "bulbul:v4-flash", "output_audio_codec", "mp3"))))
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw providerError(response.statusCode());
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> data = json.readValue(response.body(), Map.class);
			if (!(data.get("audios") instanceof List<?> audio) || audio.isEmpty()
					|| !(audio.getFirst() instanceof String encoded)) {
				throw invalid("Voice response was empty. Try again.");
			}
			return Base64.getDecoder().decode(encoded);
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw invalid("Speech was interrupted.");
		}
		catch (java.io.IOException error) {
			throw invalid("Voice response is unavailable. Try again.");
		}
	}

	private void requireKey() {
		if (!configured()) {
			throw invalid("Sarvam AI API key has not been set.");
		}
	}

	private ApiException providerError(int status) {
		return invalid(status == 401 || status == 403 ? "Sarvam AI rejected its API key. Ask an admin to update it."
				: status == 429 ? "Sarvam AI voice limit was reached. Try later."
						: "Sarvam AI voice is unavailable. Try again.");
	}

	private ApiException invalid(String message) {
		return new ApiException(ErrorCode.INVALID_STATE, message);
	}

}
