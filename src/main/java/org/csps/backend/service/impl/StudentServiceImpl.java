package org.csps.backend.service.impl;

import java.util.Optional;

import org.csps.backend.domain.dtos.request.StudentProfileCompletionRequestDTO;
import org.csps.backend.domain.dtos.request.StudentRequestDTO;
import org.csps.backend.domain.dtos.response.StudentResponseDTO;
import org.csps.backend.domain.entities.Admin;
import org.csps.backend.domain.entities.Student;
import org.csps.backend.domain.entities.UserAccount;
import org.csps.backend.domain.entities.UserProfile;
import org.csps.backend.exception.InvalidStudentId;
import org.csps.backend.exception.MissingFieldException;
import org.csps.backend.exception.StudentNotFoundException;
import org.csps.backend.exception.UserAlreadyExistsException;
import org.csps.backend.mapper.StudentMapper;
import org.csps.backend.repository.AdminRepository;
import org.csps.backend.repository.StudentRepository;
import org.csps.backend.repository.UserProfileRepository;
import org.csps.backend.service.CartService;
import org.csps.backend.service.StudentService;
import org.csps.backend.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

   private final StudentMapper studentMapper;
   private final StudentRepository studentRepository;
   private final AdminRepository adminRepository;
   private final UserService userService;
   private final CartService cartService;
   private final UserProfileRepository userProfileRepository;
    
    @Override
    @Transactional
    public StudentResponseDTO createStudent(@Valid StudentRequestDTO studentRequestDTO) {

        // Check if the student already exists
        String studentId = studentRequestDTO.getStudentId().trim();

        if (studentId.isEmpty()) {
            throw new MissingFieldException("Username cannot be empty!");
        }

        if (studentId.length() != 8) {
            throw new InvalidStudentId("Invalid Student Id");
        }
        
        
        Optional<Student> existingStudent = studentRepository.findByStudentId(studentId);

        // If exists
        if (existingStudent.isPresent()) {
            throw new UserAlreadyExistsException(String.format("User %s already existed", studentId));
        } 

        // If npt
        // Create UserAccount from nested UserRequestDTO
        UserAccount savedUserAccount = userService.createUser(studentRequestDTO, studentRequestDTO.getUserRequestDTO());
    
        // Map Student entity
        Student student = studentMapper.toEntity(studentRequestDTO);
        student.setUserAccount(savedUserAccount);
        student.setStudentId(studentId);
            
    
        // Persist Student
        student = studentRepository.save(student);
        
        // Create Cart for the student
        cartService.createCart(studentId);
    
        // Map to DTO
        return studentMapper.toResponseDTO(student);

    }


    // Get all Students
   @Override
   public Page<StudentResponseDTO> getAllStudents(Pageable pageable) {
       return studentRepository.findAll(pageable)
               .map(studentMapper::toResponseDTO);
   }

   // Search Students by ID or name with optional year level filter
   @Override
   public Page<StudentResponseDTO> searchStudents(String search, Byte yearLevel, Pageable pageable) {
       return studentRepository.searchStudents(search, yearLevel, pageable)
               .map(studentMapper::toResponseDTO);
   }


   // Get Student By Id
   @Override
   public StudentResponseDTO getStudentProfile(String studentId) {
       Student existingStudent = studentRepository.findById(studentId)
               .orElseThrow(() -> new StudentNotFoundException(studentId));
       StudentResponseDTO dto = studentMapper.toResponseDTO(existingStudent);
       enrichStudentWithAdminInfo(dto, existingStudent);
       return dto;
   }

   @Override
   public Optional<Student> findByAccountId(Long accountId) {
    return studentRepository.findByUserAccountUserAccountId(accountId);
   }

   @Override
   public Optional<StudentResponseDTO> findById(String id) {
        return studentRepository.findByStudentId(id)
                .map(student -> {
                    StudentResponseDTO dto = studentMapper.toResponseDTO(student);
                    enrichStudentWithAdminInfo(dto, student);
                    return dto;
                });
   }

   /*
    * Get current authenticated student ID from SecurityContext
    */
   
   @Override
   public String getCurrentStudentId()
   {
        String principal = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
    

        return principal;
   }


   
   @Override
   @Transactional
   public StudentResponseDTO completeStudentProfile(String studentId, StudentProfileCompletionRequestDTO profileRequestDTO) {
       /* find student and validate existence */
       Student student = studentRepository.findById(studentId)
               .orElseThrow(() -> new StudentNotFoundException("Student not found: " + studentId));
        
       /* get the user profile */
       UserProfile profile = student.getUserAccount().getUserProfile();
       
       /* update profile with complete information */
    profile.setMiddleName(profileRequestDTO.getMiddleName());
    profile.setBirthDate(profileRequestDTO.getBirthDate());
    profile.setEmail(profileRequestDTO.getEmail());
       profile.setIsProfileComplete(true);  // mark as complete
       
       userProfileRepository.save(profile);
       
       return studentMapper.toResponseDTO(student);
   }
   

   /* overloaded method for single student queries (uses direct database lookup) */
   private void enrichStudentWithAdminInfo(StudentResponseDTO studentDTO, Student student) {
       if (student.getUserAccount() != null && 
           student.getUserAccount().getUserProfile() != null) {
           
           Long userProfileId = student.getUserAccount().getUserProfile().getUserId();
           
           // single query for individual student lookup
           Optional<Admin> adminOpt = adminRepository.findByUserAccount_UserProfile_UserId(userProfileId);
           
           if (adminOpt.isPresent()) {
               Admin admin = adminOpt.get();
               studentDTO.setAdminPosition(admin.getPosition());
           }
       }
   }
}
