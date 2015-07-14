package info.batey.healthcheck.configuration;

import io.dropwizard.Application;
import io.dropwizard.setup.Environment;

public class HealthCheckApplication extends Application<HealthCheckConfiguration> {
    @Override
    public void run(HealthCheckConfiguration healthCheckConfiguration, Environment environment) throws Exception {
        CassandraHealthCheck cassandraHealthCheck = new CassandraHealthCheck(healthCheckConfiguration);
        cassandraHealthCheck.initalise();
        StatusEndpoint statusEndpoint = new StatusEndpoint(cassandraHealthCheck);
        environment.jersey().register(statusEndpoint);
    }

    public static void main(String[] args) throws Exception {
        new HealthCheckApplication().run(args);
    }
}
