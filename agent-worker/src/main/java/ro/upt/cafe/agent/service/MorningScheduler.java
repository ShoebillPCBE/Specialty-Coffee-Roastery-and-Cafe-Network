package ro.upt.cafe.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ro.upt.cafe.agent.model.AgentRun;

/** Wakes the agent up every morning at 07:00 (Europe/Bucharest), before the rush hour. */
@Component
public class MorningScheduler {

    private static final Logger log = LoggerFactory.getLogger(MorningScheduler.class);

    private final AgentRunService agent;

    public MorningScheduler(AgentRunService agent) {
        this.agent = agent;
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "${agent.zone:Europe/Bucharest}")
    public void morningRun() {
        agent.runCycle(AgentRun.Trigger.SCHEDULED)
                .ifPresentOrElse(run -> log.info("Morning run {} created {} proposals", run.runId(), run.proposalsCreated()),
                        () -> log.warn("Morning run skipped - another run is in progress"));
    }
}
