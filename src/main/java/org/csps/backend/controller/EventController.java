package org.csps.backend.controller;

import java.time.LocalDate;
import java.util.List;

import org.csps.backend.annotation.Auditable;
import org.csps.backend.domain.dtos.request.EventPostRequestDTO;
import org.csps.backend.domain.dtos.request.EventUpdateRequestDTO;
import org.csps.backend.domain.dtos.response.EventResponseDTO;
import org.csps.backend.domain.dtos.response.GlobalResponseBuilder;
import org.csps.backend.domain.enums.AuditAction;
import org.csps.backend.service.EventService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/event")
public class EventController {

    private final EventService eventService;
    private final ObjectMapper objectMapper;


    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<List<EventResponseDTO>>> getAllEvents() {
        List<EventResponseDTO> events = eventService.getAllEvents();

        String message = "Events retrieved successfully";
        return GlobalResponseBuilder.buildResponse(message, events, HttpStatus.OK);
    } 

    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<Page<EventResponseDTO>>> searchEvents(
        @RequestParam String query,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @PageableDefault(size = 10, sort = "eventDate", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<EventResponseDTO> results = eventService.searchEvent(query, startDate, endDate, pageable);
        String message = "Events retrieved successfully";
        return GlobalResponseBuilder.buildResponse(message, results, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<EventResponseDTO>> getEventById(@PathVariable Long id) {
        EventResponseDTO event = eventService.getEventById(id);
        String message = "Event retrieved successfully";
        return GlobalResponseBuilder.buildResponse(message, event, HttpStatus.OK);
    }

    @GetMapping("/image/{s3ImageKey}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<EventResponseDTO>> getEventByS3ImageKey(@PathVariable String s3ImageKey) {
        EventResponseDTO event = eventService.getEventByS3ImageKey(s3ImageKey);
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

    @GetMapping("/upcoming")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<Page<EventResponseDTO>>> getUpcomingEvents(
        @PageableDefault(size = 5) Pageable pageable
    ) {
        Page<EventResponseDTO> events = eventService.getUpcomingEventsPaginated(pageable);
        String message = "Upcoming events retrieved successfully";
        return GlobalResponseBuilder.buildResponse(message, events, HttpStatus.OK);
    }

    @GetMapping("/by-month")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<List<EventResponseDTO>>> getEventsByMonth(
        @RequestParam int year,
        @RequestParam int month) {
        List<EventResponseDTO> events = eventService.getEventsByMonth(year, month);
        String message = "Events for " + month + "/" + year + " retrieved successfully";
        return GlobalResponseBuilder.buildResponse(message, events, HttpStatus.OK);
    }

    @GetMapping("/my-history")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<Page<EventResponseDTO>>> getMyEventHistory(
        @AuthenticationPrincipal String studentId,
        @PageableDefault(size = 5) Pageable pageable) {
        Page<EventResponseDTO> events = eventService.getEventsByStudentId(pageable, studentId);
        String message = "Event history retrieved successfully";
        return GlobalResponseBuilder.buildResponse(message, events, HttpStatus.OK);
    }

    @GetMapping("/past")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<List<EventResponseDTO>>> getPastEvents() {
        List<EventResponseDTO> events = eventService.getPastEvents();
        String message = "Past events retrieved successfully";
        return GlobalResponseBuilder.buildResponse(message, events, HttpStatus.OK);
    }

    @PostMapping("/add")
    @PreAuthorize("hasRole('ADMIN_EXECUTIVE')")
    @Auditable(action = AuditAction.CREATE, resourceType = "Event")
    public ResponseEntity<GlobalResponseBuilder<EventResponseDTO>> addEvent(
            @RequestParam("event") String eventJson,
            @RequestParam(value = "eventImage", required = false) MultipartFile eventImage) throws Exception {
        EventPostRequestDTO eventPostRequestDTO = objectMapper.readValue(eventJson, EventPostRequestDTO.class);
        EventResponseDTO event = eventService.postEvent(eventPostRequestDTO, eventImage);
        String message = "Event added successfully";
        return GlobalResponseBuilder.buildResponse(message, event, HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_EXECUTIVE')")
    @Auditable(action = AuditAction.DELETE, resourceType = "Event")
    public ResponseEntity<GlobalResponseBuilder<EventResponseDTO>> deleteEvent(@PathVariable Long id) {
        EventResponseDTO event = eventService.deleteEvent(id);
        String message = "Event deleted successfully";
        return GlobalResponseBuilder.buildResponse(message, event, HttpStatus.OK);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_EXECUTIVE')")
    @Auditable(action = AuditAction.UPDATE, resourceType = "Event")
    public ResponseEntity<GlobalResponseBuilder<EventResponseDTO>> putEvent(
            @PathVariable Long id,
            @RequestParam("event") String eventJson,
            @RequestParam(value = "eventImage", required = false) MultipartFile eventImage) throws Exception {
        EventUpdateRequestDTO eventUpdateRequestDTO = objectMapper.readValue(eventJson, EventUpdateRequestDTO.class);
        EventResponseDTO event = eventService.putEvent(id, eventUpdateRequestDTO, eventImage);
        String message = "Event updated successfully";
        return GlobalResponseBuilder.buildResponse(message, event, HttpStatus.OK);
    }



    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_EXECUTIVE')")
    @Auditable(action = AuditAction.UPDATE, resourceType = "Event")
    public ResponseEntity<GlobalResponseBuilder<EventResponseDTO>> patchEvent(
            @PathVariable Long id,
            @RequestParam("event") String eventJson,
            @RequestParam(value = "eventImage", required = false) MultipartFile eventImage) throws Exception {
        EventUpdateRequestDTO eventUpdateRequestDTO = objectMapper.readValue(eventJson, EventUpdateRequestDTO.class);
        EventResponseDTO event = eventService.patchEvent(id, eventUpdateRequestDTO, eventImage);
        String message = "Event patched successfully";
        return GlobalResponseBuilder.buildResponse(message, event, HttpStatus.OK);
    }
}
