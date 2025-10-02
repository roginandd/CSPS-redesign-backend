package org.csps.backend.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.csps.backend.domain.dtos.request.EventPostRequestDTO;
import org.csps.backend.domain.dtos.request.EventUpdateRequestDTO;
import org.csps.backend.domain.dtos.request.InvalidRequestException;
import org.csps.backend.domain.dtos.response.EventResponseDTO;
import org.csps.backend.domain.entities.Event;
import org.csps.backend.domain.enums.EventStatus;
import org.csps.backend.domain.enums.EventType;
import org.csps.backend.exception.EventNotFoundException;
import org.csps.backend.mapper.EventMapper;
import org.csps.backend.repository.EventRepository;
import org.csps.backend.service.EventService;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    @Override
    @Transactional
    public EventResponseDTO postEvent(EventPostRequestDTO eventPostRequestDTO) {
        // convert the request to entity
        Event event = eventMapper.toEntity(eventPostRequestDTO);

        // check if the event already exists
        boolean existsOverlap = eventRepository.isDateOverlap(
            event.getEventDate(),
            event.getStartTime(),
            event.getEndTime()
        );

        if (existsOverlap) {
            throw new InvalidRequestException("Event already exists with the same date and time");
        }

        // validations
        if (eventPostRequestDTO.getEventName() == null || eventPostRequestDTO.getEventName().trim().isEmpty() ||
            eventPostRequestDTO.getEventDescription() == null || eventPostRequestDTO.getEventDescription().trim().isEmpty() ||
            eventPostRequestDTO.getEventLocation() == null || eventPostRequestDTO.getEventLocation().trim().isEmpty() ||
            eventPostRequestDTO.getEventDate() == null || eventPostRequestDTO.getStartTime() == null || eventPostRequestDTO.getEndTime() == null ||
            eventPostRequestDTO.getEventType() == null || eventPostRequestDTO.getEventStatus() == null) {
            throw new InvalidRequestException("Invalid Credential");
        }

        // validate the date (must be present or future)
        if (eventPostRequestDTO.getEventDate().isBefore(LocalDate.now())) {
            throw new InvalidRequestException("Event date cannot be in the past");
        }

        // if event is for today, validate that start time hasn't passed
        if (eventPostRequestDTO.getEventDate().equals(LocalDate.now()) && 
            eventPostRequestDTO.getStartTime().isBefore(LocalTime.now())) {
            throw new InvalidRequestException("Event start time cannot be in the past for today's date");
        }

        // validate the time range
        if (eventPostRequestDTO.getStartTime().isAfter(eventPostRequestDTO.getEndTime()) || eventPostRequestDTO.getStartTime().equals(eventPostRequestDTO.getEndTime())) {
            throw new InvalidRequestException("Invalid Time Range");
        }

        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        
        LocalTime startTime = eventPostRequestDTO.getStartTime();
        LocalTime endTime = eventPostRequestDTO.getEndTime();

        if (startTime.isAfter(endTime) || startTime.equals(endTime)) {
            throw new InvalidRequestException("Invalid Time Range");
        }

        // persist the entity
        eventRepository.save(event);


        // convert the entity into response dto
        EventResponseDTO eventResponseDTO = eventMapper.toResponseDTO(event);

        return eventResponseDTO;
    }

    @Override
    public List<EventResponseDTO> getAllEvents() {

        // convert all the event entities to event response dto
        List<EventResponseDTO> events = eventRepository.findAll()
                                    .stream()
                                    .map(eventMapper::toResponseDTO)
                                    .toList();

        return events;
    }

    @Override
    public EventResponseDTO getEventById(Long eventId) {

        // find the event by id
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new EventNotFoundException("Event not found with id: " + eventId));

        // convert the entity into response dto
        EventResponseDTO eventResponseDTO = eventMapper.toResponseDTO(event);

        return eventResponseDTO;
    }

    @Override
    @Transactional
    public EventResponseDTO deleteEvent(Long eventId) {

        // find the event by id
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new EventNotFoundException("Event not found with id: " + eventId));

        // delete the event
        eventRepository.delete(event);

        // convert the entity into response dto
        EventResponseDTO eventResponseDTO = eventMapper.toResponseDTO(event);

        return eventResponseDTO;
    }

    @Override
    @Transactional
    public EventResponseDTO putEvent(Long eventId, EventUpdateRequestDTO eventUpdateRequestDTO) {
    
        // find the event by id
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new EventNotFoundException("Event not found with id: " + eventId));

        // get the new values
        String newEventName = eventUpdateRequestDTO.getEventName();
        String newEventDescription = eventUpdateRequestDTO.getEventDescription();
        String newEventLocation = eventUpdateRequestDTO.getEventLocation();
        LocalDate newEventDate = eventUpdateRequestDTO.getEventDate();
        LocalTime newEventStartTime = eventUpdateRequestDTO.getStartTime();
        LocalTime newEventEndTime = eventUpdateRequestDTO.getEndTime();
        EventType newEventType = eventUpdateRequestDTO.getEventType();
        EventStatus newEventStatus = eventUpdateRequestDTO.getEventStatus();

        // validate the new values
        if (newEventName == null || newEventName.trim().isEmpty() ||
            newEventDescription == null || newEventDescription.trim().isEmpty() ||
            newEventLocation == null || newEventLocation.trim().isEmpty() ||
            newEventDate == null || newEventStartTime == null || newEventEndTime == null ||
            newEventType == null || newEventStatus == null) {
            throw new InvalidRequestException("Invalid Credential");
        }

        // validate the date (must be present or future)
        if (newEventDate.isBefore(LocalDate.now())) {
            throw new InvalidRequestException("Event date cannot be in the past");
        }

        // if event is for today, validate that start time hasn't passed
        if (newEventDate.equals(LocalDate.now()) && newEventStartTime.isBefore(LocalTime.now())) {
            throw new InvalidRequestException("Event start time cannot be in the past for today's date");
        }

        // validate the time range
        if (newEventStartTime.isAfter(newEventEndTime) || newEventStartTime.equals(newEventEndTime)) {
            throw new InvalidRequestException("Invalid Time Range");
        }

        // set the new values
        event.setEventName(newEventName);
        event.setEventDescription(newEventDescription);
        event.setEventLocation(newEventLocation);
        event.setEventDate(newEventDate);
        event.setStartTime(newEventStartTime);
        event.setEndTime(newEventEndTime);
        event.setEventType(newEventType);
        event.setEventStatus(newEventStatus);

        event.setUpdatedAt(LocalDateTime.now());

        // check if the event already exists
        boolean existsOverlap = eventRepository.isDateOverlap(
            event.getEventDate(),
            event.getEndTime(),
            event.getStartTime()
        );

        if (existsOverlap) {
            throw new InvalidRequestException("Event already exists with the same date and time");
        }

        // save the event
        eventRepository.save(event);

        // convert the entity into response dto
        EventResponseDTO eventResponseDTO = eventMapper.toResponseDTO(event);

        // return the response dto
        return eventResponseDTO;
    }

    @Override
    @Transactional
    public EventResponseDTO patchEvent(Long eventId, EventUpdateRequestDTO eventUpdateRequestDTO) {
    
        // find the event by id
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new EventNotFoundException("Event not found with id: " + eventId));

        // get the new values
        String newEventName = eventUpdateRequestDTO.getEventName();
        String newEventDescription = eventUpdateRequestDTO.getEventDescription();
        String newEventLocation = eventUpdateRequestDTO.getEventLocation();
        LocalDate newEventDate = eventUpdateRequestDTO.getEventDate();
        LocalTime newEventStartTime = eventUpdateRequestDTO.getStartTime();
        LocalTime newEventEndTime = eventUpdateRequestDTO.getEndTime();
        EventType newEventType = eventUpdateRequestDTO.getEventType();
        EventStatus newEventStatus = eventUpdateRequestDTO.getEventStatus();

        // validate the date (must be present or future) - BEFORE setting values
        if (newEventDate != null && newEventDate.isBefore(LocalDate.now())) {
            throw new InvalidRequestException("Event date cannot be in the past");
        }

        // validate the time range - BEFORE setting values
        if ((newEventStartTime != null && newEventEndTime != null) && 
            (newEventStartTime.isAfter(newEventEndTime) || newEventStartTime.equals(newEventEndTime))) {
            throw new InvalidRequestException("Invalid Time Range");
        }

        // set the new values
        if (newEventName != null && !newEventName.trim().isEmpty()) {
            event.setEventName(newEventName);
        }
        if (newEventDescription != null && !newEventDescription.trim().isEmpty()) {
            event.setEventDescription(newEventDescription);
        }   
        if (newEventLocation != null && !newEventLocation.trim().isEmpty()) {
            event.setEventLocation(newEventLocation);
        }
        if (newEventDate != null) {
            event.setEventDate(newEventDate);
        }
        if (newEventType != null) {
            event.setEventType(newEventType);
        }
        if (newEventStatus != null) {
            event.setEventStatus(newEventStatus);
        }
        
        
        if (newEventStartTime != null) {
            event.setStartTime(newEventStartTime);
        }
        if (newEventEndTime != null) {
            event.setEndTime(newEventEndTime);
        }

        event.setUpdatedAt(LocalDateTime.now());

        // check if the event already exists
        boolean existsOverlap = eventRepository.isDateOverlap(
            event.getEventDate(),
            event.getEndTime(),
            event.getStartTime()
        );

        
        if (existsOverlap) {
            throw new InvalidRequestException("Event already exists with the same date and time");
        }

        // save the event
        eventRepository.save(event);

        // convert the entity into response dto
        EventResponseDTO eventResponseDTO = eventMapper.toResponseDTO(event);

        // return the response dto
        return eventResponseDTO;
    }

    @Override
    public List<EventResponseDTO> getEventByDate(LocalDate eventDate) {
        List<Event> events = eventRepository.findByEventDate(eventDate);
    
        if (events.isEmpty()) 
            throw new EventNotFoundException("Event not found with date: " + eventDate);

        List<EventResponseDTO> eventResponseDTOs = events.stream()
                .map(eventMapper::toResponseDTO)
                .toList();
                
        // return the response dto
        return eventResponseDTOs;
    }

}
