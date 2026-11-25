package kr.ac.hanseo.lostfound.founditem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

/**
 * FR-03 동적 다중 조건 검색. 넣은 조건만 AND로 건다.
 * - buildingId, categoryId: 같은 값
 * - keyword: 물품명 또는 설명 부분 일치 (대소문자 무시)
 * - 기본으로 삭제된 것과 수령완료(CLAIMED)는 뺀다
 */
public final class FoundItemSearch {

    private FoundItemSearch() {
    }

    public static Specification<FoundItem> of(Long buildingId, Long categoryId, String keyword) {
        return (root, query, cb) -> {
            // 목록 쿼리에서만 카테고리·건물을 함께 읽어 N+1을 막는다 (count 쿼리에는 fetch를 걸면 안 됨)
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("category", JoinType.INNER);
                root.fetch("building", JoinType.INNER);
            }
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.isNull(root.get("deletedAt")));
            conditions.add(cb.notEqual(root.get("status"), FoundItemStatus.CLAIMED));
            if (buildingId != null) {
                conditions.add(cb.equal(root.get("building").get("id"), buildingId));
            }
            if (categoryId != null) {
                conditions.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
                conditions.add(cb.or(
                        cb.like(cb.lower(root.get("itemName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }

    /** 사용자가 입력한 %, _ 를 글자 그대로 찾도록 */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
