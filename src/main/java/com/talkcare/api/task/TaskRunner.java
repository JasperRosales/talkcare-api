package com.talkcare.api.task;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/** Runs the {@link CliTask} named on the command line, then exits with its status. */
@Component
class TaskRunner implements ApplicationRunner {

    private final Map<String, CliTask> tasks;
    private final ConfigurableApplicationContext context;

    TaskRunner(List<CliTask> tasks, ConfigurableApplicationContext context) {
        this.tasks = tasks.stream().collect(Collectors.toMap(CliTask::name, Function.identity()));
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        for (String arg : args.getNonOptionArgs()) {
            CliTask task = tasks.get(arg);
            if (task != null) {
                int status = 0;
                try {
                    task.run();
                } catch (Exception ex) {
                    System.err.println(task.name() + " failed: " + ex.getMessage());
                    status = 1;
                }
                int exitCode = status;
                System.exit(SpringApplication.exit(context, () -> exitCode));
            }
        }
    }
}
