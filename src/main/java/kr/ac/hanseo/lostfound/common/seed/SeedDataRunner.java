package kr.ac.hanseo.lostfound.common.seed;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.hanseo.lostfound.catalog.Building;
import kr.ac.hanseo.lostfound.catalog.BuildingRepository;
import kr.ac.hanseo.lostfound.catalog.Category;
import kr.ac.hanseo.lostfound.catalog.CategoryRepository;
import kr.ac.hanseo.lostfound.common.config.AppProperties;
import kr.ac.hanseo.lostfound.user.Admin;
import kr.ac.hanseo.lostfound.user.AdminRepository;
import kr.ac.hanseo.lostfound.user.Student;
import kr.ac.hanseo.lostfound.user.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 개발·시연용 기본 데이터. app.seed-enabled=true이고 테이블이 비어 있을 때만 넣는다.
 * - 카테고리 5개 (11/22 확정)
 * - 관리자 계정 (회원가입 없음)
 * - 테스트 학생 2명
 * - 건물: data/buildings.csv (없으면 data/templates/buildings.csv 예시)
 * 보행 그래프(노드·간선)는 pathgraph 기능에서 따로 읽는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeedDataRunner implements ApplicationRunner {

    public static final String ADMIN_ID = "admin";
    public static final String ADMIN_PASSWORD = "admin1234";
    public static final String TEST_PASSWORD = "test1234";

    private final AppProperties properties;
    private final CategoryRepository categoryRepository;
    private final BuildingRepository buildingRepository;
    private final AdminRepository adminRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (!properties.seedEnabled()) {
            return;
        }
        if (categoryRepository.count() == 0) {
            List<String> names = List.of("전자기기", "지갑·카드", "가방", "의류·잡화", "기타 물품");
            for (int i = 0; i < names.size(); i++) {
                categoryRepository.save(new Category(names.get(i), i + 1));
            }
            log.info("[seed] 카테고리 {}개", names.size());
        }
        if (adminRepository.count() == 0) {
            adminRepository.save(new Admin(ADMIN_ID, passwordEncoder.encode(ADMIN_PASSWORD), "관리자", "학생지원팀"));
            log.info("[seed] 관리자 계정 {} (시연 전에 비밀번호를 바꿀 것)", ADMIN_ID);
        }
        if (studentRepository.count() == 0) {
            String hash = passwordEncoder.encode(TEST_PASSWORD);
            studentRepository.save(new Student("20210001", hash, "테스트학생A", "무인항공기학과", "010-1111-0001"));
            studentRepository.save(new Student("20210002", hash, "테스트학생B", "컴퓨터공학과", "010-1111-0002"));
            log.info("[seed] 테스트 학생 20210001, 20210002");
        }
        if (buildingRepository.count() == 0) {
            seedBuildings();
        }
    }

    private void seedBuildings() throws IOException {
        Path real = Path.of(properties.dataDir(), "buildings.csv");
        Path file = Files.exists(real) ? real : Path.of(properties.dataDir(), "templates", "buildings.csv");
        if (!Files.exists(file)) {
            log.warn("[seed] 건물 CSV가 없습니다: {}", real.toAbsolutePath());
            return;
        }
        if (file != real) {
            log.warn("[seed] data/buildings.csv가 없어 예시 파일({})로 건물을 넣습니다", file);
        }
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).get();
        int count = 0;
        // 엑셀에서 저장한 CSV의 BOM 제거
        String text = Files.readString(file, StandardCharsets.UTF_8).replace("\uFEFF", "");
        try (Reader reader = new StringReader(text)) {
            for (CSVRecord row : format.parse(reader)) {
                buildingRepository.save(new Building(
                        row.get("building_code"),
                        row.get("name"),
                        decimalOrNull(row.get("lat")),
                        decimalOrNull(row.get("lng")),
                        Integer.parseInt(row.get("sort_order"))));
                count++;
            }
        }
        log.info("[seed] 건물 {}개 ({})", count, file);
    }

    private static BigDecimal decimalOrNull(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }
}
