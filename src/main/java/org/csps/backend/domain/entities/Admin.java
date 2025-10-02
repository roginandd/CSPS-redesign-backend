package org.csps.backend.domain.entities;

import org.csps.backend.domain.enums.AdminPosition;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table (indexes = @Index(name = "idx_admin_position", columnList = "position"))
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long adminId;

    @OneToOne(cascade=CascadeType.REMOVE)
    @JoinColumn(name = "user_account_id", unique = true)
    private UserAccount userAccount;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminPosition position;
}
