package org.csps.backend.domain.entities;

import org.csps.backend.domain.enums.ClothingSizing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "ticket_freebie_size_option",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_ticket_freebie_size_option", columnNames = {"ticket_freebie_config_id", "size_label"})
    },
    indexes = {
        @Index(name = "idx_ticket_freebie_size_option_config", columnList = "ticket_freebie_config_id")
    }
)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TicketFreebieSizeOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ticketFreebieSizeOptionId;

    @ManyToOne
    @JoinColumn(name = "ticket_freebie_config_id", nullable = false)
    private TicketFreebieConfig ticketFreebieConfig;

    @Enumerated(EnumType.STRING)
    @Column(name = "size_label", nullable = false)
    private ClothingSizing sizeLabel;
}
