package com.memoraai.repository;

import com.memoraai.entity.Memory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemoryRepository extends JpaRepository<Memory, UUID> {

    List<Memory> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Memory> findByIdAndUserId(UUID id, UUID userId);

    List<Memory> findAllByIdInAndUserId(List<UUID> ids, UUID userId);

    long deleteByIdAndUserId(UUID id, UUID userId);
}
