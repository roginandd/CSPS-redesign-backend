package org.csps.backend.domain.entities;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.csps.backend.domain.enums.ClothingSizing;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

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
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "merch_variant",
    indexes = {
        @Index(name = "idx_merch_id", columnList = "merch_id")
    }
)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MerchVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long merchVariantId;

    @ManyToOne
    @JoinColumn(name = "merch_id", nullable = false)
    private Merch merch;

    @OneToMany(mappedBy="merchVariant", cascade=CascadeType.ALL)
    @Fetch(FetchMode.SUBSELECT)
    @BatchSize(size = 20)
    private List<MerchVariantItem> merchVariantItems;

    @Column(nullable = true)
    private String color;

    @Column(nullable = true)
    private String design;

    @Column(nullable = false)
    private String s3ImageKey;  // S3 object key - REQUIRED, every variant must have an image

}
