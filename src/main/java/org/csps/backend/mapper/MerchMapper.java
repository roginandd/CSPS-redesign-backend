package org.csps.backend.mapper;

import org.csps.backend.domain.dtos.request.MerchRequestDTO;
import org.csps.backend.domain.dtos.response.MerchDetailedResponseDTO;
import org.csps.backend.domain.dtos.response.MerchSummaryResponseDTO;
import org.csps.backend.domain.entities.Merch;
import org.csps.backend.domain.entities.MerchVariant;
import org.csps.backend.domain.entities.MerchVariantItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {MerchVariantMapper.class})
public interface MerchMapper {
    @Mapping(target = "variants", source = "merchVariantList")
    @Mapping(target = "freebieConfigs", ignore = true)
    @Mapping(target = "purchaseBlocked", ignore = true)
    @Mapping(target = "purchaseBlockMessage", ignore = true)
    MerchDetailedResponseDTO toDetailedResponseDTO(Merch merch);

    @Mapping(target = "freebieConfigs", ignore = true)
    @Mapping(target = "purchaseBlocked", ignore = true)
    @Mapping(target = "purchaseBlockMessage", ignore = true)
    @Mapping(target = "totalStockQuantity", expression = "java(getTotalStockQuantity(merch))")
    MerchSummaryResponseDTO toSummaryResponseDTO(Merch merch);

    @Mapping(target = "merchVariantList", source = "merchVariantRequestDto")
    Merch toEntity(MerchRequestDTO dto);

    default Integer getTotalStockQuantity(Merch merch) {
        if (merch == null || merch.getMerchVariantList() == null) {
            return 0;
        }
        return merch.getMerchVariantList().stream()
            .map(MerchVariant::getMerchVariantItems)
            .filter(items -> items != null)
            .flatMap(java.util.List::stream)
            .map(MerchVariantItem::getStockQuantity)
            .filter(stock -> stock != null)
            .reduce(0, Integer::sum);
    }
}
