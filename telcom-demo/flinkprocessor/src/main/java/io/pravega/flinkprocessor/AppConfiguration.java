package io.pravega.flinkprocessor;


import io.pravega.client.stream.RetentionPolicy;
import io.pravega.client.stream.ScalingPolicy;
import io.pravega.client.stream.Stream;
import io.pravega.client.stream.StreamCut;
import io.pravega.client.stream.impl.DefaultCredentials;
import io.pravega.connectors.flink.PravegaConfig;
import io.pravega.flinkprocessor.util.PravegaKeycloakCredentialsFromString;
import org.apache.flink.api.java.utils.ParameterTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * A generic configuration class for Flink Pravega applications.
 * This class can be extended for job-specific configuration parameters.
 */
public class AppConfiguration {
    final private static Logger log = LoggerFactory.getLogger(AppConfiguration.class);

    private final ParameterTool params;
    private final int parallelism;
    private final int readerParallelism;
    private final long checkpointIntervalMs;
    private final long checkpointTimeoutMs;
    private final boolean enableCheckpoint;
    private final boolean enableOperatorChaining;
    private final boolean enableRebalance;
    private final long maxOutOfOrdernessMs;
    private final String nodeName;
    private final String jobName;
    private final String avroSchema;
    // InfluxDB
    private String metricsSink;
    private String influxDbUrl;
    private String influxdbUsername;
    private String influxdbPassword;
    private String influxdbDatabase;
    private int influxdbBatchSize;
    private int influxdbFlushDuration;


    // Analytics
    private String groupBy;
    private String valueField;
    private String valueFieldType;
    private String tsField;
    private String controllerUri;



    public AppConfiguration(String[] args) throws IOException {
        params = ParameterTool.fromArgs(args);
        log.info("Parameter Tool: {}", getParams().toMap());
        parallelism = getParams().getInt("parallelism", 1);
        readerParallelism = getParams().getInt("readerParallelism", 1);
        checkpointIntervalMs = getParams().getLong("checkpointIntervalMs", 10000);
        checkpointTimeoutMs = getParams().getLong("checkpointTimeoutMs", 20000);
        enableCheckpoint = getParams().getBoolean("enableCheckpoint", true);
        enableOperatorChaining = getParams().getBoolean("enableOperatorChaining", true);
        enableRebalance = getParams().getBoolean("rebalance", true);
        maxOutOfOrdernessMs = getParams().getLong("maxOutOfOrdernessMs", 1000);
        // vsphere node name to match with the k8s node name value
        nodeName = getParams().get("nodeName");
        jobName = getParams().get("jobName");
       

        // InfluxDB
        metricsSink  = params.get("metricsSink", "InfluxDB");
        influxDbUrl  = params.get("influxDB_host", "http://project-metrics:8086");
        influxdbUsername = params.get("influxDB_username", "nTUXERVAg0");
        influxdbPassword = params.get("influxDB_password", "jZI79agzcM");
        influxdbDatabase = params.get("influxDB_database", "vsphere");
        influxdbBatchSize = params.getInt("influxDB_batchsize", 5000);
        influxdbFlushDuration = params.getInt("influxDB_flushDuration", 500);

        // data Transformation using sql
        groupBy = params.get("group-by", "MetricId");
        valueField = params.get("value-field", "value");
        valueFieldType = params.get("value-field-type", "STRING");
        tsField = params.get("timestamp-field", "timestamp");
        controllerUri = params.get("controller");

    

        // Get Avro schema from base-64 encoded string parameter or from a file.
        final String avroSchemaBase64 = getParams().get("avroSchema", "");
        if (avroSchemaBase64.isEmpty()) {
            final String avroSchemaFileName = getParams().get("avroSchemaFile", "");
            if (avroSchemaFileName.isEmpty()) {
                avroSchema = "";
            } else {
                avroSchema = new String(Files.readAllBytes(Paths.get(avroSchemaFileName)), StandardCharsets.UTF_8);
            }
        } else {
            avroSchema = new String(Base64.getDecoder().decode(avroSchemaBase64), StandardCharsets.UTF_8);
        }
    }

    @Override
    public String toString() {
        return "AppConfiguration{" +
                "parallelism=" + parallelism +
                ", readerParallelism=" + readerParallelism +
                ", checkpointIntervalMs=" + checkpointIntervalMs +
                ", checkpointTimeoutMs=" + checkpointTimeoutMs +
                ", enableCheckpoint=" + enableCheckpoint +
                ", enableOperatorChaining=" + enableOperatorChaining +
                ", enableRebalance=" + enableRebalance +
                ", maxOutOfOrdernessMs=" + maxOutOfOrdernessMs +
                ", jobName=" + jobName +
                ", avroSchema=" + avroSchema +
                '}';
    }

    public ParameterTool getParams() {
        return params;
    }

    public String getNodeName() { return nodeName; }

    public StreamConfig getStreamConfig(final String argName) {
        return new StreamConfig(argName,  getParams());
    }

    public int getParallelism() {
        return parallelism;
    }

    public int getReaderParallelism() {
        return readerParallelism;
    }

    public long getCheckpointTimeoutMs() {
        return checkpointTimeoutMs;
    }

    public long getCheckpointIntervalMs() {
        return checkpointIntervalMs;
    }

    public boolean isEnableCheckpoint() {
        return enableCheckpoint;
    }

    public boolean isEnableOperatorChaining() {
        return enableOperatorChaining;
    }

    public boolean isEnableRebalance() {
        return enableRebalance;
    }

    public long getMaxOutOfOrdernessMs() {
        return maxOutOfOrdernessMs;
    }

    public String getJobName(String defaultJobName) {
        return (jobName == null) ? defaultJobName : jobName;
    }

    public String getAvroSchema() {
        return avroSchema;
    }

    public enum MetricsSink {
        InfluxDB
    }

    public MetricsSink getMetricsSink() {   return MetricsSink.valueOf(metricsSink);    }

    public String getInfluxdbUrl() { return influxDbUrl; }

    public String getInfluxdbUsername() {
        return influxdbUsername;
    }

    public String getInfluxdbPassword() {
        return influxdbPassword;
    }

    public String getInfluxdbDatabase() {
        return influxdbDatabase;
    }

    public int getInfluxdbFlushDuration() {
        return influxdbFlushDuration;
    }

    public int getInfluxdbBatchSize() {
        return influxdbBatchSize;
    }

    

    public String getGroupBy(){
        return groupBy;
    }

    public String getValueField(){
        return valueField;
    }

    public String getValueFieldType() {
        return valueFieldType;
    }

    public String getTsField() {
        return tsField;
    }

    public String getControllerUri() {
        return controllerUri;
    }


    public static class StreamConfig {
        private final Stream stream;
        private final PravegaConfig pravegaConfig;
        private final int targetRate;
        private final int scaleFactor;
        private final int minNumSegments;
        private final StreamCut startStreamCut;
        private final StreamCut endStreamCut;
        private final boolean startAtTail;
        private final boolean endAtTail;
        private final int retentionPolicy;


        public StreamConfig(final String argName, final ParameterTool globalParams) {
            final String argPrefix = argName.isEmpty() ? argName : argName + "-";

            // Build ParameterTool parameters with stream-specific parameters copied to global parameters.
            Map<String, String> streamParamsMap = new HashMap<>(globalParams.toMap());
            globalParams.toMap().forEach((k, v) -> {
                if (k.startsWith(argPrefix)) {
                    streamParamsMap.put(k.substring(argPrefix.length()), v);
                }
            });
            ParameterTool params = ParameterTool.fromMap(streamParamsMap);
            final String streamSpec = globalParams.getRequired(argPrefix + "stream");
            log.info("Parameters for {} stream {}: {}", argName, streamSpec, params.toMap());

            // Build Pravega config for this stream.
            PravegaConfig tempPravegaConfig = PravegaConfig.fromParams(params);
            stream = tempPravegaConfig.resolve(streamSpec);
            // Copy stream's scope to default scope.
            tempPravegaConfig = tempPravegaConfig.withDefaultScope(stream.getScope());

            final String keycloakConfigBase64 = params.get("keycloak", "");
            if (!keycloakConfigBase64.isEmpty()) {
                // Add Keycloak credentials. This is decoded as base64 to avoid complications with JSON in arguments.
                log.info("Loading base64-encoded Keycloak credentials from parameter {}keycloak.", argPrefix);
                final String keycloakConfig = new String(Base64.getDecoder().decode(keycloakConfigBase64), StandardCharsets.UTF_8);
                tempPravegaConfig = tempPravegaConfig.withCredentials(new PravegaKeycloakCredentialsFromString(keycloakConfig));
            } else {
                // Add username/password credentials.
                final String username = params.get("username", "");
                final String password = params.get("password", "");
                if (!username.isEmpty() || !password.isEmpty()) {
                    tempPravegaConfig = tempPravegaConfig.withCredentials(new DefaultCredentials(password, username));
                }
            }

            pravegaConfig = tempPravegaConfig;
            targetRate = params.getInt("targetRate", 10*1024*1024);  // data rate in KiB/sec
            scaleFactor = params.getInt("scaleFactor", 2);
            minNumSegments = params.getInt("minNumSegments", 1);
            startStreamCut = StreamCut.from(params.get("startStreamCut", StreamCut.UNBOUNDED.asText()));
            endStreamCut = StreamCut.from(params.get("endStreamCut", StreamCut.UNBOUNDED.asText()));
            startAtTail = params.getBoolean( "startAtTail", false);
            endAtTail = params.getBoolean("endAtTail", false);
            retentionPolicy = params.getInt("retentionPolicy", 525600); // duration per minute default to one year
        }

        @Override
        public String toString() {
            return "StreamConfig{" +
                    "stream=" + stream +
                    ", pravegaConfig=" + pravegaConfig.getClientConfig() +
                    ", targetRate=" + targetRate +
                    ", scaleFactor=" + scaleFactor +
                    ", minNumSegments=" + minNumSegments +
                    ", startStreamCut=" + startStreamCut +
                    ", endStreamCut=" + endStreamCut +
                    ", startAtTail=" + startAtTail +
                    ", endAtTail=" + endAtTail +
                    '}';
        }

        public Stream getStream() {
            return stream;
        }

        public PravegaConfig getPravegaConfig() {
            return pravegaConfig;
        }

        public RetentionPolicy getRetentionPolicy() {
            return RetentionPolicy.byTime(Duration.ofMinutes(retentionPolicy));
        }

        public ScalingPolicy getScalingPolicy() {
            return ScalingPolicy.byDataRate(targetRate, scaleFactor, minNumSegments);
        }

        public StreamCut getStartStreamCut() {
            return startStreamCut;
        }

        public StreamCut getEndStreamCut() {
            return endStreamCut;
        }

        public boolean isStartAtTail() {
            return startAtTail;
        }

        public boolean isEndAtTail() {
            return endAtTail;
        }
    }

}
