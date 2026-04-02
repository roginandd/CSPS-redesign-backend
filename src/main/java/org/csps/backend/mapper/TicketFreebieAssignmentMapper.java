package org.csps.backend.mapper;

import org.csps.backend.domain.dtos.response.TicketFreebieAssignmentResponseDTO;
import org.csps.backend.domain.entities.TicketFreebieAssignment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TicketFreebieAssignmentMapper {
    @Mapping(source = "ticketFreebieAssignmentId", target = "ticketFreebieAssignmentId")
    @Mapping(source = "orderItem.orderItemId", target = "orderItemId")
    @Mapping(source = "ticketFreebieConfig.ticketFreebieConfigId", target = "ticketFreebieConfigId")
    @Mapping(target = "hasFreebie", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "freebieName", ignore = true)
    @Mapping(target = "clothingSubtype", ignore = true)
    @Mapping(target = "allowedSizes", ignore = true)
    @Mapping(target = "allowedColors", ignore = true)
    @Mapping(target = "allowedDesigns", ignore = true)
    TicketFreebieAssignmentResponseDTO toResponseDTO(TicketFreebieAssignment assignment);
}
