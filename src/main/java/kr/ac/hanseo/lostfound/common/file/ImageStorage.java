package kr.ac.hanseo.lostfound.common.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import kr.ac.hanseo.lostfound.common.config.AppProperties;
import kr.ac.hanseo.lostfound.common.error.BusinessException;
import kr.ac.hanseo.lostfound.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;

/**
 * 습득물 이미지 저장 (명세 공통 규칙 "이미지", "업로드 검증")
 * - jpg·png만, 5MB 이하. 확장자, Content-Type, 파일 앞부분 바이트(매직 넘버)를 모두 검사
 * - 저장 파일명은 UUID. DB에는 "/uploads/파일명" 경로만 저장
 */
@Slf4j
@Component
public class ImageStorage {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final String URL_PREFIX = "/uploads/";

    private final Path root;

    public ImageStorage(AppProperties properties) {
        this.root = Path.of(properties.uploadDir()).toAbsolutePath().normalize();
    }

    /** 저장하고 imageUrl(/uploads/uuid.jpg)을 돌려준다 */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.IMAGE_REQUIRED);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.IMAGE_INVALID);
        }
        ImageType type = detect(file);
        String fileName = UUID.randomUUID() + "." + type.extension;
        try {
            Files.createDirectories(root);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, root.resolve(fileName));
            }
        } catch (IOException e) {
            throw new IllegalStateException("이미지 저장 실패", e);
        }
        return URL_PREFIX + fileName;
    }

    /** imageUrl로 파일 삭제. 없으면 무시 (습득물 삭제·이미지 교체 시) */
    public void delete(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(URL_PREFIX)) {
            return;
        }
        Path target = root.resolve(imageUrl.substring(URL_PREFIX.length())).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("이미지 삭제 실패: {}", imageUrl, e);
        }
    }

    private static ImageType detect(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        byte[] head = new byte[8];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.IMAGE_INVALID);
        }
        boolean jpegBytes = read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
        boolean pngBytes = read >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';

        if (jpegBytes && (name.endsWith(".jpg") || name.endsWith(".jpeg")) && contentType.equals("image/jpeg")) {
            return ImageType.JPG;
        }
        if (pngBytes && name.endsWith(".png") && contentType.equals("image/png")) {
            return ImageType.PNG;
        }
        throw new BusinessException(ErrorCode.IMAGE_INVALID);
    }

    private enum ImageType {
        JPG("jpg"), PNG("png");

        private final String extension;

        ImageType(String extension) {
            this.extension = extension;
        }
    }
}
