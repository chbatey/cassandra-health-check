package info.batey.healthcheck.configuration;

import io.dropwizard.Application;
import io.dropwizard.setup.Environment;

public class HealthCheckApplication extends Application<HealthCheckConfiguration> {
    @Override
    public void run(HealthCheckConfiguration healthCheckConfiguration, Environment environment) throws Exception {
        StatusEndpoint statusEndpoint = new StatusEndpoint();
        environment.jersey().register(statusEndpoint);
        new CassandraHealthCheck(healthCheckConfiguration).initalise();
    }

    public static void main(String[] args) throws Exception {
        new HealthCheckApplication().run(args);
    }
}
