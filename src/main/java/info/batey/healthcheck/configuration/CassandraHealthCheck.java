package info.batey.healthcheck.configuration;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SocketOptions;
import com.datastax.driver.core.policies.ConstantReconnectionPolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;
import com.datastax.driver.core.policies.WhiteListPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CassandraHealthCheck {

    private final static Logger LOGGER = LoggerFactory.getLogger(CassandraHealthCheck.class);
    private final HealthCheckConfiguration configuration;
    private Map<String, Session> sessionMap;
    private Session allHosts;
    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    private volatile HealthStatus currentStatus = new HealthStatus(Collections.emptyMap());


    public CassandraHealthCheck(HealthCheckConfiguration configuration) {
        this.configuration = configuration;
    }

    public void initalise() throws Exception {
        List<String> hosts = configuration.getHosts();

        SocketOptions socketOptions = new SocketOptions();
        socketOptions.setConnectTimeoutMillis(configuration.getSocketTimeout());
        socketOptions.setReadTimeoutMillis(configuration.getSocketReadTimeout());
        socketOptions.setKeepAlive(configuration.isKeepAlive());

        Cluster.Builder cluster = Cluster.builder()
                .addContactPoints(hosts.toArray(new String[hosts.size()]))
                .withSocketOptions(socketOptions)
                .withInitialListeners(Collections.singleton(new CassandraStateListener()));

        this.allHosts = cluster.build().connect("test2");

        Map<String, Session> connections = new HashMap<>();
        for (String host : hosts) {
            try {
                connections.put(host, Cluster.builder()
                        .addContactPoints(InetAddress.getByName(host))
                        .withLoadBalancingPolicy(new WhiteListPolicy(
                                new RoundRobinPolicy(), Collections.singletonList(new InetSocketAddress(host, 9042))))
                        .withReconnectionPolicy(new ConstantReconnectionPolicy(configuration.getReconnectionInterval()))
                        .withSocketOptions(socketOptions)
                        .build().connect("test2"));
            } catch (Exception e) {
                LOGGER.warn("Unable to add host" + host, e);
            }
        }
        this.sessionMap = connections;

        this.executor.scheduleAtFixedRate(() -> {
                    Map<String, Status> status = new HashMap<>();
                    sessionMap.forEach((k, v) -> {
                        try {
                            v.execute("Select * from test");
                            status.put(k, Status.UP);
                        } catch (Exception e) {
                            status.put(k, Status.DOWN);
                        }
                        this.currentStatus = new HealthStatus(status);
                    });
                    LOGGER.info("{}", this.currentStatus);

                },
                0, 1, TimeUnit.SECONDS);

    }

    private static class HealthStatus {
        private Map<String, Status> status;

        public HealthStatus(Map<String, Status> status) {
            this.status = status;
        }

        @Override
        public String toString() {
            return "HealthStatus{" +
                    "status=" + status +
                    '}';
        }
    }

    private enum Status {
        UP, DOWN
    }

    private static class CassandraStateListener implements Host.StateListener {

        private static final Logger LOGGER = LoggerFactory.getLogger(CassandraHealthCheck.class);

        @Override
        public void onAdd(Host host) {
            LOGGER.info("Host added {}", host);
        }

        @Override
        public void onUp(Host host) {
            LOGGER.info("Host up {}", host);
        }

        @Override
        public void onSuspected(Host host) {
            LOGGER.info("Host suspected {}", host);
        }

        @Override
        public void onDown(Host host) {
            LOGGER.info("Host down {}", host);
        }

        @Override
        public void onRemove(Host host) {
            LOGGER.info("Host removed {}", host);
        }
    }
}
