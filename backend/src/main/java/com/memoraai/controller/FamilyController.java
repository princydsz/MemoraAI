package com.memoraai.controller;

import com.memoraai.dto.CreatePatientRequest;
import com.memoraai.dto.PatientResponse;
import com.memoraai.dto.CreateInvitationRequest;
import com.memoraai.dto.InvitationResponse;
import com.memoraai.dto.MemberResponse;
import com.memoraai.dto.RedeemInvitationRequest;
import com.memoraai.dto.UpdateMemberRequest;
import com.memoraai.service.FamilyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/family")
public class FamilyController {

    private final FamilyService familyService;

    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    @PostMapping("/patients")
    public ResponseEntity<PatientResponse> createPatient(
            @Valid @RequestBody CreatePatientRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        PatientResponse response =
                familyService.createPatient(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/patients")
    public ResponseEntity<List<PatientResponse>> getPatients(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<PatientResponse> responses =
                familyService.getPatients(userDetails.getUsername());
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/patients/{patientId}/invitations")
    public ResponseEntity<InvitationResponse> createInvitation(
            @PathVariable UUID patientId,
            @Valid @RequestBody CreateInvitationRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        InvitationResponse response =
                familyService.createInvitation(patientId, request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/invitations/redeem")
    public ResponseEntity<MemberResponse> redeemInvitation(
            @Valid @RequestBody RedeemInvitationRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        MemberResponse response =
                familyService.redeemInvitation(request, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/patients/{patientId}/invitations/{invitationId}")
    public ResponseEntity<InvitationResponse> revokeInvitation(
            @PathVariable UUID patientId,
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal UserDetails userDetails) {
        InvitationResponse response =
                familyService.revokeInvitation(patientId, invitationId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/patients/{patientId}/members")
    public ResponseEntity<List<MemberResponse>> getMembers(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(familyService.getMembers(patientId, userDetails.getUsername()));
    }

    @GetMapping("/patients/{patientId}/invitations")
    public ResponseEntity<List<InvitationResponse>> getPendingInvitations(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                familyService.getPendingInvitations(patientId, userDetails.getUsername()));
    }

    @DeleteMapping("/patients/{patientId}/members/me")
    public ResponseEntity<Void> leaveCareCircle(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal UserDetails userDetails) {
        familyService.leaveCareCircle(patientId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/patients/{patientId}/members/{memberId}")
    public ResponseEntity<MemberResponse> updateMember(
            @PathVariable UUID patientId,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateMemberRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        MemberResponse response = familyService.updateMember(
                patientId, memberId, request, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/patients/{patientId}/members/{memberId}")
    public ResponseEntity<Void> revokeMember(
            @PathVariable UUID patientId,
            @PathVariable UUID memberId,
            @AuthenticationPrincipal UserDetails userDetails) {
        familyService.revokeMember(patientId, memberId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
