package org.csps.backend.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.csps.backend.domain.dtos.request.AdminPostRequestDTO;
import org.csps.backend.domain.dtos.request.AdminUnsecureRequestDTO;
import org.csps.backend.domain.dtos.request.UserRequestDTO;
import org.csps.backend.domain.dtos.response.AdminResponseDTO;
import org.csps.backend.domain.entities.Admin;
import org.csps.backend.domain.entities.Student;
import org.csps.backend.domain.entities.UserAccount;
import org.csps.backend.domain.entities.UserProfile;
import org.csps.backend.domain.enums.AdminPosition;
import org.csps.backend.domain.enums.UserRole;
import org.csps.backend.exception.AdminNotFoundException;
import org.csps.backend.exception.PositionAlreadyTakenException;
import org.csps.backend.exception.StudentNotFoundException;
import org.csps.backend.mapper.AdminMapper;
import org.csps.backend.mapper.UserMapper;
import org.csps.backend.repository.AdminRepository;
import org.csps.backend.repository.StudentRepository;
import org.csps.backend.repository.UserAccountRepository;
import org.csps.backend.repository.UserProfileRepository;
import org.csps.backend.service.AdminService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminRepository adminRepository;
    private final AdminMapper adminMapper;

    private final UserAccountRepository userAccountRepository;
    private final StudentRepository studentRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;


    private final UserMapper userMapper;

    @Value("${csps.adminUserFormat}")
    private String adminUserFormat;
    @Value("${csps.adminPasswordFormat}")
    private String adminPasswordFormat;

    @Override
    public Optional<Admin> findByAccountId(Long accountId) {
        return adminRepository.findByUserAccountUserAccountId(accountId);
    }

    @Override
    public Optional<AdminResponseDTO> findById(Long id) {
        return adminRepository.findById(id).map(adminMapper::toResponseDTO);
    }

    @Override
    public AdminResponseDTO createAdmin(AdminPostRequestDTO adminPostRequestDTO) {

        boolean isDeveloper = adminPostRequestDTO.getPosition() == AdminPosition.DEVELOPER;
        boolean isPositionTaken = adminRepository.existsByPosition(adminPostRequestDTO.getPosition());

        // load Student
        Student student = studentRepository.findById(adminPostRequestDTO.getStudentId())
                .orElseThrow(() -> new StudentNotFoundException("Student not found"));
        

        // Load Profile through Student
        UserProfile userProfile = student.getUserAccount().getUserProfile();

        if (!isDeveloper && isPositionTaken) {
            throw new PositionAlreadyTakenException("Position already taken: " + adminPostRequestDTO.getPosition());
        }

        UserAccount userAccount = UserAccount.builder()
                .userProfile(userProfile)
                .role(UserRole.ADMIN)
                .username(String.format("%s-%s%s%s%s",
                        adminUserFormat,
                        student.getStudentId()))
                .password(passwordEncoder.encode(String.format("%s%s%s",
                        adminPasswordFormat,
                        userProfile.getLastName(),
                        student.getStudentId())))
                .build();

        userAccount = userAccountRepository.save(userAccount);

        // Create Admin linked to the new account
        Admin admin = adminMapper.toEntity(adminPostRequestDTO);
        admin.setUserAccount(userAccount);

        admin = adminRepository.save(admin);

        return adminMapper.toResponseDTO(admin);
    }

    @Override
    public AdminResponseDTO createAdminUnsecure(AdminUnsecureRequestDTO dto) {
        // Validate inputs
        if (dto.getFirstName() == null || dto.getFirstName().isEmpty()) {
            throw new IllegalArgumentException("First name cannot be empty");
        }
        if (dto.getLastName() == null || dto.getLastName().isEmpty()) {
            throw new IllegalArgumentException("Last name cannot be empty");
        }
        if (dto.getEmail() == null || dto.getEmail().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        if (dto.getPosition() == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }

        // Check if email exists
        if (userProfileRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + dto.getEmail());
        }

        // Create UserRequestDTO for mapper
        UserRequestDTO userRequestDTO = new UserRequestDTO();
        userRequestDTO.setFirstName(dto.getFirstName());
        userRequestDTO.setLastName(dto.getLastName());
        userRequestDTO.setMiddleName(dto.getMiddleName());
        userRequestDTO.setBirthDate(dto.getBirthDate());
        userRequestDTO.setEmail(dto.getEmail());

        // Create and save UserProfile
        UserProfile userProfile = userMapper.toUserProfile(userRequestDTO);
        UserProfile savedProfile = userProfileRepository.save(userProfile);

        // Generate username and password
        String username = String.format("%s%s",
                adminUserFormat,
                dto.getLastName());
        String password = passwordEncoder.encode(String.format("%s%s",
                adminPasswordFormat,
                dto.getLastName()));
        
        // Create UserAccount
        UserAccount userAccount = UserAccount.builder()
                .userProfile(savedProfile)
                .role(UserRole.ADMIN)
                .username(username)
                .password(password)
                .build();
        userAccount = userAccountRepository.save(userAccount);

        // Create Admin
        AdminPostRequestDTO adminPostDTO = new AdminPostRequestDTO();
        adminPostDTO.setPosition(dto.getPosition());
        // No studentId needed since we're not linking to student

        Admin admin = adminMapper.toEntity(adminPostDTO);
        admin.setUserAccount(userAccount);
        admin = adminRepository.save(admin);

        return adminMapper.toResponseDTO(admin);
    }

    @Override
    public AdminResponseDTO deleteAdmin(Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminNotFoundException("Admin not found"));
        adminRepository.delete(admin);
        return adminMapper.toResponseDTO(admin);
    }

    @Override
    public AdminResponseDTO resetAdminPassword(Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminNotFoundException("Admin not found with ID: " + adminId));

        UserAccount adminUserAccount = admin.getUserAccount();
        if (adminUserAccount == null || adminUserAccount.getUserAccountId() == null) {
            throw new AdminNotFoundException("Admin account not found for admin ID: " + adminId);
        }

        String position = admin.getPosition().toString();

        String defaultPassword = String.format("%s%s", adminPasswordFormat, position);
        adminUserAccount.setPassword(passwordEncoder.encode(defaultPassword));
        userAccountRepository.save(adminUserAccount);

        return adminMapper.toResponseDTO(admin);
    }

    @Override
    public AdminResponseDTO grantAdminAccess(String studentId, AdminPosition position) {
        // Check if student exists
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));
        
        // Check if student is already an admin (has an admin account)
        if (isStudentAlreadyAdmin(studentId)) {
            throw new IllegalArgumentException("Student is already an admin");
        }
        
        // Check if position is available (except for DEVELOPER)
        boolean isDeveloper = position == AdminPosition.DEVELOPER;
        if (!isDeveloper && adminRepository.existsByPosition(position)) {
            throw new PositionAlreadyTakenException("Position already taken: " + position);
        }
        
        // Get the student's user profile (shared between student and admin accounts)
        UserProfile userProfile = student.getUserAccount().getUserProfile();
        
        // Create a new admin user account (separate from student account)
        UserAccount adminUserAccount = UserAccount.builder()
                .userProfile(userProfile)  // Same profile, different account
                .role(UserRole.ADMIN)
                .username(String.format("%s%s",
                        adminUserFormat,
                        student.getStudentId()))
                .password(passwordEncoder.encode(String.format("%s%s",
                        adminPasswordFormat,
                        student.getStudentId())))
                .build();

        adminUserAccount = userAccountRepository.save(adminUserAccount);

        // Create AdminPostRequestDTO for mapping
        AdminPostRequestDTO adminPostRequestDTO = new AdminPostRequestDTO();
        adminPostRequestDTO.setStudentId(studentId);
        adminPostRequestDTO.setPosition(position);
        
        // Create admin entity using mapper
        Admin admin = adminMapper.toEntity(adminPostRequestDTO);
        admin.setUserAccount(adminUserAccount);  // Link to the new admin account
        
        // Save the admin
        admin = adminRepository.save(admin);
        
        return adminMapper.toResponseDTO(admin);
    }

    @Override
    public boolean isStudentAlreadyAdmin(String studentId) {
        // Find the student
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));
        
        // Get the student's user profile
        UserProfile studentProfile = student.getUserAccount().getUserProfile();
        
        boolean isAdmin = adminRepository.existsByUserAccount_UserProfile_UserId(studentProfile.getUserId());

        return isAdmin;
    }

    @Override
    public List<AdminPosition> getAvailablePositions() {
        // Get all positions that are not taken (except DEVELOPER which can have multiple)
        List<AdminPosition> takenPositions = adminRepository.findAll().stream()
                .filter(admin -> admin.getPosition() != AdminPosition.DEVELOPER)
                .map(Admin::getPosition)
                .collect(Collectors.toList());
        
        return Arrays.stream(AdminPosition.values())
                .filter(position -> position == AdminPosition.DEVELOPER || !takenPositions.contains(position))
                .collect(Collectors.toList());
    }

    @Override
    public AdminResponseDTO revokeAdminAccess(Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminNotFoundException("Admin not found with ID: " + adminId));
        
        // Get the admin user account (separate from student account)
        UserAccount adminUserAccount = admin.getUserAccount();
        
        // Delete the admin record first
        adminRepository.delete(admin);
        
        // Delete the admin user account (student account remains untouched)
        userAccountRepository.delete(adminUserAccount);
        
        return adminMapper.toResponseDTO(admin);
    }

    @Override
    public List<AdminResponseDTO> getAllAdmins() {
        return adminRepository.findAll().stream()
                .map(adminMapper::toResponseDTO)
                .collect(Collectors.toList());
    }
}
