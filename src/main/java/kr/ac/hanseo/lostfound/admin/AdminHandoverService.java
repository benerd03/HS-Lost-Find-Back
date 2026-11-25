package kr.ac.hanseo.lostfound.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.hanseo.lostfound.claim.ClaimRequest;
import kr.ac.hanseo.lostfound.claim.ClaimStatus;
import kr.ac.hanseo.lostfound.common.error.BusinessException;
import kr.ac.hanseo.lostfound.common.error.ErrorCode;
import kr.ac.hanseo.lostfound.founditem.FoundItem;
import kr.ac.hanseo.lostfound.founditem.FoundItemStatus;
import kr.ac.hanseo.lostfound.handover.Handover;
import kr.ac.hanseo.lostfound.handover.HandoverRepository;
import kr.ac.hanseo.lostfound.handover.HandoverStatus;
import lombok.RequiredArgsConstructor;

/** 관리자 A4 전달 현황 (보기 전용). 좌표는 보내지 않고 이동 가능 시각은 보낸다 (보고서 ④-4 기본안 A) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminHandoverService {

    private final HandoverRepository handoverRepository;

    public record HandoverList(Summary summary, List<HandoverRow> items) {
    }

    /** ongoing = WAITING + PLACE_SET, placeSet = 그중 PLACE_SET, done = DONE (날짜 필터만 적용) */
    public record Summary(long ongoing, long placeSet, long done) {
    }

    public record HandoverRow(Long handoverId, HandoverStatus status, ItemBrief item, ClaimBrief claim,
            String finderName, String ownerName, BuildingBrief building, LocalDateTime finderReadyUntil,
            LocalDateTime ownerReadyUntil, LocalDateTime placeSetAt, LocalDateTime completedAt) {

        static HandoverRow from(Handover h) {
            ClaimRequest c = h.getClaim();
            FoundItem f = c.getFoundItem();
            return new HandoverRow(h.getId(), h.getStatus(),
                    new ItemBrief(f.getId(), f.getItemName(), f.getImagePath(), f.getStatus()),
                    new ClaimBrief(c.getId(), c.getStatus(), c.getProcessedAt()),
                    f.getFinder().getName(), c.getStudent().getName(),
                    h.getBuilding() == null ? null : new BuildingBrief(h.getBuilding().getId(), h.getBuilding().getName()),
                    h.getFinderReadyUntil(), h.getOwnerReadyUntil(), h.getPlaceSetAt(), h.getCompletedAt());
        }
    }

    public record ItemBrief(Long foundItemId, String itemName, String imageUrl, FoundItemStatus status) {
    }

    public record ClaimBrief(Long claimId, ClaimStatus status, LocalDateTime approvedAt) {
    }

    public record BuildingBrief(Long buildingId, String name) {
    }

    /**
     * @param status WAITING, PLACE_SET, DONE, ONGOING(WAITING+PLACE_SET), 생략 시 전체
     * @param from   주인 확정일 시작 (포함)
     * @param to     주인 확정일 끝 (포함)
     */
    public HandoverList list(String status, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "from은 to보다 늦을 수 없습니다.");
        }
        LocalDateTime fromAt = from == null ? null : from.atStartOfDay();
        LocalDateTime toAt = to == null ? null : to.plusDays(1).atStartOfDay();

        Map<HandoverStatus, Long> counts = new EnumMap<>(HandoverStatus.class);
        for (Object[] row : handoverRepository.countByStatus(fromAt, toAt)) {
            counts.put((HandoverStatus) row[0], (Long) row[1]);
        }
        long waiting = counts.getOrDefault(HandoverStatus.WAITING, 0L);
        long placeSet = counts.getOrDefault(HandoverStatus.PLACE_SET, 0L);
        Summary summary = new Summary(waiting + placeSet, placeSet, counts.getOrDefault(HandoverStatus.DONE, 0L));

        List<HandoverRow> items = handoverRepository.findForAdmin(parseStatus(status), fromAt, toAt).stream()
                .map(HandoverRow::from).toList();
        return new HandoverList(summary, items);
    }

    public HandoverRow detail(Long id) {
        return handoverRepository.findWithDetailsById(id).map(HandoverRow::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private static Set<HandoverStatus> parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return EnumSet.allOf(HandoverStatus.class);
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (value.equals("ONGOING")) {
            return EnumSet.of(HandoverStatus.WAITING, HandoverStatus.PLACE_SET);
        }
        try {
            return EnumSet.of(HandoverStatus.valueOf(value));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "status: WAITING, PLACE_SET, DONE, ONGOING 중 하나여야 합니다.");
        }
    }
}
