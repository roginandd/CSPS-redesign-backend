package org.csps.backend.controller;

import java.util.List;

import org.csps.backend.domain.dtos.request.MerchRequestDTO;
import org.csps.backend.domain.dtos.request.MerchUpdateRequestDTO;
import org.csps.backend.domain.dtos.response.GlobalResponseBuilder;
import org.csps.backend.domain.dtos.response.MerchResponseDTO;
import org.csps.backend.service.MerchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/merch")
@RequiredArgsConstructor
public class MerchController {

    private final MerchService merchService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MerchResponseDTO> createMerch(@RequestBody MerchRequestDTO merchRequestDTO) {
        MerchResponseDTO createdMerch = merchService.createMerch(merchRequestDTO);
        return ResponseEntity.ok(createdMerch);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<List<MerchResponseDTO>> getAllMerch() {
        List<MerchResponseDTO> merchList = merchService.getAllMerch();
        return ResponseEntity.ok(merchList);
    }

    

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<MerchResponseDTO> getMerchById(@PathVariable Long id) {
        MerchResponseDTO merch = merchService.getMerchById(id);
        return ResponseEntity.ok(merch);
    }

    @PutMapping("/update/{merchId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GlobalResponseBuilder<MerchResponseDTO>> putMerch(@PathVariable Long merchId, @RequestBody MerchUpdateRequestDTO merchUpdateRequestDTO) {
        MerchResponseDTO merchResponseDTO = merchService.putMerch(merchId, merchUpdateRequestDTO);

        String message = "Merch Updated Successfully";
        return GlobalResponseBuilder.buildResponse(message, merchResponseDTO, HttpStatus.OK);
    }

    @PatchMapping("/update/{merchId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GlobalResponseBuilder<MerchResponseDTO>> patchMerch(@PathVariable Long merchId, @RequestBody MerchUpdateRequestDTO merchUpdateRequestDTO) {
        MerchResponseDTO merchResponseDTO = merchService.putMerch(merchId, merchUpdateRequestDTO);

        String message = "Merch Updated Successfully";

        return GlobalResponseBuilder.buildResponse(message, merchResponseDTO, HttpStatus.OK);
    }
    
}
