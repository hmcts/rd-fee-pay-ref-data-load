package uk.gov.hmcts.reform.locationrefdata;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import uk.gov.hmcts.reform.data.ingestion.camel.service.AuditServiceImpl;

import static org.apache.commons.lang.BooleanUtils.isFalse;

@SpringBootApplication(scanBasePackages = "uk.gov.hmcts.reform")
@SuppressWarnings ("PMD.DoNotCallSystemExit")
@Slf4j
public class LrdLoadApplication implements ApplicationRunner {

    private static String logComponentName = "Location Reference Data Load";

    @Autowired
    JobLauncher jobLauncher;

    @Autowired
    Job job;

    @Value("${batchjob-name}")
    String jobName;

    @Autowired
    AuditServiceImpl auditService;

    public static void main(final String[] args) throws InterruptedException {
        ApplicationContext context = SpringApplication.run(LrdLoadApplication.class);
        //Sleep added to allow app-insights to flush the logs
        Thread.sleep(7000);
        int exitCode = SpringApplication.exit(context);
        log.info("{}:: Application exiting with exit code {} ", logComponentName, exitCode);
        System.exit(exitCode);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {

        JobParameters params = new JobParametersBuilder()
            .addString(jobName, String.valueOf(System.currentTimeMillis()))
            .toJobParameters();

        if (isFalse(auditService.isAuditingCompleted())) {
            log.info("{}:: Location Ref data load job running first time for a day::", logComponentName);
            jobLauncher.run(job, params);
            log.info("{}:: Location Ref data load  job run completed::", logComponentName);
        } else {
            log.info("{}:: no run of Location Ref data load job as it has ran for the day::", logComponentName);
        }
    }

    @Value("${logging-component-name}")
    public void setLogComponentName(String logComponentName) {
        LrdLoadApplication.logComponentName = logComponentName;
    }
}
