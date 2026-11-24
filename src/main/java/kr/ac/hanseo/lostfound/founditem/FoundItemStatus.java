package kr.ac.hanseo.lostfound.founditem;

/** 습득물 상태: 보관중 → 수령신청 → 수령대기 → 수령완료 */
public enum FoundItemStatus {
    STORED, CLAIM_REQUESTED, AWAITING_PICKUP, CLAIMED
}
