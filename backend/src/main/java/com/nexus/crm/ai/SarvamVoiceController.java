package com.nexus.crm.ai;

import java.time.Duration;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.limits.RateLimit;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ai/voice")
@RequiredArgsConstructor
class SarvamVoiceController {
  private final SarvamVoiceService voice;
  record Speech(String text) { }

  /** Each question by voice uses one transcription and one spoken answer, and both cost money. */
  private static final RateLimit VOICE_LIMIT = new RateLimit(60, Duration.ofHours(1));

  @GetMapping("/status")
  Map<String, Boolean> status() { return Map.of("configured", voice.configured()); }

  @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  Map<String, String> transcribe(@AuthenticationPrincipal CurrentUser user, @RequestParam("file") MultipartFile file) {
    VOICE_LIMIT.require(user.id());
    return Map.of("text", voice.transcribe(file));
  }

  @PostMapping(value = "/speak", produces = "audio/mpeg")
  ResponseEntity<byte[]> speak(@AuthenticationPrincipal CurrentUser user, @RequestBody Speech input) {
    VOICE_LIMIT.require(user.id());
    return ResponseEntity.ok().contentType(MediaType.valueOf("audio/mpeg")).body(voice.speak(input.text()));
  }
}
