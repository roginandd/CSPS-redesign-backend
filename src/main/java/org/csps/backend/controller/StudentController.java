package org.csps.backend.controller;

import org.csps.backend.domain.dtos.request.StudentRequestDTO;
import org.csps.backend.domain.dtos.request.UserRequestDTO;
import org.csps.backend.domain.dtos.response.GlobalResponseBuilder;
import org.csps.backend.domain.dtos.response.StudentResponseDTO;
import org.csps.backend.service.StudentService;
import org.csps.backend.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

   private final StudentService studentService;
   private final UserService userService;

   @PostMapping()
   @PreAuthorize("hasRole('ADMIN_EXECUTIVE')")
   public ResponseEntity<StudentResponseDTO> createStudent(@RequestBody StudentRequestDTO studentRequestDTO) {
       // create student
       StudentResponseDTO createdStudent = studentService.createStudent(studentRequestDTO);
       return ResponseEntity.status(HttpStatus.CREATED).body(createdStudent);
   }

   @GetMapping()
   @PreAuthorize("hasAnyRole('ADMIN_EXECUTIVE')")
   public ResponseEntity<Page<StudentResponseDTO>> getAllStudents(
           @RequestParam(defaultValue = "0") int page,
           @RequestParam(defaultValue = "7") int size,
           @RequestParam(required = false) String search,
           @RequestParam(required = false) Byte yearLevel) {
       Pageable pageable = PageRequest.of(page, size);
       Page<StudentResponseDTO> students;
       if (search != null && !search.isBlank()) {
           students = studentService.searchStudents(search.trim(), yearLevel, pageable);
       } else if (yearLevel != null) {
           students = studentService.searchStudents("", yearLevel, pageable);
       } else {
           students = studentService.getAllStudents(pageable);
       }
       return ResponseEntity.ok(students);
   }

   @GetMapping("/{studentId}")
   @PreAuthorize("hasAnyRole('ADMIN_EXECUTIVE')")
   public ResponseEntity<StudentResponseDTO> getStudent(@PathVariable String studentId) {
       // should be map first to responseDTO
       StudentResponseDTO student = studentService.getStudentProfile(studentId);
       return ResponseEntity.ok(student);
   }

   @PatchMapping("/{studentId}/restore-default-password")
   @PreAuthorize("hasRole('ADMIN_EXECUTIVE')")
   public ResponseEntity<GlobalResponseBuilder<String>> restoreDefaultPassword(@PathVariable String studentId) {
       userService.restoreStudentPassword(studentId);
       return GlobalResponseBuilder.buildResponse(
               "Default password restored successfully",
               "Password reset to the default student format",
               HttpStatus.OK);
   }

   @PutMapping("/{studentId}/complete-profile")
   @PreAuthorize("hasRole('STUDENT')")
   public ResponseEntity<GlobalResponseBuilder<StudentResponseDTO>> completeProfile(
           @PathVariable String studentId,
           @Valid @RequestBody UserRequestDTO userRequestDTO) {
       
       StudentResponseDTO updated = studentService.completeStudentProfile(studentId, userRequestDTO);
       String message = "Profile completed successfully";
       return GlobalResponseBuilder.buildResponse(message, updated, HttpStatus.OK);
   }
}
