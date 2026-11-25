package kr.ac.hanseo.lostfound.founditem;

import com.fasterxml.jackson.annotation.JsonValue;

import kr.ac.hanseo.lostfound.claim.ClaimStatus;

/** 수령 신청 버튼 상태 (명세 "claimEligibility" 표). 습득물 상세와 매칭 결과에서 같은 규칙을 쓴다. */
public enum ClaimEligibility {
    ALLOWED("allowed"),
    OWN_ITEM("own-item"),
    ALREADY_REQUESTED("already-requested"),
    REJECTED("rejected"),
    CANCELLED("cancelled"),
    CLOSED("closed");

    private final String value;

    ClaimEligibility(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** 판정 순서: own-item → (내 신청이 있으면) 신청 상태 → closed → allowed */
    public static ClaimEligibility of(boolean mine, ClaimStatus myClaimStatus, FoundItemStatus itemStatus) {
        if (mine) {
            return OWN_ITEM;
        }
        if (myClaimStatus != null) {
            return switch (myClaimStatus) {
                case PENDING, APPROVED, COMPLETED -> ALREADY_REQUESTED;
                case REJECTED -> REJECTED;
                case CANCELLED -> CANCELLED;
            };
        }
        if (itemStatus == FoundItemStatus.AWAITING_PICKUP || itemStatus == FoundItemStatus.CLAIMED) {
            return CLOSED;
        }
        return ALLOWED;
    }
}
