package org.csps.backend.security;

import org.csps.backend.domain.entities.Admin;
import org.csps.backend.domain.entities.Student;
import org.csps.backend.domain.entities.UserAccount;
import org.csps.backend.domain.enums.AdminPosition;
import org.csps.backend.repository.AdminRepository;
import org.csps.backend.repository.StudentRepository;
import org.csps.backend.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;
    private final StudentRepository studentRepository;
    private final AdminRepository adminRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Fetch base user account
        UserAccount user = userAccountRepository.findByUsername(username);
            
        String domainId = null;
        String role = user.getRole().name();
        String position = null;

        // Resolve domainId depending on role
        switch (role) {
            case "STUDENT" -> {
                Student student = studentRepository.findByUserAccountUserAccountId(user.getUserAccountId())
                        .orElseThrow(() -> new RuntimeException("Student not found"));
                domainId = student.getStudentId(); // use studentId as domain identifier

                return UserPrincipal.builder()
                        .user(user)
                        .domainId(domainId)
                        .role(role)
                        .build();
            }
            case "ADMIN" -> {
                Admin admin = adminRepository.findByUserAccountUserAccountId(user.getUserAccountId())
                        .orElseThrow(() -> new RuntimeException("Admin not found"));
                domainId = admin.getAdminId().toString(); // use adminId as domain identifier
                position = admin.getPosition().name();

                return UserPrincipal.builder()
                        .user(user)
                        .domainId(domainId)
                        .role(role)
                        .position(AdminPosition.valueOf(position))
                        .build();
            }
            default -> throw new RuntimeException("Role not recognized");
        }
    }
}
