package com.vitassistant.controller;

import com.vitassistant.dto.ChatRequest;
import com.vitassistant.dto.ChatResponse;
import com.vitassistant.dto.IntentDataset;
import com.vitassistant.service.IntentService;
import com.vitassistant.service.RagService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final RagService ragService;
    private final IntentService intentService;

    public ChatController(RagService ragService, IntentService intentService) {
        this.ragService = ragService;
        this.intentService = intentService;
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Uploaded file must not be empty"));
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Uploaded file must have a filename"));
        }

        int chunkCount;
        try {
            chunkCount = ragService.ingest(file);
        } catch (IOException exception) {
            String error = exception.getMessage();
            if (error == null || error.isBlank()) {
                error = "PDF could not be read or contains no extractable text";
            }
            return ResponseEntity.badRequest()
                    .body(Map.of("error", error));
        }

        return ResponseEntity.ok(Map.of(
                "fileName", fileName,
                "chunksIndexed", chunkCount
        ));
    }

    @PostMapping("/intents")
    public ResponseEntity<Map<String, Object>> uploadIntents(@Valid @RequestBody IntentDataset dataset) {
        int questionsIndexed = intentService.ingest(dataset);
        return ResponseEntity.ok(Map.of(
                "intentsIndexed", dataset.intents().size(),
                "questionsIndexed", questionsIndexed
        ));
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(ragService.answer(request.question()));
    }
}
