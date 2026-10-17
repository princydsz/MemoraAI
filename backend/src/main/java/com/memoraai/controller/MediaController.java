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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Controller exposing authenticated REST endpoints for managing image attachments.
 * Never exposes MinIO credentials, storage paths, or direct public URLs.
 */
@RestController
@RequestMapping("/api/memories/{memoryId:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}/media")
public class MediaController {

    private final MediaService mediaService;
    private final StorageService storageService;

    public MediaController(MediaService mediaService, StorageService storageService) {
        this.mediaService = mediaService;
        this.storageService = storageService;
    }

    /**
     * Upload one or more image attachments to the specified memory.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<MemoryMediaResponse>> uploadMedia(
            @PathVariable UUID memoryId,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "file", required = false) MultipartFile singleFile,
            @AuthenticationPrincipal UserDetails userDetails) {

        List<MultipartFile> uploadList = new ArrayList<>();
        if (files != null && !files.isEmpty()) {
            uploadList.addAll(files);
        } else if (singleFile != null) {
            uploadList.add(singleFile);
        }

        List<MemoryMediaResponse> responses = mediaService.uploadMedia(memoryId, uploadList, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    /**
     * List all media attachments for the specified memory.
     */
    @GetMapping
    public ResponseEntity<List<MemoryMediaResponse>> getMedia(
            @PathVariable UUID memoryId,
            @AuthenticationPrincipal UserDetails userDetails) {

        List<MemoryMediaResponse> responses = mediaService.getMediaForMemory(memoryId, userDetails.getUsername());
        return ResponseEntity.ok(responses);
    }

    /**
     * Stream the verified image bytes through Spring with security headers and private cache control.
     * Does not hold database transactions open during byte transmission.
     */
    @GetMapping("/{mediaId:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}")
    public ResponseEntity<Resource> downloadMedia(
            @PathVariable UUID memoryId,
            @PathVariable UUID mediaId,
            @AuthenticationPrincipal UserDetails userDetails) {

        MemoryMedia media = mediaService.getMediaForDownload(memoryId, mediaId, userDetails.getUsername());
        GetObjectResponse objectStream = storageService.getObject(media.getS3Key());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.getContentType()))
                .contentLength(media.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + media.getFileName() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new InputStreamResource(objectStream));
    }

    /**
     * Delete an individual image attachment.
     */
    @DeleteMapping("/{mediaId:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}")
    public ResponseEntity<Void> deleteMedia(
            @PathVariable UUID memoryId,
            @PathVariable UUID mediaId,
            @AuthenticationPrincipal UserDetails userDetails) {

        mediaService.deleteMedia(memoryId, mediaId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
