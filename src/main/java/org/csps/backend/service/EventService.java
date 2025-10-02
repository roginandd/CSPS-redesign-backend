package org.csps.backend.service;

import java.time.LocalDate;
import java.util.List;

import org.csps.backend.domain.dtos.request.EventPostRequestDTO;
import org.csps.backend.domain.dtos.request.EventUpdateRequestDTO;
import org.csps.backend.domain.dtos.response.EventResponseDTO;

public interface EventService {
    EventResponseDTO postEvent(EventPostRequestDTO eventPostRequestDTO);
    List<EventResponseDTO> getAllEvents();
    EventResponseDTO getEventById(Long eventId);
    EventResponseDTO deleteEvent(Long eventId);
    EventResponseDTO putEvent(Long eventId, EventUpdateRequestDTO eventPostRequestDTO);
    EventResponseDTO patchEvent(Long eventId, EventUpdateRequestDTO eventPostRequestDTO);
    List<EventResponseDTO> getEventByDate(LocalDate eventDate);
}
