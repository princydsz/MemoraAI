package com.memoraai.repository;

import com.memoraai.entity.AppointmentStatus;
import com.memoraai.entity.PatientAppointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<PatientAppointment, UUID> {
    Optional<PatientAppointment> findByIdAndPatientUserId(UUID id, UUID patientUserId);

    List<PatientAppointment> findByStatus(AppointmentStatus status);

    @Query("""
            select a from PatientAppointment a
            where a.patientUserId = :patientUserId
              and (:fromDate is null or a.appointmentDate >= :fromDate)
              and (:toDate is null or a.appointmentDate <= :toDate)
              and (:status is null or a.status = :status)
              and (:upcoming = false or a.appointmentDate >= :today)
              and (:past = false or a.appointmentDate < :today)
            """)
    Page<PatientAppointment> findFiltered(
            @Param("patientUserId") UUID patientUserId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("status") AppointmentStatus status,
            @Param("upcoming") boolean upcoming,
            @Param("past") boolean past,
            @Param("today") LocalDate today,
            Pageable pageable);
}
