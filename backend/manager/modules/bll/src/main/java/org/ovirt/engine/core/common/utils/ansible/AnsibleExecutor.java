/*
 * Copyright oVirt Authors
 * SPDX-License-Identifier: Apache-2.0
*/

package org.ovirt.engine.core.common.utils.ansible;

import java.util.Map;
import java.util.function.BiConsumer;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.ovirt.engine.core.common.AuditLogType;
import org.ovirt.engine.core.dal.dbbroker.auditloghandling.AuditLogDirector;
import org.ovirt.engine.core.dal.dbbroker.auditloghandling.AuditLogable;
import org.ovirt.engine.core.dal.dbbroker.auditloghandling.AuditLogableImpl;
import org.ovirt.engine.core.utils.EngineLocalConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AnsibleExecutor {

    public static final String DEFAULT_LOG_DIRECTORY = "ansible";

    private static Logger log = LoggerFactory.getLogger(AnsibleExecutor.class);
    private static final int POLL_INTERVAL = 3000;

    @Inject
    private AuditLogDirector auditLogDirector;

    @Inject
    private AnsibleClientFactory ansibleClientFactory;

    /**
     * Executes ansible-playbook command. Default timeout is specified by ANSIBLE_PLAYBOOK_EXEC_DEFAULT_TIMEOUT variable
     * in engine.conf.
     *
     * @param commandConfig
     *            the config of command to be executed
     * @return return code of ansible-playbook
     */
    public AnsibleReturnValue runCommand(AnsibleCommandConfig commandConfig) {
        return runCommand(commandConfig, 0);
    }

    /**
     * Executes ansible-playbook command.
     *
     * @param command
     *            the config of command to be executed
     * @param timeout
     *            timeout in minutes to wait for command to finish
     * @return return code of ansible-playbook
     */
    public AnsibleReturnValue runCommand(AnsibleCommandConfig command, int timeout) {
        return runCommand(
            command,
            timeout,
            (String taskName, String eventUrl) -> {
                AuditLogable logable = createAuditLogable(command, taskName);
                auditLogDirector.log(logable, AuditLogType.ANSIBLE_RUNNER_EVENT_NOTIFICATION);
            }
        );
    }

    public AnsibleReturnValue runCommand(AnsibleCommandConfig command, BiConsumer<String, String> fn) {
        int timeout = EngineLocalConfig.getInstance().getInteger("ANSIBLE_PLAYBOOK_EXEC_DEFAULT_TIMEOUT");
        return runCommand(command, timeout, fn);
    }

    public AnsibleReturnValue runCommand(AnsibleCommandConfig command,
            Logger originLogger,
            BiConsumer<String, String> eventUrlConsumer) {
        int timeout = EngineLocalConfig.getInstance().getInteger("ANSIBLE_PLAYBOOK_EXEC_DEFAULT_TIMEOUT");
        return runCommand(
            command,
            timeout,
            (String taskName, String eventUrl) -> {
                AuditLogable logable = createAuditLogable(command, taskName);
                auditLogDirector.log(logable, AuditLogType.ANSIBLE_RUNNER_EVENT_NOTIFICATION);
                try {
                    eventUrlConsumer.accept(taskName, eventUrl);
                } catch (Exception ex) {
                    originLogger.error("Error: {}", ex.getMessage());
                    originLogger.debug("Exception: ", ex);
                }
            });
    }

    public AnsibleReturnValue runCommand(AnsibleCommandConfig command, int timeout, BiConsumer<String, String> fn) {
        if (timeout <= 0) {
            timeout = EngineLocalConfig.getInstance().getInteger("ANSIBLE_PLAYBOOK_EXEC_DEFAULT_TIMEOUT");
        }

        log.trace("Enter AnsibleExecutor::runCommand");

        AnsibleReturnValue ret = new AnsibleReturnValue(AnsibleReturnCode.ERROR);
        int lastEventId = 0;
        int iteration = 0;
        int totalEvents;

        String playUuid = null;
        AnsibleRunnerHTTPClient runnerClient = null;
        try {
            runnerClient = ansibleClientFactory.create(command);
            ret.setLogFile(runnerClient.getLogger().getLogFile());

            // Run the playbook:
            playUuid = runnerClient.runPlaybook(command);

            if (runnerClient.isHostUnreachable(playUuid)) {
                ret.setAnsibleReturnCode(AnsibleReturnCode.UNREACHABLE);
                return ret;
            }

            // Process the events of the playbook:
            while (iteration < timeout * 60) {
                Thread.sleep(POLL_INTERVAL);

                // Get the current status of the playbook:
                AnsibleRunnerHTTPClient.PlaybookStatus playbookStatus = runnerClient.getPlaybookStatus(playUuid);
                String status = playbookStatus.getStatus();
                String msg = playbookStatus.getMsg();
                // Process the events if the playbook is running:
                totalEvents = runnerClient.getTotalEvents(playUuid);

                if (
                    msg.equalsIgnoreCase("running")
                    || (msg.equalsIgnoreCase("successful") && lastEventId < totalEvents)
                ) {
                    lastEventId = runnerClient.processEvents(playUuid, lastEventId, fn);
                    iteration += POLL_INTERVAL / 1000;
                } else if (msg.equalsIgnoreCase("successful")) {
                    // Exit the processing if playbook finished:
                    ret.setAnsibleReturnCode(AnsibleReturnCode.OK);
                    return ret;
                } else if (status.equalsIgnoreCase("unknown")) {
                    // ignore and continue:
                } else {
                    // Playbook failed:
                    return ret;
                }
            }

            // Cancel playbook, and raise exception in case timeout occur:
            runnerClient.cancelPlaybook(playUuid);
            throw new PlaybookExecutionException(
                "Timeout exceed while waiting for playbook. ", command.playbook()
            );
        } catch (Exception ex) {
            log.error("Exception: {}", ex.getMessage());
            log.debug("Exception: ", ex);
            ret.setStderr(ex.getMessage());
        } finally {
            // Make sure all events are proccessed even in case of failure:
            if (playUuid != null && runnerClient != null) {
                runnerClient.processEvents(playUuid, lastEventId, fn);
            }
        }

        return ret;
    }

    private AuditLogable createAuditLogable(AnsibleCommandConfig command, String taskName) {
        return AuditLogableImpl.createHostEvent(
            command.hosts().get(0),
            command.correlationId(),
            Map.of("Message", taskName, "PlayAction", command.playAction())
        );
    }
}
