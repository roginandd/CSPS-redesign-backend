package org.csps.backend.service;

import java.util.List;
import java.util.Optional;

import org.csps.backend.domain.dtos.request.AdminPostRequestDTO;
import org.csps.backend.domain.dtos.request.AdminUnsecureRequestDTO;
import org.csps.backend.domain.dtos.response.AdminResponseDTO;
import org.csps.backend.domain.entities.Admin;
import org.csps.backend.domain.enums.AdminPosition;

public interface AdminService {
    Optional<Admin> findByAccountId(Long accountId);
    Optional<AdminResponseDTO> findById (Long Id);
    AdminResponseDTO createAdmin(AdminPostRequestDTO adminPostRequestDTO);
    AdminResponseDTO createAdminUnsecure(AdminUnsecureRequestDTO adminUnsecureRequestDTO);
    AdminResponseDTO deleteAdmin(Long adminId);
    AdminResponseDTO resetAdminPassword(Long adminId);
    
    // New methods
    AdminResponseDTO grantAdminAccess(String studentId, AdminPosition position);
    boolean isStudentAlreadyAdmin(String studentId);
    List<AdminPosition> getAvailablePositions();
    AdminResponseDTO revokeAdminAccess(Long adminId);
    List<AdminResponseDTO> getAllAdmins();
}
