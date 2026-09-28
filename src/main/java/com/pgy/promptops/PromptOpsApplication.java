package com.pgy.promptops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PromptOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PromptOpsApplication.class, args);
    }
}
