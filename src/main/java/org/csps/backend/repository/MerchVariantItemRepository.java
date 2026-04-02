package org.csps.backend.repository;

import java.util.List;
import java.util.Optional;

import org.csps.backend.domain.entities.MerchVariant;
import org.csps.backend.domain.entities.MerchVariantItem;
import org.csps.backend.domain.enums.ClothingSizing;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

@Repository
public interface MerchVariantItemRepository extends JpaRepository<MerchVariantItem, Long> {
    
    /**
     * Find all items for a specific variant.
     */
    @EntityGraph(attributePaths = {"merchVariant", "merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    List<MerchVariantItem> findByMerchVariantMerchVariantId(Long merchVariantId);
    
    /**
     * Find item by variant and size.
     */
    @EntityGraph(attributePaths = {"merchVariant", "merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    Optional<MerchVariantItem> findByMerchVariantAndSize(MerchVariant merchVariant, ClothingSizing size);
    
    /**
     * Check if item with size exists for variant.
     */
    boolean existsByMerchVariantAndSize(MerchVariant merchVariant, ClothingSizing size);
    
    /**
     * Find top 5 items ordered by stock ascending - eager load merch hierarchy to prevent N+1.
     * Only includes items where the parent merch is active (not soft deleted).
     */
    @EntityGraph(attributePaths = {"merchVariant", "merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT mvi FROM MerchVariantItem mvi WHERE mvi.merchVariant.merch.isActive = true ORDER BY mvi.stockQuantity ASC")
    List<MerchVariantItem> findTop5ByOrderByStockQuantityAsc();

    /**
     * Find by ID with pessimistic write lock.
     * Prevents concurrent stock updates by acquiring database-level lock.
     * Used during stock deduction to ensure thread-safe operations.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT mvi FROM MerchVariantItem mvi WHERE mvi.merchVariantItemId = :id")
    Optional<MerchVariantItem> findByIdWithLock(Long id);

}
