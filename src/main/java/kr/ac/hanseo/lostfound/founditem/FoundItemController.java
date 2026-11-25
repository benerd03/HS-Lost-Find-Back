package kr.ac.hanseo.lostfound.founditem;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import kr.ac.hanseo.lostfound.common.api.ApiResponse;
import kr.ac.hanseo.lostfound.common.api.PageParams;
import kr.ac.hanseo.lostfound.common.api.PageResponse;
import kr.ac.hanseo.lostfound.common.auth.LoginUser;
import kr.ac.hanseo.lostfound.common.auth.SessionUser;
import kr.ac.hanseo.lostfound.common.auth.StudentOnly;
import kr.ac.hanseo.lostfound.founditem.FoundItemResponses.Created;
import kr.ac.hanseo.lostfound.founditem.FoundItemResponses.Detail;
import kr.ac.hanseo.lostfound.founditem.FoundItemResponses.Summary;
import lombok.RequiredArgsConstructor;

/** 습득물 (명세 "습득물" 절). 목록·상세는 로그인한 누구나, 등록·수정·삭제는 학생만 */
@RestController
@RequestMapping("/api/found-items")
@RequiredArgsConstructor
public class FoundItemController {

    /** 최신 등록순, 같은 시각이면 ID가 큰 것 먼저 */
    private static final Sort LATEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final FoundItemService foundItemService;

    @StudentOnly
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Created> create(@Valid @ModelAttribute FoundItemForm form,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @LoginUser SessionUser user) {
        return ApiResponse.ok(new Created(foundItemService.create(form, image, user)));
    }

    /** 동적 검색. 빈 문자열 파라미터는 보내지 않은 것과 같다 */
    @GetMapping
    public ApiResponse<PageResponse<Summary>> search(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var result = foundItemService.search(buildingId, categoryId, keyword, PageParams.of(page, size, LATEST));
        return ApiResponse.ok(PageResponse.of(result, s -> s));
    }

    @GetMapping("/{id}")
    public ApiResponse<Detail> detail(@PathVariable Long id, @LoginUser SessionUser user) {
        return ApiResponse.ok(foundItemService.detail(id, user));
    }

    @StudentOnly
    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @ModelAttribute FoundItemForm form,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @LoginUser SessionUser user) {
        foundItemService.update(id, form, image, user);
        return ApiResponse.ok();
    }

    @StudentOnly
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @LoginUser SessionUser user) {
        foundItemService.delete(id, user);
        return ApiResponse.ok();
    }
}
