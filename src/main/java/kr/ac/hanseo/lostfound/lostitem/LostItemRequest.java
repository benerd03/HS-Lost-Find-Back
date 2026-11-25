package kr.ac.hanseo.lostfound.lostitem;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 분실 신고 등록·수정 JSON */
public record LostItemRequest(
        @NotBlank @Size(max = 100) String itemName,
        @NotNull Long categoryId,
        @NotNull LocalDateTime lostAt,
        @NotNull Long buildingId,
        @Size(max = 100) String locationDetail,
        @Size(max = 1000) String description) {
}
