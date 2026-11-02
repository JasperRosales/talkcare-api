package com.talkcare.api;

import com.talkcare.api.task.CliTask;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point.
 *
 * <pre>
 *   java -jar talkcare-api.jar           # HTTP server (applies pending migrations first)
 *   java -jar talkcare-api.jar migrate   # apply migrations and exit (release step)
 *   java -jar talkcare-api.jar seed      # create the four test accounts and exit
 * </pre>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TalkCareApiApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(TalkCareApiApplication.class);
        if (CliTask.isRequested(args)) {
            application.setWebApplicationType(WebApplicationType.NONE);
        }
        application.run(args);
    }
}
