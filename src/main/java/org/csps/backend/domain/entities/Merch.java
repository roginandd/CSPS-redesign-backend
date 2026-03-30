package org.csps.backend.domain.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.csps.backend.domain.enums.MerchType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.List;

import org.hibernate.annotations.BatchSize;

@Builder
@Entity
@Table(name = "merch", indexes = {
    @Index(name = "idx_merch_type", columnList = "merch_type"),
    @Index(name = "idx_merch_is_active", columnList = "is_active")
})
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Merch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long merchId;

    @Column(nullable = false)
    private String merchName;

    @Column(nullable = false, length=1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchType merchType;

    @Column(nullable = false)
    private Double basePrice;

    @Column(nullable = false)
    private String s3ImageKey;  // S3 object key of the first variant's image - REQUIRED

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;  // soft delete flag

    @OneToMany(mappedBy = "merch", cascade = CascadeType.ALL, orphanRemoval = true)
    @Fetch(FetchMode.SUBSELECT)
    @BatchSize(size = 20)
    private List<MerchVariant> merchVariantList;
}
