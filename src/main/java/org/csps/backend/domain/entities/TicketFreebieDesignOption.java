package org.csps.backend.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "ticket_freebie_design_option",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_ticket_freebie_design_option", columnNames = {"ticket_freebie_config_id", "design_label"})
    },
    indexes = {
        @Index(name = "idx_ticket_freebie_design_option_config", columnList = "ticket_freebie_config_id")
    }
)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TicketFreebieDesignOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ticketFreebieDesignOptionId;

    @ManyToOne
    @JoinColumn(name = "ticket_freebie_config_id", nullable = false)
    private TicketFreebieConfig ticketFreebieConfig;

    @Column(name = "design_label", nullable = false)
    private String designLabel;
}
