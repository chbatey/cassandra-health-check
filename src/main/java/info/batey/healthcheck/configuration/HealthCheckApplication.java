package info.batey.healthcheck.configuration;

import com.codahale.metrics.servlets.MetricsServlet;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class HealthCheckApplication extends Application<HealthCheckConfiguration> {


    @Override
    public void run(HealthCheckConfiguration healthCheckConfiguration, Environment environment) throws Exception {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");

        environment.getApplicationContext().setAttribute(MetricsServlet.METRICS_REGISTRY, environment.metrics());
        environment.servlets().addServlet("metrics", MetricsServlet.class).addMapping("/metrics");


        CassandraHealthCheck cassandraHealthCheck = new CassandraHealthCheck(healthCheckConfiguration, environment.metrics());
        cassandraHealthCheck.initalise();
        StatusEndpoint statusEndpoint = new StatusEndpoint(cassandraHealthCheck);
        environment.jersey().register(statusEndpoint);

    }

    @Override
    public void initialize(Bootstrap<HealthCheckConfiguration> bootstrap) {
        bootstrap.addBundle(new AssetsBundle("/assets/css", "/css", null, "css"));
        bootstrap.addBundle(new AssetsBundle("/assets/js", "/js", null, "js"));
        bootstrap.addBundle(new AssetsBundle("/assets/img", "/img", null, "img"));
        bootstrap.addBundle(new AssetsBundle("/assets/pages", "/", "index.html", "html"));
        bootstrap.addBundle(new AssetsBundle("/META-INF/resources/webjars", "/webjars"));
    }

    public static void main(String[] args) throws Exception {
        new HealthCheckApplication().run(args);
    }
}
