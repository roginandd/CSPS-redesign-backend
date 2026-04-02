package org.csps.backend.mapper;

import org.csps.backend.domain.dtos.response.TicketFreebieConfigResponseDTO;
import org.csps.backend.domain.entities.TicketFreebieConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TicketFreebieConfigMapper {
    @Mapping(source = "displayOrder", target = "displayOrder")
    @Mapping(target = "sizes", expression = "java(config.getSizeOptions().stream().map(option -> option.getSizeLabel()).toList())")
    @Mapping(target = "colors", expression = "java(config.getColorOptions().stream().map(option -> option.getColorLabel()).toList())")
    @Mapping(target = "designs", expression = "java(config.getDesignOptions().stream().map(option -> option.getDesignLabel()).toList())")
    TicketFreebieConfigResponseDTO toResponseDTO(TicketFreebieConfig config);
}
