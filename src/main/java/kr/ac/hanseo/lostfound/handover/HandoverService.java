package kr.ac.hanseo.lostfound.handover;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.hanseo.lostfound.claim.ClaimRequest;
import kr.ac.hanseo.lostfound.common.auth.SessionUser;
import kr.ac.hanseo.lostfound.common.config.AppProperties;
import kr.ac.hanseo.lostfound.common.error.BusinessException;
import kr.ac.hanseo.lostfound.common.error.ErrorCode;
import kr.ac.hanseo.lostfound.common.time.TimeProvider;
import kr.ac.hanseo.lostfound.founditem.FoundItemStatus;
import kr.ac.hanseo.lostfound.notification.LinkKind;
import kr.ac.hanseo.lostfound.notification.NotificationService;
import kr.ac.hanseo.lostfound.notification.NotificationType;
import kr.ac.hanseo.lostfound.pathgraph.GeoPoint;
import kr.ac.hanseo.lostfound.pathgraph.PlaceRecommendation;
import kr.ac.hanseo.lostfound.pathgraph.PlaceRecommendationService;
import kr.ac.hanseo.lostfound.user.Student;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 전달 진행 (FR-07). 습득자와 분실자 두 사람만 쓴다.
 * ready·reset·receive는 전달 행을 잠근 뒤 처리하므로, 두 사람이 거의 동시에 눌러도 건물 계산은 한 번만 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandoverService {

    /** "지금 이동 가능" 유지 시간 (11/18 확정) */
    public static final int READY_MINUTES = 30;
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final HandoverRepository handoverRepository;
    private final PlaceRecommendationService placeRecommendationService;
    private final NotificationService notificationService;
    private final TimeProvider timeProvider;
    private final AppProperties appProperties;

    /** 상태 조회 (5초 폴링). 만료된 사람의 임시 좌표는 이때 지운다 */
    @Transactional
    public HandoverView get(Long id, SessionUser user) {
        LocalDateTime now = timeProvider.now();
        handoverRepository.clearExpiredFinderCoordinates(id, now);
        handoverRepository.clearExpiredOwnerCoordinates(id, now);
        Handover h = handoverRepository.findWithDetailsById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return HandoverView.of(h, roleOf(h, user), now);
    }

    /** 내가 당사자인 진행 중(DONE 제외) 전달, 최근 생성순 */
    @Transactional(readOnly = true)
    public List<HandoverView> mine(SessionUser user) {
        LocalDateTime now = timeProvider.now();
        return handoverRepository.findOngoingOf(user.loginId(), Pageable.unpaged()).stream()
                .map(h -> HandoverView.of(h, roleOf(h, user), now))
                .toList();
    }

    /**
     * "지금 이동 가능". 상대도 이동 가능이면 이 요청 안에서 건물을 계산해 PLACE_SET으로 바꾸고 임시 좌표를 지운다.
     * WAITING에서 내 이동 가능이 아직 유효하면 HANDOVER_ALREADY_READY (연장 없음), PLACE_SET이면 HANDOVER_PLACE_SET.
     */
    @Transactional
    public HandoverView ready(Long id, ReadyRequest request, SessionUser user) {
        Handover h = lock(id);
        HandoverRole role = roleOf(h, user);
        LocalDateTime now = timeProvider.now();
        if (h.getStatus() == HandoverStatus.DONE) {
            throw new BusinessException(ErrorCode.HANDOVER_DONE);
        }
        if (h.getStatus() == HandoverStatus.PLACE_SET) {
            throw new BusinessException(ErrorCode.HANDOVER_PLACE_SET);
        }
        boolean finder = role == HandoverRole.FINDER;
        if (finder ? h.isFinderReady(now) : h.isOwnerReady(now)) {
            throw new BusinessException(ErrorCode.HANDOVER_ALREADY_READY);
        }
        GeoPoint here = resolveLocation(request);

        h.clearExpiredCoordinates(now);
        LocalDateTime until = now.plusMinutes(READY_MINUTES);
        BigDecimal lat = scale(here.lat());
        BigDecimal lng = scale(here.lng());
        if (finder) {
            h.markFinderReady(until, lat, lng);
        } else {
            h.markOwnerReady(until, lat, lng);
        }

        ClaimRequest claim = h.getClaim();
        Student finderStudent = claim.getFoundItem().getFinder();
        Student ownerStudent = claim.getStudent();
        notificationService.notify(finder ? ownerStudent : finderStudent, NotificationType.HANDOVER_READY,
                "상대가 " + now.format(HH_MM) + "에 이동 가능을 눌렀어요", LinkKind.HANDOVER, h.getId());

        if (h.isFinderReady(now) && h.isOwnerReady(now)) {
            Optional<PlaceRecommendation> place = placeRecommendationService.recommend(
                    GeoPoint.of(h.getFinderLat(), h.getFinderLng()), GeoPoint.of(h.getOwnerLat(), h.getOwnerLng()));
            if (place.isPresent()) {
                PlaceRecommendation p = place.get();
                h.setPlace(p.building(), p.finderWalkSeconds(), p.ownerWalkSeconds(), now);
                String message = p.building().getName() + "에서 만나세요";
                notificationService.notify(finderStudent, NotificationType.HANDOVER_PLACE_SET, message,
                        LinkKind.HANDOVER, h.getId());
                notificationService.notify(ownerStudent, NotificationType.HANDOVER_PLACE_SET, message,
                        LinkKind.HANDOVER, h.getId());
            } else {
                log.warn("추천할 건물이 없습니다. 건물 좌표 또는 보행 그래프 데이터를 확인하세요 (handover {})", id);
            }
        }
        return HandoverView.of(h, role, now);
    }

    /** 다시 정하기. PLACE_SET이면 두 사람 상태와 추천 건물을 모두 초기화, WAITING이면 내 이동 가능만 해제 */
    @Transactional
    public HandoverView reset(Long id, SessionUser user) {
        Handover h = lock(id);
        HandoverRole role = roleOf(h, user);
        if (h.getStatus() == HandoverStatus.DONE) {
            throw new BusinessException(ErrorCode.HANDOVER_DONE);
        }
        if (h.getStatus() == HandoverStatus.PLACE_SET) {
            h.reset();
        } else if (role == HandoverRole.FINDER) {
            h.clearFinderReady();
        } else {
            h.clearOwnerReady();
        }
        return HandoverView.of(h, role, timeProvider.now());
    }

    /** 받았어요 (분실자만). 추천 건물 없이 만나도 된다. 신청 COMPLETED, 습득물 CLAIMED, 습득자에게 알림 */
    @Transactional
    public HandoverView receive(Long id, SessionUser user) {
        Handover h = lock(id);
        HandoverRole role = roleOf(h, user);
        if (role != HandoverRole.OWNER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (h.getStatus() == HandoverStatus.DONE) {
            throw new BusinessException(ErrorCode.HANDOVER_DONE);
        }
        LocalDateTime now = timeProvider.now();
        ClaimRequest claim = h.getClaim();
        h.complete(now);
        claim.complete(now);
        claim.getFoundItem().changeStatus(FoundItemStatus.CLAIMED);
        notificationService.notify(claim.getFoundItem().getFinder(), NotificationType.HANDOVER_DONE,
                claim.getFoundItem().getItemName() + " 전달이 완료됐어요", LinkKind.HANDOVER, h.getId());
        return HandoverView.of(h, role, now);
    }

    /** 행을 잠그고, 같은 영속성 컨텍스트에서 화면에 필요한 연관까지 읽는다 */
    private Handover lock(Long id) {
        handoverRepository.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return handoverRepository.findWithDetailsById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private static HandoverRole roleOf(Handover h, SessionUser user) {
        ClaimRequest claim = h.getClaim();
        if (claim.getFoundItem().isFoundBy(user.loginId())) {
            return HandoverRole.FINDER;
        }
        if (claim.isRequestedBy(user.loginId())) {
            return HandoverRole.OWNER;
        }
        throw new BusinessException(ErrorCode.HANDOVER_NOT_PARTY);
    }

    /** GPS 좌표가 있으면 그것을, 없으면 시연 모드일 때만 고른 건물 위치를 쓴다 */
    private GeoPoint resolveLocation(ReadyRequest request) {
        if (request != null && request.lat() != null && request.lng() != null) {
            return new GeoPoint(request.lat(), request.lng());
        }
        if (request != null && request.lat() == null && request.lng() == null && request.buildingId() != null
                && appProperties.demoMode()) {
            return placeRecommendationService.locationOf(request.buildingId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.LOCATION_REQUIRED));
        }
        throw new BusinessException(ErrorCode.LOCATION_REQUIRED);
    }

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }
}
