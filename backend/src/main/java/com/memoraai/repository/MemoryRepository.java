package com.memoraai.repository;

import com.memoraai.entity.Memory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemoryRepository extends JpaRepository<Memory, UUID> {

    List<Memory> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<Memory> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<Memory> findByIdAndUserId(UUID id, UUID userId);

    List<Memory> findAllByIdInAndUserId(List<UUID> ids, UUID userId);

    long deleteByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
