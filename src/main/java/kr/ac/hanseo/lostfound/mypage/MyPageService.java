package kr.ac.hanseo.lostfound.mypage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.hanseo.lostfound.claim.ClaimRequest;
import kr.ac.hanseo.lostfound.claim.ClaimRequestRepository;
import kr.ac.hanseo.lostfound.claim.ClaimStatus;
import kr.ac.hanseo.lostfound.founditem.FoundItem;
import kr.ac.hanseo.lostfound.founditem.FoundItemRepository;
import kr.ac.hanseo.lostfound.founditem.FoundItemStatus;
import kr.ac.hanseo.lostfound.handover.Handover;
import kr.ac.hanseo.lostfound.handover.HandoverRepository;
import kr.ac.hanseo.lostfound.lostitem.LostItemRepository;
import kr.ac.hanseo.lostfound.lostitem.LostItemStatus;
import lombok.RequiredArgsConstructor;

/** 마이페이지(S9)와 홈 "내 진행 현황" (FR-06) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {

    private final LostItemRepository lostItemRepository;
    private final FoundItemRepository foundItemRepository;
    private final ClaimRequestRepository claimRequestRepository;
    private final HandoverRepository handoverRepository;

    /**
     * 홈 "내 진행 현황". 모두 진행 중인 것만 센다.
     * lostItemCount: OPEN 분실 신고 / foundItemCount: 수령완료·삭제가 아닌 습득 신고
     * claimCount: PENDING·APPROVED 신청 / activeHandoverId: 진행 중 전달 중 가장 최근 1건
     */
    public record Summary(long lostItemCount, long foundItemCount, long claimCount, Long activeHandoverId) {
    }

    /** 내 습득 신고 항목 = 습득물 목록 항목 + claimCount(PENDING 신청 수) + handoverId(주인 확정 후) */
    public record MyFoundItem(Long foundItemId, String itemName, String categoryName, String buildingName,
            String locationDetail, LocalDateTime foundAt, String imageUrl, FoundItemStatus status,
            long claimCount, Long handoverId) {
    }

    public record MyClaim(Long claimId, ClaimStatus status, LocalDateTime requestedAt, FoundItemBrief foundItem,
            Long handoverId) {
    }

    public record FoundItemBrief(Long foundItemId, String itemName, String imageUrl) {
    }

    public Summary summary(String studentId) {
        Long activeHandoverId = handoverRepository.findOngoingOf(studentId, PageRequest.of(0, 1)).stream()
                .findFirst().map(Handover::getId).orElse(null);
        return new Summary(
                lostItemRepository.countMine(studentId, LostItemStatus.OPEN),
                foundItemRepository.countOngoingMine(studentId),
                claimRequestRepository.countMine(studentId, List.of(ClaimStatus.PENDING, ClaimStatus.APPROVED)),
                activeHandoverId);
    }

    public List<MyFoundItem> foundItems(String studentId) {
        List<FoundItem> items = foundItemRepository.findMine(studentId);
        if (items.isEmpty()) {
            return List.of();
        }
        List<Long> ids = items.stream().map(FoundItem::getId).toList();
        Map<Long, Long> pending = claimRequestRepository.countPendingByFoundItem(ids).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        Map<Long, Long> handovers = handoverRepository.findByFoundItemIds(ids).stream()
                .collect(Collectors.toMap(h -> h.getClaim().getFoundItem().getId(), Handover::getId));
        return items.stream().map(f -> new MyFoundItem(f.getId(), f.getItemName(), f.getCategory().getName(),
                f.getBuilding().getName(), f.getLocationDetail(), f.getFoundAt(), f.getImagePath(), f.getStatus(),
                pending.getOrDefault(f.getId(), 0L), handovers.get(f.getId()))).toList();
    }

    public List<MyClaim> claims(String studentId) {
        List<ClaimRequest> claims = claimRequestRepository.findAllMine(studentId);
        if (claims.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> handovers = handoverRepository.findByClaimIds(claims.stream().map(ClaimRequest::getId).toList())
                .stream().collect(Collectors.toMap(h -> h.getClaim().getId(), Handover::getId));
        return claims.stream().map(c -> new MyClaim(c.getId(), c.getStatus(), c.getRequestedAt(),
                new FoundItemBrief(c.getFoundItem().getId(), c.getFoundItem().getItemName(),
                        c.getFoundItem().getImagePath()),
                handovers.get(c.getId()))).toList();
    }
}
