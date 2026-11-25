package kr.ac.hanseo.lostfound.pathgraph;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import kr.ac.hanseo.lostfound.common.error.BusinessException;
import kr.ac.hanseo.lostfound.common.error.ErrorCode;

/**
 * data/path_nodes.csv, data/path_edges.csv를 읽고 검증한다 (양식은 docs/DATA_GUIDE.md).
 * 오류가 있으면 "파일명 N행: 이유" 메시지로 VALIDATION_FAILED를 던지고, 아무것도 바꾸지 않는다.
 */
public final class GraphCsvReader {

    public static final String NODES_FILE = "path_nodes.csv";
    public static final String EDGES_FILE = "path_edges.csv";

    private GraphCsvReader() {
    }

    public record NodeRow(String code, NodeType type, String buildingCode, BigDecimal lat, BigDecimal lng,
            BigDecimal elevationM, String memo) {
    }

    public record EdgeRow(String fromCode, String toCode, EdgeType type, BigDecimal distanceM,
            Integer forwardSeconds, Integer backwardSeconds, String memo) {
    }

    public record GraphData(List<NodeRow> nodes, List<EdgeRow> edges) {
    }

    public static boolean exists(Path dataDir) {
        return Files.exists(dataDir.resolve(NODES_FILE)) && Files.exists(dataDir.resolve(EDGES_FILE));
    }

    /** @param buildingIdsByCode 존재하는 건물 코드 (ENTRANCE의 building_code 검증용) */
    public static GraphData read(Path dataDir, Map<String, Long> buildingIdsByCode) {
        List<NodeRow> nodes = readNodes(dataDir.resolve(NODES_FILE), buildingIdsByCode);
        Set<String> codes = new HashSet<>();
        for (NodeRow n : nodes) {
            codes.add(n.code());
        }
        List<EdgeRow> edges = readEdges(dataDir.resolve(EDGES_FILE), codes);
        return new GraphData(nodes, edges);
    }

    private static List<NodeRow> readNodes(Path file, Map<String, Long> buildingIdsByCode) {
        List<NodeRow> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CSVRecord r : parse(file)) {
            Line line = new Line(file, r);
            String code = line.required("node_code");
            if (code.length() > 30) {
                throw line.error("node_code는 30자 이내여야 합니다");
            }
            if (!seen.add(code)) {
                throw line.error("node_code가 중복됩니다: " + code);
            }
            NodeType type = line.enumValue("node_type", NodeType.class);
            String buildingCode = line.optional("building_code");
            if (type == NodeType.ENTRANCE) {
                if (buildingCode == null) {
                    throw line.error("ENTRANCE는 building_code가 필요합니다");
                }
                if (!buildingIdsByCode.containsKey(buildingCode)) {
                    throw line.error("buildings.csv에 없는 building_code입니다: " + buildingCode);
                }
            } else if (buildingCode != null) {
                throw line.error("JUNCTION은 building_code를 비워야 합니다");
            }
            BigDecimal lat = line.decimal("lat", true, -90, 90);
            BigDecimal lng = line.decimal("lng", true, -180, 180);
            BigDecimal elevation = line.decimal("elevation_m", false, -500, 9000);
            rows.add(new NodeRow(code, type, buildingCode, lat, lng, elevation, line.optional("memo")));
        }
        return rows;
    }

    private static List<EdgeRow> readEdges(Path file, Set<String> nodeCodes) {
        List<EdgeRow> rows = new ArrayList<>();
        Set<String> pairs = new HashSet<>();
        for (CSVRecord r : parse(file)) {
            Line line = new Line(file, r);
            String from = line.required("from_node_code");
            String to = line.required("to_node_code");
            for (String code : List.of(from, to)) {
                if (!nodeCodes.contains(code)) {
                    throw line.error("path_nodes.csv에 없는 node_code입니다: " + code);
                }
            }
            if (from.equals(to)) {
                throw line.error("같은 노드끼리 이을 수 없습니다: " + from);
            }
            String key = from.compareTo(to) < 0 ? from + "|" + to : to + "|" + from;
            if (!pairs.add(key)) {
                throw line.error("같은 길이 이미 있습니다(방향만 바꾼 줄 포함): " + from + " - " + to);
            }
            EdgeType type = line.optional("edge_type") == null ? EdgeType.PATH : line.enumValue("edge_type", EdgeType.class);
            BigDecimal distance = line.decimal("distance_m", false, 0.01, 99999);
            Integer forward = line.positiveInt("forward_seconds");
            Integer backward = line.positiveInt("backward_seconds");
            rows.add(new EdgeRow(from, to, type, distance, forward, backward, line.optional("memo")));
        }
        return rows;
    }

    private static List<CSVRecord> parse(Path file) {
        try {
            // 엑셀에서 저장한 CSV의 BOM 제거
            String text = Files.readString(file, StandardCharsets.UTF_8).replace("﻿", "");
            CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true)
                    .setIgnoreEmptyLines(true).get();
            try (Reader reader = new StringReader(text)) {
                return format.parse(reader).getRecords();
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    file.getFileName() + ": 파일을 읽을 수 없습니다 (" + e.getMessage() + ")");
        }
    }

    /** 한 행. 오류 메시지에 파일명과 행 번호(머리글이 1행)를 붙인다 */
    private record Line(Path file, CSVRecord record) {

        BusinessException error(String reason) {
            return new BusinessException(ErrorCode.VALIDATION_FAILED,
                    file.getFileName() + " " + (record.getRecordNumber() + 1) + "행: " + reason);
        }

        String optional(String column) {
            if (!record.isMapped(column)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        file.getFileName() + ": 머리글에 " + column + " 열이 없습니다");
            }
            String value = record.isSet(column) ? record.get(column) : null;
            return value == null || value.isBlank() ? null : value.trim();
        }

        String required(String column) {
            String value = optional(column);
            if (value == null) {
                throw error(column + "가 비어 있습니다");
            }
            return value;
        }

        <E extends Enum<E>> E enumValue(String column, Class<E> type) {
            String value = required(column);
            try {
                return Enum.valueOf(type, value.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw error(column + " 값이 올바르지 않습니다: " + value);
            }
        }

        BigDecimal decimal(String column, boolean required, double min, double max) {
            String value = required ? required(column) : optional(column);
            if (value == null) {
                return null;
            }
            try {
                BigDecimal number = new BigDecimal(value);
                if (number.doubleValue() < min || number.doubleValue() > max) {
                    throw error(column + " 값이 범위를 벗어났습니다: " + value);
                }
                return number;
            } catch (NumberFormatException e) {
                throw error(column + "가 숫자가 아닙니다: " + value);
            }
        }

        Integer positiveInt(String column) {
            String value = optional(column);
            if (value == null) {
                return null;
            }
            try {
                int number = Integer.parseInt(value);
                if (number <= 0) {
                    throw error(column + "는 1 이상이어야 합니다: " + value);
                }
                return number;
            } catch (NumberFormatException e) {
                throw error(column + "가 정수(초)가 아닙니다: " + value);
            }
        }
    }
}
