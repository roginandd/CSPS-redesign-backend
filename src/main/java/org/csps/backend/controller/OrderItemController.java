package org.csps.backend.controller;

import java.util.List;

import org.csps.backend.annotation.Auditable;
import org.csps.backend.domain.dtos.request.OrderItemRequestDTO;
import org.csps.backend.domain.dtos.request.TicketFreebieAssignmentRequestDTO;
import org.csps.backend.domain.dtos.response.GlobalResponseBuilder;
import org.csps.backend.domain.dtos.response.OrderItemResponseDTO;
import org.csps.backend.domain.dtos.response.TicketFreebieAssignmentResponseDTO;
import org.csps.backend.domain.enums.AuditAction;
import org.csps.backend.domain.enums.OrderStatus;
import org.csps.backend.service.OrderItemService;
import org.csps.backend.service.TicketFreebieAssignmentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/order-items")
@RequiredArgsConstructor
public class OrderItemController {

    private final OrderItemService orderItemService;
    private final TicketFreebieAssignmentService ticketFreebieAssignmentService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GlobalResponseBuilder<OrderItemResponseDTO>> createOrderItem(
            @Valid @RequestBody OrderItemRequestDTO requestDTO) {
        OrderItemResponseDTO responseDTO = orderItemService.createOrderItem(requestDTO);
        return GlobalResponseBuilder.buildResponse("Order item created successfully", responseDTO, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN')")
    public ResponseEntity<GlobalResponseBuilder<OrderItemResponseDTO>> getOrderItemById(
            @PathVariable Long id,
            Authentication authentication) {
        OrderItemResponseDTO responseDTO = orderItemService.getOrderItemById(id, resolveStudentScope(authentication));
        return GlobalResponseBuilder.buildResponse("Order item retrieved successfully", responseDTO, HttpStatus.OK);
    }

    @GetMapping
    @PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN')")
    public ResponseEntity<GlobalResponseBuilder<List<OrderItemResponseDTO>>> getOrderItemsByOrderId(
            @RequestParam Long orderId,
            Authentication authentication) {
        List<OrderItemResponseDTO> responseDTOs = orderItemService.getOrderItemsByOrderId(orderId, resolveStudentScope(authentication));
        return GlobalResponseBuilder.buildResponse("Order items retrieved successfully", responseDTOs, HttpStatus.OK);
    }

    @GetMapping("/paginated")
    @PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN')")
    public ResponseEntity<GlobalResponseBuilder<List<OrderItemResponseDTO>>> getOrderItemsByOrderIdPaginated(
            @RequestParam Long orderId,
            Authentication authentication,
            Pageable pageable) {
        Page<OrderItemResponseDTO> page = orderItemService.getOrderItemsByOrderIdPaginated(orderId, pageable, resolveStudentScope(authentication));
        return GlobalResponseBuilder.buildResponse("Order items retrieved successfully", page.getContent(), HttpStatus.OK);
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<Page<OrderItemResponseDTO>>> getOrderItemsByStatus(
            @RequestParam OrderStatus status,
            @PageableDefault(size = 5) Pageable pageable,
            Authentication authentication) {
        Page<OrderItemResponseDTO> page = orderItemService.getOrderItemsByStatus(status, pageable, resolveStudentScope(authentication));
        return GlobalResponseBuilder.buildResponse("Order items retrieved successfully", page, HttpStatus.OK);
    }

    @GetMapping("/my-items")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<Page<OrderItemResponseDTO>>> getMyOrderItems(
            @AuthenticationPrincipal String studentId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<OrderItemResponseDTO> page = orderItemService.getOrderItemsByStudentIdPaginated(studentId, pageable);
        return GlobalResponseBuilder.buildResponse("Order items retrieved successfully", page, HttpStatus.OK);
    }

    @GetMapping("/my-items/status")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<Page<OrderItemResponseDTO>>> getMyOrderItemsByStatus(
            @AuthenticationPrincipal String studentId,
            @RequestParam OrderStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<OrderItemResponseDTO> page = orderItemService.getOrderItemsByStudentIdAndStatusPaginated(studentId, status, pageable);
        return GlobalResponseBuilder.buildResponse("Order items retrieved successfully", page, HttpStatus.OK);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GlobalResponseBuilder<OrderItemResponseDTO>> updateOrderItemStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status) {
        OrderItemResponseDTO responseDTO = orderItemService.updateOrderItemStatus(id, status);
        return GlobalResponseBuilder.buildResponse("Order item status updated successfully", responseDTO, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GlobalResponseBuilder<Void>> deleteOrderItem(@PathVariable Long id) {
        orderItemService.deleteOrderItem(id);
        return GlobalResponseBuilder.buildResponse("Order item deleted successfully", null, HttpStatus.NO_CONTENT);
    }

    @GetMapping("/{orderItemId}/freebie-assignment")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ADMIN_FINANCE') or hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<List<TicketFreebieAssignmentResponseDTO>>> getOrderItemFreebieAssignment(
            @PathVariable Long orderItemId,
            Authentication authentication) {
        String studentScope = resolveStudentScope(authentication);
        List<TicketFreebieAssignmentResponseDTO> response = studentScope == null
                ? ticketFreebieAssignmentService.getAssignmentsByOrderItemId(orderItemId)
                : orderItemService.getOrderItemById(orderItemId, studentScope).getFreebieAssignments();
        return GlobalResponseBuilder.buildResponse("Freebie assignment retrieved successfully", response, HttpStatus.OK);
    }

    @PutMapping("/{orderItemId}/freebie-assignment")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ADMIN_FINANCE')")
    @Auditable(action = AuditAction.UPDATE, resourceType = "TicketFreebieAssignment")
    public ResponseEntity<GlobalResponseBuilder<List<TicketFreebieAssignmentResponseDTO>>> upsertOrderItemFreebieAssignment(
            @PathVariable Long orderItemId,
            @Valid @RequestBody List<TicketFreebieAssignmentRequestDTO> request) {
        List<TicketFreebieAssignmentResponseDTO> response = ticketFreebieAssignmentService.upsertAssignments(orderItemId, request);
        return GlobalResponseBuilder.buildResponse("Freebie assignment updated successfully", response, HttpStatus.OK);
    }

    private String resolveStudentScope(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        boolean studentRequest = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_STUDENT".equals(authority.getAuthority()));

        return studentRequest ? String.valueOf(authentication.getPrincipal()) : null;
    }
}
