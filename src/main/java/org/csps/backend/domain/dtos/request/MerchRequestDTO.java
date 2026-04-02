package org.csps.backend.domain.dtos.request;

import java.util.ArrayList;
import java.util.List;

import org.csps.backend.domain.enums.MerchType;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MerchRequestDTO {
    @NotBlank(message = "Merchandise name is required")
    @Size(max = 100, message = "Merchandise name must not exceed 100 characters")
    private String merchName;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @NotNull(message = "Merchandise type is required")
    private MerchType merchType;

    @NotNull(message = "Price is required")
    @Positive
    private Double basePrice;

    private String s3ImageKey;

    @JsonIgnore
    private MultipartFile merchImage;

    @Builder.Default
    private Boolean hasFreebie = false;

    @Valid
    @Builder.Default
    private List<TicketFreebieConfigRequestDTO> freebieConfigs = new ArrayList<>();

    @JsonProperty("variants")
    @Valid
    @Builder.Default
    private List<MerchVariantRequestDTO> merchVariantRequestDto = new ArrayList<>();
}
