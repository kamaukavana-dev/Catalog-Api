package com.catalog.attribute.api;

import com.catalog.attribute.api.dto.request.CreateAttributeTypeRequest;
import com.catalog.attribute.api.dto.request.CreateAttributeValueRequest;
import com.catalog.attribute.api.dto.response.AttributeTypeResponse;
import com.catalog.attribute.api.dto.response.AttributeValueResponse;
import com.catalog.attribute.application.AttributeService;
import com.catalog.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attributes")
@RequiredArgsConstructor
public class AttributeController {

    private final AttributeService attributeService;

    @PostMapping("/types")
    public ResponseEntity<ApiResponse<AttributeTypeResponse>> createType(
            @Valid @RequestBody CreateAttributeTypeRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Attribute type created successfully", attributeService.createAttributeType(request)));
    }

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<AttributeTypeResponse>>> getTypes() {
        return ResponseEntity.ok(ApiResponse.success(attributeService.getAttributeTypes()));
    }

    @PostMapping("/types/{typeId}/values")
    public ResponseEntity<ApiResponse<AttributeValueResponse>> createValue(
            @PathVariable UUID typeId,
            @Valid @RequestBody CreateAttributeValueRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Attribute value created successfully", attributeService.createAttributeValue(typeId, request)));
    }

    @GetMapping("/types/{typeId}/values")
    public ResponseEntity<ApiResponse<List<AttributeValueResponse>>> getValues(
            @PathVariable UUID typeId) {

        return ResponseEntity.ok(ApiResponse.success(attributeService.getAttributeValues(typeId)));
    }
}

