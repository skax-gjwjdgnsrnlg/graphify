package com.graphify.company;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    @Query("""
            SELECT c FROM Company c
            WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(c.ticker) LIKE LOWER(CONCAT('%', :q, '%'))
            """)
    List<Company> searchByKeyword(@Param("q") String q, Pageable pageable);

    @Query("""
            SELECT c FROM Company c
            WHERE (
                LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(c.ticker) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(c.industry, '')) LIKE LOWER(CONCAT('%', :q, '%'))
            )
            AND (:industry IS NULL OR c.industry = :industry)
            AND (:market IS NULL OR c.market = :market)
            AND (:dataStatus IS NULL OR c.dataStatus = :dataStatus)
            """)
    Page<Company> searchCompanies(
            @Param("q") String q,
            @Param("industry") String industry,
            @Param("market") String market,
            @Param("dataStatus") String dataStatus,
            Pageable pageable
    );

    List<Company> findTop5ByIndustryAndIdNot(String industry, Long id);

    List<Company> findTop5ByOrderByUpdatedAtDesc();

    long countByIdIn(Iterable<Long> ids);

    Optional<Company> findByTicker(String ticker);

    List<Company> findByInKospi200True();

    Optional<Company> findByExternalSourceAndExternalId(String externalSource, String externalId);

    Optional<Company> findFirstByNameIgnoreCase(String name);
}
