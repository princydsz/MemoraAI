package com.memoraai.repository;

import com.memoraai.entity.MemoryMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemoryMediaRepository extends JpaRepository<MemoryMedia, UUID> {

    long countByMemoryId(UUID memoryId);

    List<MemoryMedia> findAllByMemoryIdAndUserIdOrderByCreatedAtAsc(UUID memoryId, UUID userId);

    Optional<MemoryMedia> findByIdAndUserId(UUID id, UUID userId);

    List<MemoryMedia> findAllByMemoryId(UUID memoryId);

    Optional<MemoryMedia> findByIdAndMemoryIdAndUserId(UUID id, UUID memoryId, UUID userId);
}
