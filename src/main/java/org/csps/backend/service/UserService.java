package org.csps.backend.service;

import java.util.List;

import org.csps.backend.domain.dtos.request.ChangePasswordRequestDTO;
import org.csps.backend.domain.dtos.request.StudentRequestDTO;
import org.csps.backend.domain.dtos.request.UserRequestDTO;
import org.csps.backend.domain.dtos.response.UserResponseDTO;
import org.csps.backend.domain.entities.UserAccount;

public interface UserService {
    UserAccount createUser(StudentRequestDTO student, UserRequestDTO userRequestDTO); 
    UserResponseDTO getUserById(Long userId);
    List<UserResponseDTO> getAllUsers();
    void changePassword(Long userId, ChangePasswordRequestDTO requestDTO);
    void restoreStudentPassword(String studentId);

}
