package org.csps.backend.repository;

import java.util.List;
import java.util.Optional;

import org.csps.backend.domain.dtos.response.MerchSummaryResponseDTO;
import org.csps.backend.domain.entities.Merch;
import org.csps.backend.domain.enums.ClothingSizing;
import org.csps.backend.domain.enums.MerchType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchRepository extends JpaRepository<Merch, Long>{
    boolean existsByMerchName(String merchName);
    
    @Query("SELECT m FROM Merch m WHERE m.isActive = true")
    List<Merch> findAll();
    
    @Query("SELECT m FROM Merch m WHERE m.merchId = :id AND m.isActive = true")
    Optional<Merch> findById(@Param("id") Long id);

    @Query("SELECT m FROM Merch m WHERE m.merchId = :id AND m.isActive = false")
    Optional<Merch> findByIdAndIsInactive(@Param("id") Long id);
    
    @Query("SELECT m FROM Merch m WHERE m.merchType = :type AND m.isActive = true")
    List<Merch> findByMerchType(@Param("type") MerchType merchType);

    @Query("SELECT DISTINCT mvi.size FROM MerchVariantItem mvi WHERE mvi.merchVariant.merch.merchId = :merchId AND mvi.stockQuantity > 0")
    List<ClothingSizing> findAvailableClothingSize(@Param("merchId") Long merchId);

    /* archive queries - paginated without entity graph to avoid MultipleBagFetchException */
    @Query("SELECT m FROM Merch m WHERE m.isActive = false")
    Page<Merch> findArchivedMerch(Pageable pageable);

    @Query("""
        SELECT new org.csps.backend.domain.dtos.response.MerchSummaryResponseDTO(
            m.merchId, 
            m.merchName, 
            m.description, 
            m.merchType, 
            m.basePrice, 
            m.s3ImageKey, 
            CAST(COALESCE(SUM(i.stockQuantity), 0) AS int),
            m.hasFreebie
        )
        FROM Merch m
        LEFT JOIN m.merchVariantList v
        LEFT JOIN v.merchVariantItems i
        WHERE m.isActive = true
        GROUP BY m.merchId, m.merchName, m.description, m.merchType, m.basePrice, m.s3ImageKey, m.hasFreebie
    """)
    List<MerchSummaryResponseDTO> findAllSummaries();

    @Query("""
        SELECT new org.csps.backend.domain.dtos.response.MerchSummaryResponseDTO(
            m.merchId, 
            m.merchName, 
            m.description, 
            m.merchType, 
            m.basePrice, 
            m.s3ImageKey, 
            CAST(COALESCE(SUM(i.stockQuantity), 0) AS int),
            m.hasFreebie
        )
        FROM Merch m
        LEFT JOIN m.merchVariantList v
        LEFT JOIN v.merchVariantItems i
        WHERE m.merchType = :type AND m.isActive = true
        GROUP BY m.merchId, m.merchName, m.description, m.merchType, m.basePrice, m.s3ImageKey, m.hasFreebie
    """)
    List<MerchSummaryResponseDTO> findAllSummaryByType(@Param("type") MerchType type);
}
