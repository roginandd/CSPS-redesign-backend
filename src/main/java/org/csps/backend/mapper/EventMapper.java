package org.csps.backend.mapper;

import org.csps.backend.domain.dtos.request.EventPostRequestDTO;
import org.csps.backend.domain.dtos.response.EventResponseDTO;
import org.csps.backend.domain.entities.Event;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(source = "eventId", target = "eventId")
    @Mapping(source = "eventName", target = "eventName")
    @Mapping(source = "eventDescription", target = "eventDescription")
    @Mapping(source = "eventLocation", target = "eventLocation")
    @Mapping(source = "eventDate", target = "eventDate")
    @Mapping(source = "startTime", target = "startTime")
    @Mapping(source = "endTime", target = "endTime")
    @Mapping(source = "eventType", target = "eventType")
    @Mapping(source = "eventStatus", target = "eventStatus")
    EventResponseDTO toResponseDTO(Event event);


    @Mapping(source = "eventName", target = "eventName")
    @Mapping(source = "eventDescription", target = "eventDescription")
    @Mapping(source = "eventLocation", target = "eventLocation")
    @Mapping(source = "eventDate", target = "eventDate")
    @Mapping(source = "startTime", target = "startTime")
    @Mapping(source = "endTime", target = "endTime")
    @Mapping(source = "eventType", target = "eventType")
    @Mapping(source = "eventStatus", target = "eventStatus")
    Event toEntity(EventPostRequestDTO eventPostRequestDTO);
}
