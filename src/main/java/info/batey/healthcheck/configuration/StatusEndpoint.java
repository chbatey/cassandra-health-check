package info.batey.healthcheck.configuration;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/status")
@Produces(MediaType.APPLICATION_JSON)
public class StatusEndpoint {
    @GET
    public String check() {
        return "OK";
    }

}
