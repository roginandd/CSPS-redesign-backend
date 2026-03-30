package org.csps.backend.controller;


import java.util.List;

import org.csps.backend.annotation.Auditable;
import org.csps.backend.domain.dtos.request.AdminPostRequestDTO;
import org.csps.backend.domain.dtos.request.AdminUnsecureRequestDTO;
import org.csps.backend.domain.dtos.response.AdminResponseDTO;
import org.csps.backend.domain.dtos.response.GlobalResponseBuilder;
import org.csps.backend.domain.enums.AdminPosition;
import org.csps.backend.domain.enums.AuditAction;
import org.csps.backend.service.AdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/add")
    @Auditable(action = AuditAction.CREATE, resourceType = "Admin")
    public ResponseEntity<GlobalResponseBuilder<AdminResponseDTO>> addAdmin(@RequestBody AdminPostRequestDTO adminPostRequestDTO) {
    
        AdminResponseDTO adminResponseDTO = adminService.createAdmin(adminPostRequestDTO);

        String message = "Admin added successfully";
        
        return GlobalResponseBuilder.buildResponse(message, adminResponseDTO, HttpStatus.CREATED);
    }

    @PostMapping("/setup")
    @Auditable(action = AuditAction.CREATE, resourceType = "Admin")
    public ResponseEntity<GlobalResponseBuilder<AdminResponseDTO>> setupAdmin(@RequestBody AdminUnsecureRequestDTO adminUnsecureRequestDTO) {
    
        AdminResponseDTO adminResponseDTO = adminService.createAdminUnsecure(adminUnsecureRequestDTO);

        String message = "Admin setup successfully";

        return GlobalResponseBuilder.buildResponse(message, adminResponseDTO, HttpStatus.CREATED);
    }

    @DeleteMapping("/delete/{adminId}")
    @Auditable(action = AuditAction.DELETE, resourceType = "Admin")
    public ResponseEntity<GlobalResponseBuilder<AdminResponseDTO>> deleteAdmin(@PathVariable Long adminId) {
        AdminResponseDTO adminResponseDTO = adminService.deleteAdmin(adminId);
        String message = "Admin deleted successfully";
        return GlobalResponseBuilder.buildResponse(message, adminResponseDTO, HttpStatus.OK);
    }

    @PostMapping("/{adminId}/reset-password")
    @PreAuthorize("hasRole('ADMIN_EXECUTIVE')")
    @Auditable(action = AuditAction.UPDATE, resourceType = "Admin")
    public ResponseEntity<GlobalResponseBuilder<AdminResponseDTO>> resetAdminPassword(@PathVariable Long adminId) {
        AdminResponseDTO adminResponseDTO = adminService.resetAdminPassword(adminId);
        String message = "Admin password reset to default successfully";
        return GlobalResponseBuilder.buildResponse(message, adminResponseDTO, HttpStatus.OK);
    }

    @PostMapping("/grant-access")
    @Auditable(action = AuditAction.CREATE, resourceType = "Admin")
    public ResponseEntity<GlobalResponseBuilder<AdminResponseDTO>> grantAdminAccess(
            @RequestParam String studentId,
            @RequestParam AdminPosition position) {
        AdminResponseDTO adminResponseDTO = adminService.grantAdminAccess(studentId, position);
        String message = "Admin access granted successfully";
        return GlobalResponseBuilder.buildResponse(message, adminResponseDTO, HttpStatus.CREATED);
    }

    @GetMapping("/available-positions")
    public ResponseEntity<GlobalResponseBuilder<List<AdminPosition>>> getAvailablePositions() {
        List<AdminPosition> availablePositions = adminService.getAvailablePositions();
        String message = "Available positions retrieved successfully";
        return GlobalResponseBuilder.buildResponse(message, availablePositions, HttpStatus.OK);
    }

    @DeleteMapping("/revoke-access/{adminId}")
    @PreAuthorize("hasRole('ADMIN_EXECUTIVE')")
    @Auditable(action = AuditAction.DELETE, resourceType = "Admin")
    public ResponseEntity<GlobalResponseBuilder<AdminResponseDTO>> revokeAdminAccess(@PathVariable Long adminId) {
        AdminResponseDTO adminResponseDTO = adminService.revokeAdminAccess(adminId);
        String message = "Admin access revoked successfully";
        return GlobalResponseBuilder.buildResponse(message, adminResponseDTO, HttpStatus.OK);
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN_EXECUTIVE')")
    public ResponseEntity<GlobalResponseBuilder<List<AdminResponseDTO>>> getAllAdmins() {
        List<AdminResponseDTO> admins = adminService.getAllAdmins();
        String message = "All admins retrieved successfully";
        return GlobalResponseBuilder.buildResponse(message, admins, HttpStatus.OK);
    }

}
