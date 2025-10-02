package org.csps.backend.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.csps.backend.domain.entities.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByEventDate(LocalDate eventDate);
    @Query("""
            SELECT CASE WHEN COUNT(e) > 0 THEN TRUE ELSE FALSE END
            FROM Event e
            WHERE e.eventDate = :eventDate
            AND e.startTime < :endTime
            AND e.endTime > :startTime
        """)
        boolean isDateOverlap(
            @Param("eventDate") LocalDate eventDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
        );
}
