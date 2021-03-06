package uk.gov.hmcts.reform.locationrefdata.cameltest;

import net.thucydides.core.annotations.WithTag;
import net.thucydides.core.annotations.WithTags;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.CamelTestContextBootstrapper;
import org.apache.camel.test.spring.junit5.MockEndpoints;
import org.javatuples.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import uk.gov.hmcts.reform.data.ingestion.configuration.AzureBlobConfig;
import uk.gov.hmcts.reform.data.ingestion.configuration.BlobStorageCredentials;
import uk.gov.hmcts.reform.locationrefdata.cameltest.testsupport.LrdIntegrationBaseTest;
import uk.gov.hmcts.reform.locationrefdata.cameltest.testsupport.SpringStarter;
import uk.gov.hmcts.reform.locationrefdata.config.LrdCamelConfig;
import uk.gov.hmcts.reform.locationrefdata.configuration.BatchConfig;

import java.io.FileInputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.springframework.util.ResourceUtils.getFile;
import static uk.gov.hmcts.reform.data.ingestion.camel.util.MappingConstants.SCHEDULER_START_TIME;

@TestPropertySource(properties = {"spring.config.location=classpath:application-integration.yml,"
    + "classpath:application-leaf-integration.yml"})
@CamelSpringBootTest
@MockEndpoints("log:*")
@ContextConfiguration(classes = {LrdCamelConfig.class, CamelTestContextBootstrapper.class,
    JobLauncherTestUtils.class, BatchConfig.class, AzureBlobConfig.class, BlobStorageCredentials.class},
    initializers = ConfigFileApplicationContextInitializer.class)
@SpringBootTest
@EnableAutoConfiguration(exclude = JpaRepositoriesAutoConfiguration.class)
@EnableTransactionManagement
@SqlConfig(dataSource = "dataSource", transactionManager = "txManager",
    transactionMode = SqlConfig.TransactionMode.ISOLATED)
@WithTags({@WithTag("testType:Functional")})
public class LrdBuildingLocationFileStatusCheck extends LrdIntegrationBaseTest {

    @Value("${lrd-building-location-select-query}")
    protected String lrdBuildingLocationSelectQuery;

    @Value("${truncate-audit}")
    protected String truncateAudit;

    @Value("${truncate-exception}")
    protected String truncateException;

    @Value("${select-dataload-scheduler-failure}")
    String lrdAuditSqlFailure;

    private static final String UPLOAD_FILE_NAME = "building_location_test.csv";
    private static final String ROUTE_TO_EXECUTE = "lrd-building-location-load";

    @BeforeEach
    public void init() {
        SpringStarter.getInstance().restart();
        setLrdCamelRouteToExecute(ROUTE_TO_EXECUTE);
        setLrdFileToLoad(UPLOAD_FILE_NAME);
    }

    @Test
    @Sql(scripts = {"/testData/truncate-building-locations.sql"})
    void testTaskletStaleFileErrorDay2WithKeepingDay1Data() throws Exception {
        //Day 1 happy path
        uploadFiles(String.valueOf(new Date(System.currentTimeMillis()).getTime()));

        JobParameters params = new JobParametersBuilder()
            .addString(jobLauncherTestUtils.getJob().getName(), UUID.randomUUID().toString())
            .toJobParameters();
        dataIngestionLibraryRunner.run(jobLauncherTestUtils.getJob(), params);
        deleteFile();
        deleteAuditAndExceptionDataOfDay1();

        //Day 2 stale files
        setLrdCamelRouteToExecute(ROUTE_TO_EXECUTE);
        setLrdFileToLoad(UPLOAD_FILE_NAME);
        uploadFiles(String.valueOf(new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000).getTime()));

        //not ran with dataIngestionLibraryRunner to set stale file via camelContext.getGlobalOptions()
        //.remove(SCHEDULER_START_TIME);
        params = new JobParametersBuilder()
            .addString(jobLauncherTestUtils.getJob().getName(), UUID.randomUUID().toString())
            .toJobParameters();
        jobLauncherTestUtils.launchJob(params);
        Pair<String, String> pair = new Pair<>(
            UPLOAD_FILE_NAME,
            "not loaded due to file stale error"
        );
        validateLrdServiceFileException(jdbcTemplate, exceptionQuery, pair);
        var result = jdbcTemplate.queryForList(auditSchedulerQuery);
        assertEquals(1, result.size());
        Assertions.assertEquals(1, jdbcTemplate.queryForList(lrdAuditSqlFailure).size());
        List<Map<String, Object>> buildingLocations = jdbcTemplate.queryForList(lrdBuildingLocationSelectQuery);
        assertFalse(buildingLocations.isEmpty());
        deleteFile();
    }

    private void deleteFile() throws Exception {
        lrdBlobSupport.deleteBlob(UPLOAD_FILE_NAME, false);
    }

    @Test
    @Sql(scripts = {"/testData/truncate-building-locations.sql"})
    void testTaskletNoFileErrorDay2WithKeepingDay1Data() throws Exception {
        //Day 1 happy path
        uploadFiles(String.valueOf(new Date(System.currentTimeMillis()).getTime()));

        JobParameters params = new JobParametersBuilder()
            .addString(jobLauncherTestUtils.getJob().getName(), UUID.randomUUID().toString())
            .toJobParameters();
        dataIngestionLibraryRunner.run(jobLauncherTestUtils.getJob(), params);
        deleteFile();
        deleteAuditAndExceptionDataOfDay1();

        setLrdCamelRouteToExecute(ROUTE_TO_EXECUTE);
        setLrdFileToLoad(UPLOAD_FILE_NAME);
        //Day 2 no upload file
        camelContext.getGlobalOptions().put(
            SCHEDULER_START_TIME,
            String.valueOf(new Date(System.currentTimeMillis()).getTime())
        );
        params = new JobParametersBuilder()
            .addString(jobLauncherTestUtils.getJob().getName(), UUID.randomUUID().toString())
            .toJobParameters();
        jobLauncherTestUtils.launchJob(params);
        Pair<String, String> pair = new Pair<>(
            UPLOAD_FILE_NAME,
            "building_location_test.csv file does not exist in azure storage account"
        );
        validateLrdServiceFileException(jdbcTemplate, exceptionQuery, pair);
        var result = jdbcTemplate.queryForList(auditSchedulerQuery);
        assertEquals(1, result.size());
        Assertions.assertEquals(1, jdbcTemplate.queryForList(lrdAuditSqlFailure).size());
        List<Map<String, Object>> buildingLocations = jdbcTemplate.queryForList(lrdBuildingLocationSelectQuery);
        assertFalse(buildingLocations.isEmpty());
        assertEquals(1, result.size());
    }

    private void deleteAuditAndExceptionDataOfDay1() {
        jdbcTemplate.execute(truncateAudit);
        jdbcTemplate.execute(truncateException);
        SpringStarter.getInstance().restart();
    }

    private void uploadFiles(String time) throws Exception {
        camelContext.getGlobalOptions().put(SCHEDULER_START_TIME, time);
        lrdBlobSupport.uploadFile(
            UPLOAD_FILE_NAME,
            new FileInputStream(getFile(
                "classpath:sourceFiles/buildingLocations/building_location_test.csv"))
        );
    }


}
