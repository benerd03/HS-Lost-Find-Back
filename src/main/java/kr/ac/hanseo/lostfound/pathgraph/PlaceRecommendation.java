package kr.ac.hanseo.lostfound.pathgraph;

import kr.ac.hanseo.lostfound.catalog.Building;

/**
 * 추천 결과. 도보 시간은 보행 그래프로 계산했을 때만 있다 (직선거리 방식이면 null).
 */
public record PlaceRecommendation(Building building, Integer finderWalkSeconds, Integer ownerWalkSeconds,
        Method method) {

    public enum Method {
        /** 캠퍼스 보행 그래프의 도보 시간 */
        WALK_GRAPH,
        /** 기획안의 직선거리 (그래프 데이터가 없을 때 대체) */
        STRAIGHT_LINE
    }
}
