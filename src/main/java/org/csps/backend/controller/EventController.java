package org.csps.backend.controller;

import java.time.LocalDate;
import java.util.List;

import org.csps.backend.domain.dtos.request.EventPostRequestDTO;
import org.csps.backend.domain.dtos.request.EventUpdateRequestDTO;
import org.csps.backend.domain.dtos.response.EventResponseDTO;
import org.csps.backend.domain.dtos.response.GlobalResponseBuilder;
import org.csps.backend.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/event")
public class EventController {

    private final EventService eventService;


    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<List<EventResponseDTO>>> getAllEvents() {
        List<EventResponseDTO> events = eventService.getAllEvents();

        String message = "Events retrieved successfully";
        return GlobalResponseBuilder.buildResponse(message, events, HttpStatus.OK);
    } 

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<EventResponseDTO>> getEventById(@PathVariable Long id) {
        EventResponseDTO event = eventService.getEventById(id);
        String message = "Event retrieved successfully";
        return GlobalResponseBuilder.buildResponse(message, event, HttpStatus.OK);
    }

    @GetMapping("")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<List<EventResponseDTO>>> getEventByDate(@RequestParam LocalDate eventDate) {
        List<EventResponseDTO> events = eventService.getEventByDate(eventDate);

        String message = "Event retrieved successfully";    
        return GlobalResponseBuilder.buildResponse(message, events, HttpStatus.OK);
    }

    @PostMapping("/add")
    @PreAuthorize("hasRole('ADMIN_EXECUTIVE')")
    public ResponseEntity<GlobalResponseBuilder<EventResponseDTO>> addEvent(@RequestBody EventPostRequestDTO eventPostRequestDTO) {
        EventResponseDTO event = eventService.postEvent(eventPostRequestDTO);
        String message = "Event added successfully";
        return GlobalResponseBuilder.buildResponse(message, event, HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_EXECUTIVE')")
    public ResponseEntity<GlobalResponseBuilder<EventResponseDTO>> deleteEvent(@PathVariable Long id) {
        EventResponseDTO event = eventService.deleteEvent(id);
        String message = "Event deleted successfully";
        return GlobalResponseBuilder.buildResponse(message, event, HttpStatus.OK);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_EXECUTIVE')")
    public ResponseEntity<GlobalResponseBuilder<EventResponseDTO>> putEvent(@PathVariable Long id, @RequestBody EventUpdateRequestDTO eventUpdateRequestDTO) {
        EventResponseDTO event = eventService.putEvent(id, eventUpdateRequestDTO);
        String message = "Event updated successfully";
        return GlobalResponseBuilder.buildResponse(message, event, HttpStatus.OK);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_EXECUTIVE')")
    public ResponseEntity<GlobalResponseBuilder<EventResponseDTO>> patchEvent(@PathVariable Long id, @RequestBody EventUpdateRequestDTO eventUpdateRequestDTO) {
        EventResponseDTO event = eventService.patchEvent(id, eventUpdateRequestDTO);
        String message = "Event patched successfully";
        return GlobalResponseBuilder.buildResponse(message, event, HttpStatus.OK);
    }
}
