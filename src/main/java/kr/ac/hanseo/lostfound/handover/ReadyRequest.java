package kr.ac.hanseo.lostfound.handover;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/** "지금 이동 가능": GPS(lat·lng) 또는 시연 모드의 현재 건물(buildingId) 중 하나 */
public record ReadyRequest(
        @DecimalMin("-90") @DecimalMax("90") Double lat,
        @DecimalMin("-180") @DecimalMax("180") Double lng,
        Long buildingId) {
}
