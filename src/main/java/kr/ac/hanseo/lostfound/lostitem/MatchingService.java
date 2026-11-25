package kr.ac.hanseo.lostfound.lostitem;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import kr.ac.hanseo.lostfound.claim.ClaimRequest;
import kr.ac.hanseo.lostfound.claim.ClaimRequestRepository;
import kr.ac.hanseo.lostfound.claim.ClaimStatus;
import kr.ac.hanseo.lostfound.founditem.ClaimEligibility;
import kr.ac.hanseo.lostfound.founditem.FoundItem;
import kr.ac.hanseo.lostfound.founditem.FoundItemStatus;
import kr.ac.hanseo.lostfound.lostitem.MatchCandidate.Reason;
import lombok.RequiredArgsConstructor;

/**
 * FR-04 분실 신고 → 유사 습득물 Top 3 (명세 "매칭 규칙")
 * 1. 걸러내기(SQL): 습득 일시가 분실 일시 ±3일, STORED·CLAIM_REQUESTED, 내가 등록한 것 제외, 삭제 제외
 * 2. 점수(SQL의 CASE 식): 카테고리 +30, 건물 +30("기타"끼리는 0), 물품명 부분 일치(양방향) +30, 설명에 분실 물품명 +10
 * 3. 정렬: 점수 ↓, 분실 일시와의 시간 차이 ↑, foundItemId ↓. 점수 0은 빼고 상위 3건
 * reasons는 점수를 계산한 같은 쿼리 결과로 만들므로 score와 항상 일치한다.
 */
@Service
@RequiredArgsConstructor
public class MatchingService {

    public static final int CATEGORY_POINTS = 30;
    public static final int BUILDING_POINTS = 30;
    public static final int NAME_POINTS = 30;
    public static final int DESCRIPTION_POINTS = 10;
    public static final int WINDOW_DAYS = 3;
    public static final int LIMIT = 3;
    /** "기타" 건물의 building_code. 기타끼리는 건물 점수를 주지 않는다 */
    public static final String ETC_BUILDING_CODE = "ETC";

    private static final String SCORE_QUERY = """
            select f,
                   case when c.id = :categoryId then %d else 0 end,
                   case when b.id = :buildingId and b.code <> :etcCode then %d else 0 end,
                   case when locate(:name, lower(f.itemName)) > 0 or locate(lower(f.itemName), :name) > 0
                        then %d else 0 end,
                   case when f.description is not null and locate(:name, lower(f.description)) > 0
                        then %d else 0 end
            from FoundItem f join fetch f.category c join fetch f.building b
            where f.deletedAt is null
              and f.status in :statuses
              and f.finder.studentId <> :reporterId
              and f.foundAt between :from and :to
            """.formatted(CATEGORY_POINTS, BUILDING_POINTS, NAME_POINTS, DESCRIPTION_POINTS);

    private final EntityManager entityManager;
    private final ClaimRequestRepository claimRequestRepository;

    @Transactional(readOnly = true)
    public List<MatchCandidate> topMatches(LostItem lost) {
        String name = lost.getItemName().trim().toLowerCase(Locale.ROOT);
        LocalDateTime lostAt = lost.getLostAt();
        String reporterId = lost.getStudent().getStudentId();

        List<Object[]> rows = entityManager.createQuery(SCORE_QUERY, Object[].class)
                .setParameter("categoryId", lost.getCategory().getId())
                .setParameter("buildingId", lost.getBuilding().getId())
                .setParameter("etcCode", ETC_BUILDING_CODE)
                .setParameter("name", name)
                .setParameter("statuses", List.of(FoundItemStatus.STORED, FoundItemStatus.CLAIM_REQUESTED))
                .setParameter("reporterId", reporterId)
                .setParameter("from", lostAt.minusDays(WINDOW_DAYS))
                .setParameter("to", lostAt.plusDays(WINDOW_DAYS))
                .getResultList();

        List<Scored> top = rows.stream()
                .map(row -> new Scored((FoundItem) row[0], reasons(lost.getItemName().trim(), row)))
                .filter(s -> s.score() > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed()
                        .thenComparing(s -> Duration.between(lostAt, s.item().getFoundAt()).abs())
                        .thenComparing(s -> s.item().getId(), Comparator.reverseOrder()))
                .limit(LIMIT)
                .toList();
        if (top.isEmpty()) {
            return List.of();
        }

        Map<Long, ClaimStatus> myClaims = claimRequestRepository
                .findMineIn(reporterId, top.stream().map(s -> s.item().getId()).toList()).stream()
                .collect(Collectors.toMap(c -> c.getFoundItem().getId(), ClaimRequest::getStatus));

        return top.stream().map(s -> toCandidate(s, myClaims.get(s.item().getId()))).toList();
    }

    private static List<Reason> reasons(String lostName, Object[] row) {
        List<Reason> reasons = new ArrayList<>();
        addIfScored(reasons, "CATEGORY", "카테고리 일치", row[1]);
        addIfScored(reasons, "BUILDING", "건물 일치", row[2]);
        addIfScored(reasons, "NAME", "‘" + lostName + "’ 부분 일치", row[3]);
        addIfScored(reasons, "DESCRIPTION", "설명에 ‘" + lostName + "’ 포함", row[4]);
        return reasons;
    }

    private static void addIfScored(List<Reason> reasons, String code, String label, Object points) {
        int value = ((Number) points).intValue();
        if (value > 0) {
            reasons.add(new Reason(code, label, value));
        }
    }

    private static MatchCandidate toCandidate(Scored scored, ClaimStatus myClaimStatus) {
        FoundItem f = scored.item();
        return new MatchCandidate(f.getId(), f.getItemName(), f.getCategory().getId(), f.getCategory().getName(),
                f.getBuilding().getName(), f.getLocationDetail(), f.getFoundAt(), f.getImagePath(), f.getStatus(),
                ClaimEligibility.of(false, myClaimStatus, f.getStatus()), scored.score(), scored.reasons());
    }

    private record Scored(FoundItem item, List<Reason> reasons) {
        int score() {
            return reasons.stream().mapToInt(Reason::points).sum();
        }
    }

}
