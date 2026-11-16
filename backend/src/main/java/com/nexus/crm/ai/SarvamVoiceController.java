package com.nexus.crm.ai;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ai/voice")
@RequiredArgsConstructor
class SarvamVoiceController {
  private final SarvamVoiceService voice;
  record Speech(String text) { }

  @GetMapping("/status")
  Map<String, Boolean> status() { return Map.of("configured", voice.configured()); }

  @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  Map<String, String> transcribe(@RequestParam("file") MultipartFile file) {
    return Map.of("text", voice.transcribe(file));
  }

  @PostMapping(value = "/speak", produces = "audio/mpeg")
  ResponseEntity<byte[]> speak(@RequestBody Speech input) {
    return ResponseEntity.ok().contentType(MediaType.valueOf("audio/mpeg")).body(voice.speak(input.text()));
  }
}
