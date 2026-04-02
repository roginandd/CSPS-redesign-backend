package org.csps.backend.service.impl;

import java.util.List;

import org.csps.backend.domain.dtos.request.ChangePasswordRequestDTO;
import org.csps.backend.domain.dtos.request.StudentRequestDTO;
import org.csps.backend.domain.dtos.request.UserRequestDTO;
import org.csps.backend.domain.entities.Student;
import org.csps.backend.domain.dtos.response.UserResponseDTO;
import org.csps.backend.domain.entities.UserAccount;
import org.csps.backend.domain.entities.UserProfile;
import org.csps.backend.domain.enums.UserRole;
import org.csps.backend.exception.EmailAlreadyExistsException;
import org.csps.backend.exception.MissingFieldException;
import org.csps.backend.exception.StudentNotFoundException;
import org.csps.backend.exception.UserAlreadyExistsException;
import org.csps.backend.exception.UserNotFoundException;
import org.csps.backend.mapper.UserMapper;
import org.csps.backend.repository.StudentRepository;
import org.csps.backend.repository.UserAccountRepository;
import org.csps.backend.repository.UserProfileRepository;
import org.csps.backend.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final UserAccountRepository userAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final StudentRepository studentRepository;
    
    private final PasswordEncoder passwordEncoder;
    
    @Value("${csps.userNameformat}")
    private String userNameFormat;
    @Value("${csps.passwordformat}")
    private String passwordFormat;

    
    // Create User (for students atm, will do for Admin soon)
    @Override
    public UserAccount createUser(StudentRequestDTO student, UserRequestDTO userRequestDTO) {
        // Validate inputs
        if (userRequestDTO == null) {
            throw new MissingFieldException("User Request DTO cannot be null!");
        }
        if (student == null) {
            throw new MissingFieldException("Student Request DTO cannot be null!");
        }
        
        // check if username already exists
        String generatedUsername = userNameFormat + userRequestDTO.getFirstName();
        String firstName = userRequestDTO.getFirstName();
        String lastName = userRequestDTO.getLastName();
        String studentId = student.getStudentId();
        String email = userRequestDTO.getEmail();

        boolean validEmail = false;
        // Validate first name
        if (firstName == null || firstName.isEmpty()) {
            throw new MissingFieldException("First Name cannot be empty!");
        }

        // Validate last name
        if (lastName == null || lastName.isEmpty()) {
            throw new MissingFieldException("Last Name cannot be empty!");
        }

        // Validate email
        if (email == null || email.isEmpty()) {
            throw new MissingFieldException("Email cannot be empty!");
        } else {
            // simple regex check
            String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
            validEmail = email.matches(emailRegex);
        }
        
        if (!validEmail) {
            throw new IllegalArgumentException("Invalid email format: " + email);
        }
        
        // Validate studentId
        if (studentId == null || studentId.isEmpty()) {
            throw new MissingFieldException("Student ID cannot be empty!");
        }

        if (userAccountRepository.existsByUsername(generatedUsername)) {
            throw new UserAlreadyExistsException("Username already exists: " + generatedUsername);
        }
        if (userProfileRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }


        // Create and save UserProfile
        UserProfile userProfile = userMapper.toUserProfile(userRequestDTO);
        userProfile.setIsProfileComplete(true);
        UserProfile savedProfile = userProfileRepository.save(userProfile);

        // Create UserAccount and link it to the saved profile
        UserAccount userAccount = userMapper.toUserAccount(userRequestDTO);

        userAccount.setUserProfile(savedProfile);

        String tempPassword = String.format("%s%s", passwordFormat, studentId);
        userAccount.setPassword(passwordEncoder.encode(tempPassword)); // Hash the password
        userAccount.setUsername(String.format("%s%s", userNameFormat, studentId));
        userAccount.setRole(UserRole.STUDENT);

        

        return userAccountRepository.save(userAccount);
    }


    // Get User By Id 
    @Override
    public UserResponseDTO getUserById(Long userId) {
        UserAccount existingUser = userAccountRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return userMapper.toResponseDTO(existingUser);
    }

    // Get All Users
    @Override
    public List<UserResponseDTO> getAllUsers() {
        return userAccountRepository.findAll().stream()
                .map(userMapper::toResponseDTO)
                .toList();
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequestDTO requestDTO) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!passwordEncoder.matches(requestDTO.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Old password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(requestDTO.getNewPassword()));
        userAccountRepository.save(user);
    }

    @Override
    public void restoreStudentPassword(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            throw new MissingFieldException("Student ID cannot be empty!");
        }

        Student student = studentRepository.findByStudentId(studentId.trim())
                .orElseThrow(() -> new StudentNotFoundException("Student not found: " + studentId));

        UserAccount userAccount = student.getUserAccount();
        if (userAccount == null || userAccount.getUserAccountId() == null) {
            throw new UserNotFoundException("User account not found for student: " + studentId);
        }

        String defaultPassword = passwordFormat + student.getStudentId();
        userAccount.setPassword(passwordEncoder.encode(defaultPassword));
        userAccountRepository.save(userAccount);
    }

}
