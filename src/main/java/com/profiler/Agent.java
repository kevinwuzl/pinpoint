package com.profiler;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.profiler.common.dto.thrift.AgentInfo;
import com.profiler.common.util.SpanUtils;
import com.profiler.context.TraceContext;
import com.profiler.sender.DataSender;
import com.profiler.util.NetworkUtils;
import sun.management.resources.agent;

public class Agent {

    public static final String FQCN = Agent.class.getName();

    private static final Logger logger = Logger.getLogger(Agent.class.getName());

    private volatile boolean alive = false;

    private final ServerInfo serverInfo;
    private final SystemMonitor systemMonitor;

    private final String agentId;
    private final String applicationName;
//    private boolean validate = true;

    private Agent() {
        this.serverInfo = new ServerInfo();
        this.systemMonitor = new SystemMonitor();
        String machineName = NetworkUtils.getMachineName();
        this.agentId = System.getProperty("hippo.agentId", machineName);
        validateAgentId();
        this.applicationName = System.getProperty("hippo.applicationName", "TOMCAT");
    }

    private void validateAgentId() {
        try {
            byte[] bytes = agentId.getBytes("UTF-8");
            if (bytes.length > SpanUtils.AGENT_NAME_LIMIT) {
                logger.warning("AgentId is too long(1~24) " + agentId);
            }
//            validate = false;
            // TODO 이거 후처리를 어떻게 해야 될지. agent를 시작 시키지 않아야 될거 같은데. lifecycle이 이쪽저쪽에 퍼져 있어서 일관된 stop에 문제가 있음..
        } catch (UnsupportedEncodingException e) {
            logger.log(Level.WARNING, "invalid agentId. Cause:" + e.getMessage(), e);
        }

    }

    private static class SingletonHolder {
        public static final Agent INSTANCE = new Agent();
    }

    public static Agent getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setIsAlive(boolean alive) {
        this.alive = alive;
    }

    public ServerInfo getServerInfo() {
        return this.serverInfo;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getApplicationName() {
        return applicationName;
    }


    /**
     * HIPPO 서버로 WAS정보를 전송한다.
     */
    // TODO: life cycle을 체크하는 방법으로 바꿀까.. DEAD, STARTING, STARTED, STOPPING,
    // STOPPED
    public void sendStartupInfo() {
        logger.info("Send startup information to HIPPO server.");

        String ip = getServerInfo().getHostip();
        String ports = "";
        for (Entry<Integer, String> entry : getServerInfo().getConnectors().entrySet()) {
            ports += " " + entry.getKey();
        }

        AgentInfo agentInfo = new AgentInfo();

        agentInfo.setHostname(ip);
        agentInfo.setPorts(ports);
        agentInfo.setIsAlive(true);
        agentInfo.setTimestamp(System.currentTimeMillis());
        agentInfo.setAgentId(getAgentId());

        DataSender.getInstance().addDataToSend(agentInfo);
    }

    public void start() {
        logger.info("Starting HIPPO Agent.");
        // trace context 새롭게 생성.
        TraceContext.initialize();
        systemMonitor.start();
    }

    public void stop() {
        logger.info("Stopping HIPPO Agent.");
        systemMonitor.stop();

        String ip = getServerInfo().getHostip();
        String ports = "";
        for (Entry<Integer, String> entry : getServerInfo().getConnectors().entrySet()) {
            ports += " " + entry.getKey();
        }

        AgentInfo agentInfo = new AgentInfo();

        agentInfo.setHostname(ip);
        agentInfo.setPorts(ports);
        agentInfo.setIsAlive(false);
        agentInfo.setTimestamp(System.currentTimeMillis());
        agentInfo.setAgentId(getAgentId());

        DataSender.getInstance().addDataToSend(agentInfo);
    }

    public static void startAgent() {
        Agent.getInstance().start();
    }

    public static void stopAgent() throws Exception {
        Agent.getInstance().stop();
    }
}
