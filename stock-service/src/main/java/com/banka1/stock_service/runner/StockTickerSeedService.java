package com.banka1.stock_service.runner;

import com.banka1.stock_service.domain.Listing;
import com.banka1.stock_service.domain.ListingType;
import com.banka1.stock_service.domain.Stock;
import com.banka1.stock_service.domain.StockExchange;
import com.banka1.stock_service.dto.StockTickerSeedResponse;
import com.banka1.stock_service.repository.ListingRepository;
import com.banka1.stock_service.repository.StockExchangeRepository;
import com.banka1.stock_service.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Seeds a built-in set of starter stock tickers required by the stock refresh flow.
 *
 * <p>The service intentionally keeps the implementation simple:
 *
 * <ul>
 *     <li>it works with a fixed in-memory list of starter tickers</li>
 *     <li>it creates missing {@link Stock} rows</li>
 *     <li>it creates missing {@link Listing} rows linked to those stocks</li>
 *     <li>it does not create duplicates on repeated runs</li>
 * </ul>
 *
 * <p>The starter rows use placeholder fundamentals and placeholder market snapshot values.
 * Those values are sufficient for database integrity and are expected to be replaced later by
 * the dedicated stock market-data refresh use case.
 */
@Service
@RequiredArgsConstructor
public class StockTickerSeedService {

    private static final String SOURCE = "built-in starter stock tickers";
    private static final BigDecimal ZERO_DIVIDEND_YIELD = new BigDecimal("0.0000");
    private static final long ZERO_OUTSTANDING_SHARES = 0L;
    private static final long DEFAULT_VOLUME = 50_000_000L;
    /**
     * Approximate mid-market spreads applied to {@code price} so newly seeded listings have
     * usable bid/ask values without depending on the upstream market-data refresh succeeding.
     * Without this, freshly seeded listings sit at price=ask=bid=0 until Alpha Vantage refresh
     * runs successfully, which is unreliable in a sandboxed dev environment.
     */
    private static final BigDecimal ASK_SPREAD = new BigDecimal("0.05000000");
    private static final BigDecimal BID_SPREAD = new BigDecimal("0.05000000");
    private static final List<SeededStockRow> DEFAULT_STOCKS = List.of(
            // Nasdaq (XNAS)
            new SeededStockRow("AAPL", "Apple Inc.", "XNAS", new BigDecimal("180.00000000")),
            new SeededStockRow("MSFT", "Microsoft Corporation", "XNAS", new BigDecimal("420.00000000")),
            new SeededStockRow("GOOGL", "Alphabet Inc. Class A", "XNAS", new BigDecimal("165.00000000")),
            new SeededStockRow("AMZN", "Amazon.com, Inc.", "XNAS", new BigDecimal("185.00000000")),
            new SeededStockRow("TSLA", "Tesla, Inc.", "XNAS", new BigDecimal("260.00000000")),
            // New York Portfolio Clearing (NYPC)
            new SeededStockRow("IBM", "International Business Machines Corp.", "NYPC", new BigDecimal("210.00000000")),
            new SeededStockRow("GS", "Goldman Sachs Group Inc.", "NYPC", new BigDecimal("450.00000000")),
            new SeededStockRow("JPM", "JPMorgan Chase & Co.", "NYPC", new BigDecimal("210.00000000")),
            // Chicago Mercantile Exchange (XCME)
            new SeededStockRow("WMT", "Walmart Inc.", "XCME", new BigDecimal("70.00000000")),
            new SeededStockRow("BAC", "Bank of America Corp.", "XCME", new BigDecimal("40.00000000"))
    );

    private final StockRepository stockRepository;
    private final ListingRepository listingRepository;
    private final StockExchangeRepository stockExchangeRepository;

    /**
     * Seeds the built-in starter tickers into the database.
     *
     * <p>Each predefined ticker guarantees the presence of:
     *
     * <ul>
     *     <li>one {@link Stock} entity keyed by ticker</li>
     *     <li>one linked {@link Listing} entity with {@link ListingType#STOCK}</li>
     * </ul>
     *
     * <p>The seed is idempotent. If both required rows already exist for one ticker,
     * that ticker is counted as unchanged.
     *
     * @return seed summary
     */
    @Transactional
    public StockTickerSeedResponse seedDefaultTickers() {
        int createdCount = 0;
        int unchangedCount = 0;

        for (SeededStockRow row : DEFAULT_STOCKS) {
            boolean rowCreated = false;

            StockExchange exchange = stockExchangeRepository.findByExchangeMICCode(row.exchangeMicCode())
                    .orElseThrow(() -> new IllegalStateException(
                            "Stock exchange %s must exist before stock ticker seeding runs."
                                    .formatted(row.exchangeMicCode())
                    ));

            Stock stock = stockRepository.findByTicker(row.ticker()).orElse(null);
            if (stock == null) {
                stock = stockRepository.saveAndFlush(createStock(row));
                rowCreated = true;
            }

            Listing listing = listingRepository.findByListingTypeAndSecurityId(ListingType.STOCK, stock.getId())
                    .orElse(null);
            if (listing == null) {
                listingRepository.save(createListing(row, stock, exchange));
                rowCreated = true;
            }

            if (rowCreated) {
                createdCount++;
            } else {
                unchangedCount++;
            }
        }

        return new StockTickerSeedResponse(
                SOURCE,
                DEFAULT_STOCKS.size(),
                createdCount,
                0,
                unchangedCount
        );
    }

    /**
     * Creates a placeholder stock entity for one starter ticker.
     *
     * @param row predefined stock seed row
     * @return new stock entity
     */
    private Stock createStock(SeededStockRow row) {
        Stock stock = new Stock();
        stock.setTicker(row.ticker());
        stock.setName(row.name());
        stock.setOutstandingShares(ZERO_OUTSTANDING_SHARES);
        stock.setDividendYield(ZERO_DIVIDEND_YIELD);
        return stock;
    }

    /**
     * Creates a placeholder listing row linked to an already persisted stock with sane fallback
     * mid-market price/ask/bid/volume so manual exchange testing works even without a successful
     * upstream Alpha Vantage refresh. Once {@code refresh-market-data} runs successfully, these
     * placeholder values are overwritten by real quotes.
     *
     * @param row seed row carrying the per-ticker fallback price
     * @param stock persisted stock entity
     * @param stockExchange exchange used for the starter listing
     * @return new listing entity
     */
    private Listing createListing(SeededStockRow row, Stock stock, StockExchange stockExchange) {
        Listing listing = new Listing();
        listing.setSecurityId(stock.getId());
        listing.setListingType(ListingType.STOCK);
        listing.setStockExchange(stockExchange);
        listing.setTicker(stock.getTicker());
        listing.setName(stock.getName());
        listing.setLastRefresh(LocalDateTime.now());
        BigDecimal price = row.price();
        listing.setPrice(price);
        listing.setAsk(price.add(ASK_SPREAD));
        listing.setBid(price.subtract(BID_SPREAD));
        listing.setChange(BigDecimal.ZERO);
        listing.setVolume(DEFAULT_VOLUME);
        return listing;
    }

    /**
     * Immutable description of one built-in seed stock row.
     *
     * @param ticker unique stock ticker
     * @param name display name
     * @param exchangeMicCode MIC code of the exchange the listing belongs to
     * @param price mid-market starter price; ask/bid are derived with a small spread
     */
    private record SeededStockRow(
            String ticker,
            String name,
            String exchangeMicCode,
            BigDecimal price
    ) {
    }
}
