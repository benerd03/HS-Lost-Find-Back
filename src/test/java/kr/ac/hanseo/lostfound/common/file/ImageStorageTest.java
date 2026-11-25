package kr.ac.hanseo.lostfound.common.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import kr.ac.hanseo.lostfound.common.config.AppProperties;
import kr.ac.hanseo.lostfound.common.error.BusinessException;
import kr.ac.hanseo.lostfound.common.error.ErrorCode;

class ImageStorageTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @TempDir
    Path dir;

    @Test
    void png를_UUID_이름으로_저장하고_삭제한다() {
        ImageStorage storage = new ImageStorage(new AppProperties(dir.toString(), true, "./data", false));

        String url = storage.store(new MockMultipartFile("image", "airpods.png", "image/png", PNG));

        assertThat(url).startsWith("/uploads/").endsWith(".png").doesNotContain("airpods");
        Path saved = dir.resolve(url.substring("/uploads/".length()));
        assertThat(Files.exists(saved)).isTrue();

        storage.delete(url);
        assertThat(Files.exists(saved)).isFalse();
    }

    @Test
    void 확장자만_png인_다른_파일은_IMAGE_INVALID() {
        ImageStorage storage = new ImageStorage(new AppProperties(dir.toString(), true, "./data", false));

        assertThatThrownBy(() -> storage.store(
                new MockMultipartFile("image", "fake.png", "image/png", "not an image".getBytes())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IMAGE_INVALID);
    }

    @Test
    void 빈_파일은_IMAGE_REQUIRED() {
        ImageStorage storage = new ImageStorage(new AppProperties(dir.toString(), true, "./data", false));

        assertThatThrownBy(() -> storage.store(new MockMultipartFile("image", "a.png", "image/png", new byte[0])))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IMAGE_REQUIRED);
    }
}
