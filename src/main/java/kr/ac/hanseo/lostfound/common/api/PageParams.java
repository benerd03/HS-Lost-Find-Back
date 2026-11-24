package kr.ac.hanseo.lostfound.common.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import kr.ac.hanseo.lostfound.common.error.BusinessException;
import kr.ac.hanseo.lostfound.common.error.ErrorCode;

/** ?page=0&size=20 검증. size는 1~100. */
public final class PageParams {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageParams() {
    }

    public static Pageable of(Integer page, Integer size, Sort sort) {
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : size;
        if (p < 0 || s < 1 || s > MAX_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "page는 0 이상, size는 1~" + MAX_SIZE + "이어야 합니다.");
        }
        return PageRequest.of(p, s, sort);
    }
}
