package org.csps.backend.service;

import java.util.Optional;

import org.csps.backend.domain.dtos.request.StudentProfileCompletionRequestDTO;
import org.csps.backend.domain.dtos.request.StudentRequestDTO;
import org.csps.backend.domain.dtos.response.StudentResponseDTO;
import org.csps.backend.domain.entities.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface StudentService {
   StudentResponseDTO createStudent(StudentRequestDTO studentRequestDTO);
   public Page<StudentResponseDTO> getAllStudents(Pageable pageable);
   public Page<StudentResponseDTO> searchStudents(String search, Byte yearLevel, Pageable pageable);
   StudentResponseDTO getStudentProfile(String studentId);
   Optional<Student> findByAccountId(Long accountId);
   Optional<StudentResponseDTO> findById(String id);

   String getCurrentStudentId();
   
   /* complete incomplete student profile with full user information */
   StudentResponseDTO completeStudentProfile(String studentId, StudentProfileCompletionRequestDTO profileRequestDTO);
}
