package info.batey.healthcheck.configuration;

import io.dropwizard.Configuration;
import org.hibernate.validator.constraints.*;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;

public class HealthCheckConfiguration extends Configuration {

    @NotEmpty
    private List<String> hosts;

    @Min(1000)
    private long reconnectionInterval;

    @Min(10)
    private int socketTimeout;

    @Min(10)
    private int socketReadTimeout;

    private boolean keepAlive;

    @NotNull
    private String keyspace;

    @NotNull
    private String query;

    @NotNull
    private List<String> schemaCommands;

    public List<String> getHosts() {
        return hosts;
    }

    public long getReconnectionInterval() {
        return reconnectionInterval;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public int getSocketReadTimeout() {
        return socketReadTimeout;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public String getKeyspace() {
        return keyspace;
    }

    public String getQuery() {
        return query;
    }

    public List<String> getSchemaCommands() {
        return schemaCommands;
    }
}
