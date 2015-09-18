package info.batey.healthcheck.configuration;

import com.codahale.metrics.*;
import com.codahale.metrics.Timer;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.datastax.driver.core.*;
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
    private final com.codahale.metrics.Timer requests;

    private Map<String, Session> sessionMap;
    private Session allHosts;
    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

    private volatile HealthStatus currentStatus = new HealthStatus("Not executed yet", Status.UP, Collections.emptyMap());

    public CassandraHealthCheck(HealthCheckConfiguration configuration, MetricRegistry metrics) {
        this.configuration = configuration;
        this.requests =  metrics.timer("cassandraRequests");
    }

    public void initalise() throws Exception {

        List<String> hosts = configuration.getHosts();

        SocketOptions socketOptions = new SocketOptions();
        socketOptions.setConnectTimeoutMillis(configuration.getSocketTimeout());
        socketOptions.setReadTimeoutMillis(configuration.getSocketReadTimeout());
        socketOptions.setKeepAlive(configuration.isKeepAlive());

        Cluster cluster = Cluster.builder()
                .addContactPoints(hosts.toArray(new String[hosts.size()]))
                .withSocketOptions(socketOptions)
                .withReconnectionPolicy(new ConstantReconnectionPolicy(configuration.getReconnectionInterval()))
                .withInitialListeners(Collections.singleton(new CassandraStateListener()))
                .build();
        allHosts = cluster.connect();

        final Graphite graphite = new Graphite(new InetSocketAddress(configuration.getGraphiteHost(), 2003));
        Metrics metrics = cluster.getMetrics();
        final GraphiteReporter reporter = GraphiteReporter.forRegistry(metrics.getRegistry())
                .prefixedWith("cassandra-connection.all")
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build(graphite);
        reporter.start(1, TimeUnit.SECONDS);


        configuration.getSchemaCommands().forEach(allHosts::execute);
        allHosts.execute("USE " + configuration.getKeyspace());

        initialiseSessions(hosts, socketOptions);

        executor.scheduleAtFixedRate(() -> {
                    Map<String, Status> status = new HashMap<>();
                    sessionMap.forEach((k, v) -> status.put(k, checkStatus(k, v)));

                    Status overall = checkStatus("ALL", allHosts);
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
                Timer.Context time = requests.time();
                try {
                    allHosts.execute(configuration.getQuery());
                    Thread.sleep(1);
                } catch (Exception e) {
                    LOGGER.warn("Failed to execute query all hosts query", e);
                } finally {
                    time.stop();
                }
            }
        });
    }

    private void initialiseSessions(List<String> hosts, SocketOptions socketOptions) {
        Map<String, Session> connections = new HashMap<>();
        for (String host : hosts) {
            try {
                final Cluster connection = Cluster.builder()
                        .addContactPoints(InetAddress.getByName(host))
                        .withLoadBalancingPolicy(new WhiteListPolicy(
                                new RoundRobinPolicy(), Collections.singletonList(new InetSocketAddress(host, 9042))))
                        .withReconnectionPolicy(new ConstantReconnectionPolicy(configuration.getReconnectionInterval()))
                        .withSocketOptions(socketOptions)
                        .build();
                connections.put(host, connection.connect(configuration.getKeyspace()));

                final Graphite graphite = new Graphite(new InetSocketAddress(configuration.getGraphiteHost(), 2003));
                Metrics metrics = connection.getMetrics();
                final GraphiteReporter reporter = GraphiteReporter.forRegistry(metrics.getRegistry())
                        .prefixedWith("cassandra-connection." + host.replaceAll("\\.", "-"))
                        .convertRatesTo(TimeUnit.SECONDS)
                        .convertDurationsTo(TimeUnit.MILLISECONDS)
                        .filter(MetricFilter.ALL)
                        .build(graphite);
                reporter.start(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOGGER.warn("Unable to add host" + host, e);
            }
        }
        sessionMap = connections;
    }

    private Status checkStatus(String host, Session session) {
        try {
            session.execute(configuration.getQuery());
            return Status.UP;
        } catch (Exception e) {
            LOGGER.warn("Unable to execute query for host " + host, e);
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
