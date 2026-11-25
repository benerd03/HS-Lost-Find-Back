package kr.ac.hanseo.lostfound.admin;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.JoinType;
import kr.ac.hanseo.lostfound.admin.AdminResponses.ClaimBundle;
import kr.ac.hanseo.lostfound.admin.AdminResponses.ClaimRow;
import kr.ac.hanseo.lostfound.admin.AdminResponses.FoundItemDetail;
import kr.ac.hanseo.lostfound.admin.AdminResponses.FoundItemRow;
import kr.ac.hanseo.lostfound.claim.ClaimRequest;
import kr.ac.hanseo.lostfound.claim.ClaimRequestRepository;
import kr.ac.hanseo.lostfound.claim.ClaimStatus;
import kr.ac.hanseo.lostfound.common.error.BusinessException;
import kr.ac.hanseo.lostfound.common.error.ErrorCode;
import kr.ac.hanseo.lostfound.founditem.FoundItem;
import kr.ac.hanseo.lostfound.founditem.FoundItemRepository;
import kr.ac.hanseo.lostfound.founditem.FoundItemResponses;
import kr.ac.hanseo.lostfound.founditem.FoundItemStatus;
import kr.ac.hanseo.lostfound.handover.Handover;
import kr.ac.hanseo.lostfound.handover.HandoverRepository;
import lombok.RequiredArgsConstructor;

/** 관리자 조회: A2 습득물 목록·상세, A3 습득물별 신청 묶음 (모두 읽기 전용) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminFoundItemService {

    private final FoundItemRepository foundItemRepository;
    private final ClaimRequestRepository claimRequestRepository;
    private final HandoverRepository handoverRepository;

    /** A2 목록. status 생략 시 전체(수령완료 포함), 삭제 제외, 최신순 */
    public Page<FoundItemRow> foundItems(FoundItemStatus status, Pageable pageable) {
        Specification<FoundItem> spec = (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("category", JoinType.INNER);
                root.fetch("building", JoinType.INNER);
            }
            var notDeleted = cb.isNull(root.get("deletedAt"));
            return status == null ? notDeleted : cb.and(notDeleted, cb.equal(root.get("status"), status));
        };
        Page<FoundItem> page = foundItemRepository.findAll(spec, pageable);
        Map<Long, Long> counts = claimCounts(page.getContent().stream().map(FoundItem::getId).toList());
        return page.map(f -> FoundItemRow.of(f, counts.getOrDefault(f.getId(), 0L)));
    }

    public FoundItemDetail foundItem(Long id) {
        FoundItem item = findActive(id);
        return FoundItemDetail.of(item, claimCounts(List.of(id)).getOrDefault(id, 0L));
    }

    /**
     * A3 신청 묶음. 지정한 상태(기본 PENDING)의 신청이 있는 습득물만, 그 신청이 가장 오래된 습득물부터.
     * 묶음 안의 claims에는 그 습득물의 모든 신청을 오래된 순으로 넣는다 (대기·거절·취소 이력을 함께 보기 위함).
     */
    public List<ClaimBundle> bundles(ClaimStatus claimStatus, Long foundItemId) {
        List<Long> ids = claimRequestRepository.findFoundItemIdsWithStatus(
                claimStatus == null ? ClaimStatus.PENDING : claimStatus, foundItemId);
        return buildBundles(ids);
    }

    /** 습득물 1건의 묶음. 신청이 없어도 빈 claims로 보낸다 */
    public ClaimBundle bundleOf(Long foundItemId) {
        findActive(foundItemId);
        return buildBundles(List.of(foundItemId)).get(0);
    }

    private List<ClaimBundle> buildBundles(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, FoundItem> items = foundItemRepository.findAllWithDetailsByIdIn(ids).stream()
                .collect(Collectors.toMap(FoundItem::getId, f -> f));
        Map<Long, List<ClaimRequest>> claims = claimRequestRepository.findAllForAdmin(ids).stream()
                .collect(Collectors.groupingBy(c -> c.getFoundItem().getId()));
        Map<Long, Long> handovers = handoverRepository.findByFoundItemIds(ids).stream()
                .collect(Collectors.toMap(h -> h.getClaim().getFoundItem().getId(), Handover::getId));
        return ids.stream().map(id -> new ClaimBundle(
                FoundItemResponses.Summary.from(items.get(id)),
                handovers.get(id),
                claims.getOrDefault(id, List.of()).stream().map(ClaimRow::from).toList())).toList();
    }

    private FoundItem findActive(Long id) {
        return foundItemRepository.findActiveById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private Map<Long, Long> claimCounts(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return claimRequestRepository.countAllByFoundItem(ids).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
    }
}
