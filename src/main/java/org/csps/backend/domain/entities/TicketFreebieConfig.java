package org.csps.backend.domain.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.csps.backend.domain.enums.TicketFreebieCategory;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "ticket_freebie_config",
    indexes = {
        @Index(name = "idx_ticket_freebie_config_ticket_merch", columnList = "ticket_merch_id")
    }
)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TicketFreebieConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ticketFreebieConfigId;

    @ManyToOne
    @JoinColumn(name = "ticket_merch_id", nullable = false)
    private Merch ticketMerch;

    @Column(nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketFreebieCategory category;

    @Column(nullable = false)
    private String freebieName;

    @Column(nullable = true)
    private String clothingSubtype;

    @OneToMany(mappedBy = "ticketFreebieConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TicketFreebieSizeOption> sizeOptions = new ArrayList<>();

    @OneToMany(mappedBy = "ticketFreebieConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TicketFreebieColorOption> colorOptions = new ArrayList<>();

    @OneToMany(mappedBy = "ticketFreebieConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TicketFreebieDesignOption> designOptions = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
