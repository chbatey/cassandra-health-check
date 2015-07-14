package info.batey.healthcheck.configuration;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;

@Path("/status")
@Produces(MediaType.APPLICATION_JSON)
public class StatusEndpoint {

    private final CassandraHealthCheck cassandraHealthCheck;

    public StatusEndpoint(CassandraHealthCheck cassandraHealthCheck) {
        this.cassandraHealthCheck = cassandraHealthCheck;
    }


    @GET
    public Map<String, CassandraHealthCheck.Status> check() {
        return this.cassandraHealthCheck.status().getStatus();
    }

}
