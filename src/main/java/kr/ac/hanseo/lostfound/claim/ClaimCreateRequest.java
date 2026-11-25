package kr.ac.hanseo.lostfound.claim;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 수령 신청. lostItemId는 선택 (Top 3 화면에서 신청하면 프론트가 자동으로 넣는다) */
public record ClaimCreateRequest(
        @NotBlank @Size(max = 1000) String ownershipEvidence,
        Long lostItemId) {
}
