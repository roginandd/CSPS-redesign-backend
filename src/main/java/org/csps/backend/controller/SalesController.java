package org.csps.backend.controller;

import org.csps.backend.domain.dtos.request.OrderSearchDTO;
import org.csps.backend.domain.dtos.response.GlobalResponseBuilder;
import org.csps.backend.domain.dtos.response.sales.SalesStatsDTO;
import org.csps.backend.domain.dtos.response.sales.TransactionDTO;
import org.csps.backend.domain.enums.SalesPeriod;
import org.csps.backend.service.SalesService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SalesController {

    private final SalesService salesService;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ADMIN_FINANCE') or hasRole('ADMIN_EXECUTIVE')")
    public ResponseEntity<GlobalResponseBuilder<SalesStatsDTO>> getSalesStats(
            @RequestParam(defaultValue = "DAILY") SalesPeriod period) {
        SalesStatsDTO stats = salesService.getSalesStats(period);
        return GlobalResponseBuilder.buildResponse("Sales statistics retrieved successfully", stats, HttpStatus.OK);
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ADMIN_FINANCE') or hasRole('ADMIN_EXECUTIVE')")
    public ResponseEntity<GlobalResponseBuilder<Page<TransactionDTO>>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            @ModelAttribute OrderSearchDTO search) {
        
        Pageable pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.DESC, "orderDate").and(Sort.by(Sort.Direction.DESC, "orderId")));
        Page<TransactionDTO> transactions = salesService.getTransactions(pageable, search);
        return GlobalResponseBuilder.buildResponse("Transactions retrieved successfully", transactions, HttpStatus.OK);
    }

    @PostMapping("/transactions/{id}/approve")
    @PreAuthorize("hasRole('ADMIN_FINANCE') or hasRole('ADMIN_EXECUTIVE')")
    public ResponseEntity<GlobalResponseBuilder<TransactionDTO>> approveTransaction(
            @PathVariable Long id) {
        TransactionDTO transaction = salesService.approveTransaction(id);
        return GlobalResponseBuilder.buildResponse("Transaction approved successfully", transaction, HttpStatus.OK);
    }

    @DeleteMapping("/transactions/{id}")
    @PreAuthorize("hasRole('ADMIN_FINANCE') or hasRole('ADMIN_EXECUTIVE')")
    public ResponseEntity<GlobalResponseBuilder<Void>> rejectTransaction(
            @PathVariable Long id) {
        salesService.rejectTransaction(id);
        return GlobalResponseBuilder.buildResponse("Transaction rejected successfully", null, HttpStatus.NO_CONTENT);
    }
}