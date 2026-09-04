package com.lubrimanager.category.dto;

import com.lubrimanager.category.Category;
import java.time.Instant;

public record CategoryResponse(Long id, String name, String description, boolean active, Instant createdAt) {

    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.isActive(), c.getCreatedAt());
    }
}
