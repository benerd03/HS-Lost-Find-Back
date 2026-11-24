package kr.ac.hanseo.lostfound.common.api;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** 페이지 응답: { items, page, size, totalCount }. 습득물 검색, 관리자 습득물 목록에만 쓴다. */
public record PageResponse<T>(List<T> items, int page, int size, long totalCount) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
