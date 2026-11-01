package com.memoraai.controller;

import com.memoraai.dto.MemoryMediaResponse;
import com.memoraai.entity.MemoryMedia;
import com.memoraai.service.MediaService;
import com.memoraai.service.StorageService;
import io.minio.GetObjectResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/family/patients/{patientId}/memories/{memoryId}/media")
public class FamilyMemoryMediaController {
    private final MediaService mediaService;
    private final StorageService storageService;

    public FamilyMemoryMediaController(MediaService mediaService, StorageService storageService) {
        this.mediaService = mediaService;
        this.storageService = storageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<MemoryMediaResponse>> upload(
            @PathVariable UUID patientId,
            @PathVariable UUID memoryId,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "file", required = false) MultipartFile singleFile,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<MultipartFile> uploads = files != null && !files.isEmpty()
                ? files
                : (singleFile != null ? List.of(singleFile) : List.of());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                mediaService.uploadForPatient(patientId, memoryId, uploads, userDetails.getUsername()));
    }

    @GetMapping
    public ResponseEntity<List<MemoryMediaResponse>> list(
            @PathVariable UUID patientId,
            @PathVariable UUID memoryId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                mediaService.getForPatient(patientId, memoryId, userDetails.getUsername()));
    }

    @GetMapping("/{mediaId}")
    public ResponseEntity<Resource> download(
            @PathVariable UUID patientId,
            @PathVariable UUID memoryId,
            @PathVariable UUID mediaId,
            @AuthenticationPrincipal UserDetails userDetails) {
        MemoryMedia media = mediaService.getForDownloadForPatient(
                patientId, memoryId, mediaId, userDetails.getUsername());
        GetObjectResponse objectStream = storageService.getObject(media.getS3Key());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.getContentType()))
                .contentLength(media.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + media.getFileName() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new InputStreamResource(objectStream));
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID patientId,
            @PathVariable UUID memoryId,
            @PathVariable UUID mediaId,
            @AuthenticationPrincipal UserDetails userDetails) {
        mediaService.deleteForPatient(patientId, memoryId, mediaId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
