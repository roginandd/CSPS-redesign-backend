package org.csps.backend.domain.dtos.response;

import java.time.LocalDate;
import java.time.LocalTime;

import org.csps.backend.domain.enums.EventStatus;
import org.csps.backend.domain.enums.EventType;

import lombok.Data;

@Data
public class EventResponseDTO {
    private Long eventId;
    private String eventName;
    private String eventDescription;
    private String eventLocation;
    private LocalDate eventDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private EventType eventType;
    private EventStatus eventStatus;
}
