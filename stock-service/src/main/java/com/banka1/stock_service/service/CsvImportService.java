package com.banka1.stock_service.service;

import com.banka1.stock_service.config.StockExchangeSeedProperties;
import com.banka1.stock_service.domain.StockExchange;
import com.banka1.stock_service.dto.StockExchangeImportResponse;
import com.banka1.stock_service.repository.StockExchangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Imports stock exchange reference data from a CSV file and upserts it into the database.
 * The import is idempotent and keyed by the exchange MIC code.
 */
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");
    private static final List<String> REQUIRED_HEADERS = List.of(
            "Exchange Name",
            "Acronym",
            "MIC Code",
            "Polity",
            "Currency",
            "Time Zone",
            "Open Time",
            "Close Time",
            "Pre Market Open Time",
            "Pre Market Close Time",
            "Post Market Open Time",
            "Post Market Close Time",
            "Is Active"
    );

    private final StockExchangeRepository stockExchangeRepository;
    private final StockExchangeSeedProperties stockExchangeSeedProperties;
    private final ResourceLoader resourceLoader;

    /**
     * Imports the configured CSV source from application properties.
     *
     * @return import summary
     */
    @Transactional
    public StockExchangeImportResponse importFromConfiguredCsv() {
        return importFromLocation(stockExchangeSeedProperties.csvLocation());
    }

    /**
     * Imports stock exchanges from the provided Spring resource location.
     *
     * @param csvLocation Spring resource location, for example {@code classpath:seed/stock-exchanges.csv}
     * @return import summary
     */
    @Transactional
    public StockExchangeImportResponse importFromLocation(String csvLocation) {
        Resource resource = resourceLoader.getResource(csvLocation);
        return importFromResource(resource, csvLocation);
    }

    /**
     * Imports stock exchanges from the provided resource.
     *
     * @param resource CSV resource
     * @param source source label used in the response
     * @return import summary
     */
    @Transactional
    public StockExchangeImportResponse importFromResource(Resource resource, String source) {
        List<StockExchangeCsvRow> rows = parseCsv(resource, source);
        return persistRows(rows, source);
    }

    private StockExchangeImportResponse persistRows(List<StockExchangeCsvRow> rows, String source) {
        Collection<String> micCodes = rows.stream()
                .map(StockExchangeCsvRow::exchangeMICCode)
                .toList();

        Map<String, StockExchange> existingByMicCode = stockExchangeRepository.findAllByExchangeMICCodeIn(micCodes)
                .stream()
                .collect(Collectors.toMap(StockExchange::getExchangeMICCode, Function.identity()));

        List<StockExchange> entitiesToPersist = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;
        int unchangedCount = 0;

        for (StockExchangeCsvRow row : rows) {
            StockExchange existingEntity = existingByMicCode.get(row.exchangeMICCode());
            if (existingEntity == null) {
                StockExchange newEntity = new StockExchange();
                applyRow(newEntity, row);
                entitiesToPersist.add(newEntity);
                createdCount++;
                continue;
            }

            if (applyRowIfChanged(existingEntity, row)) {
                entitiesToPersist.add(existingEntity);
                updatedCount++;
                continue;
            }

            unchangedCount++;
        }

        if (!entitiesToPersist.isEmpty()) {
            stockExchangeRepository.saveAll(entitiesToPersist);
        }

        return new StockExchangeImportResponse(
                source,
                rows.size(),
                createdCount,
                updatedCount,
                unchangedCount
        );
    }

    private List<StockExchangeCsvRow> parseCsv(Resource resource, String source) {
        if (!resource.exists()) {
            throw new IllegalStateException("Stock exchange CSV resource does not exist: " + source);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalArgumentException("Stock exchange CSV is empty: " + source);
            }

            List<String> headerValues = parseCsvLine(headerLine, 1, source);
            Map<String, Integer> headerIndexes = indexHeaders(headerValues, source);
            validateHeaders(headerIndexes, source);

            List<StockExchangeCsvRow> rows = new ArrayList<>();
            Set<String> micCodes = new HashSet<>();
            int lineNumber = 1;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }

                List<String> values = parseCsvLine(line, lineNumber, source);
                if (values.stream().allMatch(String::isBlank)) {
                    continue;
                }

                if (values.size() != headerValues.size()) {
                    throw new IllegalArgumentException(
                            "CSV row " + lineNumber + " in " + source + " has " + values.size()
                                    + " columns, expected " + headerValues.size()
                    );
                }

                StockExchangeCsvRow row = mapRow(values, headerIndexes, lineNumber, source);
                if (!micCodes.add(row.exchangeMICCode())) {
                    throw new IllegalArgumentException(
                            "Duplicate MIC code '" + row.exchangeMICCode() + "' found in " + source
                                    + " on row " + lineNumber
                    );
                }
                rows.add(row);
            }

            return rows;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read stock exchange CSV resource: " + source, exception);
        }
    }

    private Map<String, Integer> indexHeaders(List<String> headers, String source) {
        Map<String, Integer> headerIndexes = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String normalizedHeader = headers.get(i).trim();
            if (headerIndexes.putIfAbsent(normalizedHeader, i) != null) {
                throw new IllegalArgumentException("Duplicate CSV header '" + normalizedHeader + "' in " + source);
            }
        }
        return headerIndexes;
    }

    private void validateHeaders(Map<String, Integer> headerIndexes, String source) {
        for (String requiredHeader : REQUIRED_HEADERS) {
            if (!headerIndexes.containsKey(requiredHeader)) {
                throw new IllegalArgumentException(
                        "Missing required CSV header '" + requiredHeader + "' in " + source
                );
            }
        }
    }

    private StockExchangeCsvRow mapRow(
            List<String> values,
            Map<String, Integer> headerIndexes,
            int lineNumber,
            String source
    ) {
        return new StockExchangeCsvRow(
                requiredValue(values, headerIndexes, "Exchange Name", lineNumber, source),
                requiredValue(values, headerIndexes, "Acronym", lineNumber, source),
                requiredValue(values, headerIndexes, "MIC Code", lineNumber, source),
                requiredValue(values, headerIndexes, "Polity", lineNumber, source),
                requiredValue(values, headerIndexes, "Currency", lineNumber, source),
                requiredValue(values, headerIndexes, "Time Zone", lineNumber, source),
                parseTime(requiredValue(values, headerIndexes, "Open Time", lineNumber, source), "Open Time", lineNumber, source),
                parseTime(requiredValue(values, headerIndexes, "Close Time", lineNumber, source), "Close Time", lineNumber, source),
                parseOptionalTime(optionalValue(values, headerIndexes, "Pre Market Open Time"), "Pre Market Open Time", lineNumber, source),
                parseOptionalTime(optionalValue(values, headerIndexes, "Pre Market Close Time"), "Pre Market Close Time", lineNumber, source),
                parseOptionalTime(optionalValue(values, headerIndexes, "Post Market Open Time"), "Post Market Open Time", lineNumber, source),
                parseOptionalTime(optionalValue(values, headerIndexes, "Post Market Close Time"), "Post Market Close Time", lineNumber, source),
                parseBoolean(requiredValue(values, headerIndexes, "Is Active", lineNumber, source), lineNumber, source)
        );
    }

    private String requiredValue(
            List<String> values,
            Map<String, Integer> headerIndexes,
            String header,
            int lineNumber,
            String source
    ) {
        String value = optionalValue(values, headerIndexes, header);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing value for column '" + header + "' on row " + lineNumber + " in " + source
            );
        }
        return value;
    }

    private String optionalValue(List<String> values, Map<String, Integer> headerIndexes, String header) {
        Integer index = headerIndexes.get(header);
        if (index == null || index >= values.size()) {
            return null;
        }
        return values.get(index).trim();
    }

    private LocalTime parseTime(String rawValue, String header, int lineNumber, String source) {
        try {
            return LocalTime.parse(rawValue, TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Invalid time value '" + rawValue + "' for column '" + header + "' on row "
                            + lineNumber + " in " + source + ". Expected HH:mm format.",
                    exception
            );
        }
    }

    private LocalTime parseOptionalTime(String rawValue, String header, int lineNumber, String source) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return parseTime(rawValue, header, lineNumber, source);
    }

    private boolean parseBoolean(String rawValue, int lineNumber, String source) {
        return switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes" -> true;
            case "false", "0", "no" -> false;
            default -> throw new IllegalArgumentException(
                    "Invalid boolean value '" + rawValue + "' on row " + lineNumber + " in " + source
            );
        };
    }

    private List<String> parseCsvLine(String line, int lineNumber, String source) {
        List<String> values = new ArrayList<>();
        StringBuilder currentValue = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char currentCharacter = line.charAt(i);

            if (currentCharacter == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    currentValue.append('"');
                    i++;
                    continue;
                }

                inQuotes = !inQuotes;
                continue;
            }

            if (currentCharacter == ',' && !inQuotes) {
                values.add(currentValue.toString().trim());
                currentValue.setLength(0);
                continue;
            }

            currentValue.append(currentCharacter);
        }

        if (inQuotes) {
            throw new IllegalArgumentException("Unclosed quoted CSV value on row " + lineNumber + " in " + source);
        }

        values.add(currentValue.toString().trim());
        return values;
    }

    private void applyRow(StockExchange entity, StockExchangeCsvRow row) {
        entity.setExchangeName(row.exchangeName());
        entity.setExchangeAcronym(row.exchangeAcronym());
        entity.setExchangeMICCode(row.exchangeMICCode());
        entity.setPolity(row.polity());
        entity.setCurrency(row.currency());
        entity.setTimeZone(row.timeZone());
        entity.setOpenTime(row.openTime());
        entity.setCloseTime(row.closeTime());
        entity.setPreMarketOpenTime(row.preMarketOpenTime());
        entity.setPreMarketCloseTime(row.preMarketCloseTime());
        entity.setPostMarketOpenTime(row.postMarketOpenTime());
        entity.setPostMarketCloseTime(row.postMarketCloseTime());
        entity.setIsActive(row.isActive());
    }

    private boolean applyRowIfChanged(StockExchange entity, StockExchangeCsvRow row) {
        if (matches(entity, row)) {
            return false;
        }

        applyRow(entity, row);
        return true;
    }

    private boolean matches(StockExchange entity, StockExchangeCsvRow row) {
        return Objects.equals(entity.getExchangeName(), row.exchangeName())
                && Objects.equals(entity.getExchangeAcronym(), row.exchangeAcronym())
                && Objects.equals(entity.getExchangeMICCode(), row.exchangeMICCode())
                && Objects.equals(entity.getPolity(), row.polity())
                && Objects.equals(entity.getCurrency(), row.currency())
                && Objects.equals(entity.getTimeZone(), row.timeZone())
                && Objects.equals(entity.getOpenTime(), row.openTime())
                && Objects.equals(entity.getCloseTime(), row.closeTime())
                && Objects.equals(entity.getPreMarketOpenTime(), row.preMarketOpenTime())
                && Objects.equals(entity.getPreMarketCloseTime(), row.preMarketCloseTime())
                && Objects.equals(entity.getPostMarketOpenTime(), row.postMarketOpenTime())
                && Objects.equals(entity.getPostMarketCloseTime(), row.postMarketCloseTime())
                && Objects.equals(entity.getIsActive(), row.isActive());
    }

    private record StockExchangeCsvRow(
            String exchangeName,
            String exchangeAcronym,
            String exchangeMICCode,
            String polity,
            String currency,
            String timeZone,
            LocalTime openTime,
            LocalTime closeTime,
            LocalTime preMarketOpenTime,
            LocalTime preMarketCloseTime,
            LocalTime postMarketOpenTime,
            LocalTime postMarketCloseTime,
            Boolean isActive
    ) {
    }
}
