package info.batey.healthcheck.configuration;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
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
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CassandraHealthCheck {

    private final static Logger LOGGER = LoggerFactory.getLogger(CassandraHealthCheck.class);
    private final HealthCheckConfiguration configuration;
    private final MetricRegistry metrics;
    private final Meter requests;

    private Map<String, Session> sessionMap;
    private Session allHosts;
    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

    private volatile HealthStatus currentStatus = new HealthStatus("Not executed yet", Status.UP, Collections.emptyMap());

    public CassandraHealthCheck(HealthCheckConfiguration configuration, MetricRegistry metrics) {
        this.configuration = configuration;
        this.metrics = metrics;
        this.requests =  metrics.meter("cassandra-requests");
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
                .withReconnectionPolicy(new ConstantReconnectionPolicy(configuration.getReconnectionInterval()))
                .withInitialListeners(Collections.singleton(new CassandraStateListener()));

        allHosts = cluster.build().connect();

        configuration.getSchemaCommands().forEach(allHosts::execute);
        allHosts.execute("USE " + configuration.getKeyspace());

        initialiseSessions(hosts, socketOptions);

        executor.scheduleAtFixedRate(() -> {
                    Map<String, Status> status = new HashMap<>();
                    sessionMap.forEach((k, v) -> status.put(k, checkStatus(v)));

                    Status overall = checkStatus(allHosts);
                    LOGGER.info("Over all status {}", overall);
                    this.currentStatus = new HealthStatus(
                            configuration.getQuery(),
                            overall,
                            status);
                    LOGGER.info("{}", this.currentStatus);

                },
                0, 1, TimeUnit.SECONDS);


        executor.submit(() -> {
            while (true) {
                try {
                    allHosts.execute(configuration.getQuery());
                    requests.mark();
                    Thread.sleep(1);
                } catch (Exception e) {
                    LOGGER.debug("Failed to execute query", e);
                }
            }
        });
    }

    private void initialiseSessions(List<String> hosts, SocketOptions socketOptions) {
        Map<String, Session> connections = new HashMap<>();
        for (String host : hosts) {
            try {
                connections.put(host, Cluster.builder()
                        .addContactPoints(InetAddress.getByName(host))
                        .withLoadBalancingPolicy(new WhiteListPolicy(
                                new RoundRobinPolicy(), Collections.singletonList(new InetSocketAddress(host, 9042))))
                        .withReconnectionPolicy(new ConstantReconnectionPolicy(configuration.getReconnectionInterval()))
                        .withSocketOptions(socketOptions)
                        .build().connect(configuration.getKeyspace()));
            } catch (Exception e) {
                LOGGER.warn("Unable to add host" + host, e);
            }
        }
        sessionMap = connections;
    }

    private Status checkStatus(Session session) {
        try {
            session.execute(configuration.getQuery());
            return Status.UP;
        } catch (Exception e) {
            LOGGER.debug("Unable to execute query", e);
            return Status.DOWN;
        }
    }

    public HealthStatus status() {
        return this.currentStatus;
    }

    public static class HealthStatus {
        private final Instant lastExecuted;
        private final String query;
        private final Status overall;
        private final Map<String, Status> nodes;

        public HealthStatus(String query, Status overall, Map<String, Status> nodes) {
            this.lastExecuted = Instant.now();
            this.query = query;
            this.overall = overall;
            this.nodes = nodes;
        }

        public Map<String, Status> getNodes() {
            return nodes;
        }

        public String getQuery() {
            return query;
        }

        public Status getOverall() {
            return overall;
        }

        public long getLastExecuted() {
            return lastExecuted.toEpochMilli();
        }

        @Override
        public String toString() {
            return "HealthStatus{" +
                    "lastExecuted=" + lastExecuted +
                    ", query='" + query + '\'' +
                    ", overall=" + overall +
                    ", nodes=" + nodes +
                    '}';
        }
    }

    public enum Status {
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
