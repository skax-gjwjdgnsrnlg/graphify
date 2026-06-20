package com.graphify.company;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.ANY)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CompanyEntityTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    void company_defaultInKospi200_isFalse() {
        Company company = new Company();
        company.setName("테스트 회사");
        company.setTicker("000001");
        company.setMarket("KOSPI");

        Company saved = companyRepository.save(company);

        assertThat(saved.isInKospi200()).isFalse();
    }

    @Test
    void findByInKospi200True_includesKospi200Companies() {
        Company kospi200Company = new Company();
        kospi200Company.setName("삼성전자");
        kospi200Company.setTicker("005930");
        kospi200Company.setMarket("KOSPI");
        kospi200Company.setInKospi200(true);

        Company nonKospi200Company = new Company();
        nonKospi200Company.setName("기타 회사");
        nonKospi200Company.setTicker("999999");
        nonKospi200Company.setMarket("KOSPI");

        companyRepository.save(kospi200Company);
        companyRepository.save(nonKospi200Company);

        List<Company> result = companyRepository.findByInKospi200True();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTicker()).isEqualTo("005930");
    }

    @Test
    void findByInKospi200True_excludesNonKospi200Companies() {
        Company nonKospi200 = new Company();
        nonKospi200.setName("코스닥 회사");
        nonKospi200.setTicker("123456");
        nonKospi200.setMarket("KOSDAQ");
        nonKospi200.setInKospi200(false);

        companyRepository.save(nonKospi200);

        List<Company> result = companyRepository.findByInKospi200True();

        assertThat(result).isEmpty();
    }

    @Test
    void isInKospi200_returnsPersistedValue() {
        Company company = new Company();
        company.setName("POSCO홀딩스");
        company.setTicker("005490");
        company.setMarket("KOSPI");
        company.setInKospi200(true);

        Company saved = companyRepository.saveAndFlush(company);
        companyRepository.findById(saved.getId()); // reload from DB

        assertThat(saved.isInKospi200()).isTrue();
    }
}
