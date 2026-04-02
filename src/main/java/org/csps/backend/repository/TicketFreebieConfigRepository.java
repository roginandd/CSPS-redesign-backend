package org.csps.backend.repository;

import java.util.List;

import org.csps.backend.domain.entities.TicketFreebieConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketFreebieConfigRepository extends JpaRepository<TicketFreebieConfig, Long> {
    List<TicketFreebieConfig> findByTicketMerchMerchIdOrderByDisplayOrderAscTicketFreebieConfigIdAsc(Long ticketMerchId);

    List<TicketFreebieConfig> findByTicketMerchMerchIdInOrderByTicketMerchMerchIdAscDisplayOrderAscTicketFreebieConfigIdAsc(
            List<Long> ticketMerchIds);

    boolean existsByTicketMerchMerchId(Long ticketMerchId);

    void deleteByTicketMerchMerchId(Long ticketMerchId);
}
