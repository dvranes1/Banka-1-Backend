package com.banka1.stock_service.service;

import com.banka1.stock_service.config.StockExchangeSeedProperties;
import com.banka1.stock_service.dto.StockExchangeImportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Startup runner that seeds stock exchange reference data from the configured CSV file.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeedRunner implements ApplicationRunner {

    private final CsvImportService stockExchangeCsvImportService;
    private final StockExchangeSeedProperties stockExchangeSeedProperties;

    /**
     * Imports stock exchanges on startup when the feature is enabled.
     *
     * @param args application startup arguments
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!stockExchangeSeedProperties.enabled()) {
            log.info("Stock exchange CSV seeding is disabled.");
            return;
        }

        StockExchangeImportResponse importResponse = stockExchangeCsvImportService.importFromConfiguredCsv();
        log.info(
                "Stock exchanges imported from {}. processedRows={}, createdCount={}, updatedCount={}, unchangedCount={}",
                importResponse.source(),
                importResponse.processedRows(),
                importResponse.createdCount(),
                importResponse.updatedCount(),
                importResponse.unchangedCount()
        );
    }
}
