package io.cattle.platform.process.agent;

import static io.cattle.platform.core.model.tables.HostTable.*;
import io.cattle.platform.core.constants.HostConstants;
import io.cattle.platform.core.model.Agent;
import io.cattle.platform.core.model.Host;
import io.cattle.platform.core.model.tables.HostTable;
import io.cattle.platform.deferred.util.DeferredUtils;
import io.cattle.platform.engine.handler.HandlerResult;
import io.cattle.platform.engine.process.ProcessDefinition;
import io.cattle.platform.engine.process.ProcessInstance;
import io.cattle.platform.engine.process.ProcessState;
import io.cattle.platform.engine.process.StateTransition;
import io.cattle.platform.eventing.EventService;
import io.cattle.platform.eventing.model.Event;
import io.cattle.platform.eventing.model.EventVO;
import io.cattle.platform.framework.event.FrameworkEvents;
import io.cattle.platform.object.meta.ObjectMetaDataManager;
import io.cattle.platform.process.common.handler.AbstractObjectProcessPrePostListener;
import io.cattle.platform.util.type.InitializationTask;
import io.cattle.platform.util.type.Priority;
import io.github.ibuildthecloud.gdapi.factory.SchemaFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentHostStateUpdate extends AbstractObjectProcessPrePostListener implements InitializationTask, Priority {

    private static final Logger log = LoggerFactory.getLogger(AgentHostStateUpdate.class);

    @Inject
    SchemaFactory schemaFactory;

    @Inject
    List<ProcessDefinition> processDefinitions;

    @Inject
    EventService eventService;

    Map<String, String> transitioningToDone = new HashMap<>();


    @Override
    public String[] getProcessNames() {
        return new String[] {"agent.*"};
    }

    @Override
    protected HandlerResult preHandle(ProcessState state, ProcessInstance process) {
        for (Host host : objectManager.children(state.getResource(), Host.class)) {
            log.info("Setting host [{}] agentState to [{}] on pre", host.getId(), state.getState());
            objectManager.setFields(host, HOST.AGENT_STATE, state.getState());
            trigger(host);
        }

        return null;
    }

    @Override
    protected HandlerResult postHandle(ProcessState state, ProcessInstance process) {
        Agent agent = (Agent)state.getResource();
        String newState = transitioningToDone.get(state.getState());

        if (newState == null) {
            return null;
        }

        for (Host host : objectManager.children(agent, Host.class)) {
            log.info("Setting host [{}] agentState to [{}] on post", host.getId(), newState);
            objectManager.setFields(host, HOST.AGENT_STATE, newState);
            trigger(host);
        }

        return null;
    }

    protected void trigger(Host host) {
        Map<String, Object> data = new HashMap<>();
        data.put(ObjectMetaDataManager.ACCOUNT_FIELD, host.getAccountId());

        Event event = EventVO.newEvent(FrameworkEvents.STATE_CHANGE)
                .withData(data)
                .withResourceType(HostConstants.TYPE)
                .withResourceId(host.getId().toString());

        eventService.publish(event);
    }

    @Override
    public void start() {
        String type = schemaFactory.getSchemaName(Agent.class);
        if (type == null) {
            return;
        }

        for (ProcessDefinition def : processDefinitions) {
            if (!type.equals(def.getResourceType())) {
                continue;
            }

            for (StateTransition transition : def.getStateTransitions()) {
                if (transition.getType() == StateTransition.Style.DONE) {
                    transitioningToDone.put(transition.getFromState(), transition.getToState());
                }
            }
        }
    }

    @Override
    public void stop() {
    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE;
    }

    public List<ProcessDefinition> getProcessDefinitions() {
        return processDefinitions;
    }

    public void setProcessDefinitions(List<ProcessDefinition> processDefinitions) {
        this.processDefinitions = processDefinitions;
    }

}
