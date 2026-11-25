package kr.ac.hanseo.lostfound.founditem;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 습득물 등록·수정 multipart 폼 필드 (image는 컨트롤러에서 따로 받는다) */
public record FoundItemForm(
        @NotBlank @Size(max = 100) String itemName,
        @NotNull Long categoryId,
        @NotNull LocalDateTime foundAt,
        @NotNull Long buildingId,
        @Size(max = 100) String locationDetail,
        @Size(max = 1000) String description) {
}
