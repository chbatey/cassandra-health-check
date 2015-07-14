# Cassandra health checker

Creates a separate Cluster (connection) to every node with a WhiteList load balancing policy to stop each connection
discovering the other hosts.

Executes a periodic query to ensure connectivity with each node.
 
Currently the nodes have to be up when starting the process.

Look at ```config.yml``` for configuring hosts and timeouts.

## Building

Requires Java 8 and Maven 3+.

```mvn clean package```

## Running

```java -jar ./target/cassandra-health-check-0.0.1-SNAPSHOT.jar server ./src/main/resources/config.yml```