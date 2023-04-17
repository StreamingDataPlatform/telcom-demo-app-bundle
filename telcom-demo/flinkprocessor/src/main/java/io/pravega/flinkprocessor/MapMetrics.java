package io.pravega.flinkprocessor;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import io.pravega.client.stream.StreamCut;
import io.pravega.connectors.flink.FlinkPravegaReader;
import io.pravega.connectors.flink.FlinkPravegaWriter;
import io.pravega.connectors.flink.PravegaEventRouter;
import io.pravega.flinkprocessor.datatypes.DataMap;
import io.pravega.flinkprocessor.datatypes.IdracParents;
import io.pravega.flinkprocessor.datatypes.KubernetesMetrics;
import io.pravega.flinkprocessor.datatypes.VmParents;
import io.pravega.flinkprocessor.util.JsonSerializationSchema;
import io.pravega.flinkprocessor.util.UTF8StringDeserializationSchema;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.JoinFunction;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.WindowAssigner;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.influxdb.InfluxDBPoint;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapMetrics extends AbstractJob {
    public MapMetrics(AppConfiguration appConfiguration) {
        super(appConfiguration);
        this.esxiHostName = new KeySelector<VmParents, String>() {
            public String getKey(VmParents vmParent) throws Exception {
                return new String(vmParent.esxHostname);
            }
        };
        this.guestHostname = new KeySelector<VmParents, String>() {
            public String getKey(VmParents vmParent) throws Exception {
                return new String(vmParent.guestHostname);
            }
        };
        this.systemFqdn = new KeySelector<IdracParents, String>() {
            public String getKey(IdracParents idracParent) throws Exception {
                return new String(idracParent.systemFqdn);
            }
        };
        this.k8sInstance = new KeySelector<KubernetesMetrics, String>() {
            public String getKey(KubernetesMetrics instance) throws Exception {
                return new String(instance.instance);
            }
        };
    }

    private static Logger log = LoggerFactory.getLogger(MapMetrics.class);

    KeySelector esxiHostName;

    KeySelector guestHostname;

    KeySelector systemFqdn;

    KeySelector k8sInstance;

    public static void main(String... args) throws Exception {
        AppConfiguration config = new AppConfiguration(args);
        log.info("config: {}", config);
        MapMetrics job = new MapMetrics(config);
        job.run();
    }

    public void run() {
        try {
            AppConfiguration.StreamConfig vsphereStreamConfig = getConfig().getStreamConfig("vsphere");
            String nodeName = getConfig().getNodeName();
            log.info("input stream: {}", vsphereStreamConfig);
            createStream(vsphereStreamConfig);

            StreamCut vsphereStartStreamCut = resolveStartStreamCut(vsphereStreamConfig);
            StreamCut vsphereEndStreamCut = resolveEndStreamCut(vsphereStreamConfig);
            String fixedRoutingKey = getConfig().getParams().get("fixedRoutingKey", "");
            log.info("fixedRoutingKey: {}", fixedRoutingKey);
            AppConfiguration.StreamConfig outputStreamConfig = getConfig().getStreamConfig("output");
            log.info("output stream: {}", outputStreamConfig);
            createStream(outputStreamConfig);

            StreamExecutionEnvironment env = initializeFlinkStreaming();
            env.setParallelism(4);

            FlinkPravegaReader<String> vsphereFlinkPravegaReader = ((FlinkPravegaReader.Builder)((FlinkPravegaReader.Builder)FlinkPravegaReader.builder()
                    .withPravegaConfig(vsphereStreamConfig.getPravegaConfig()))
                    .forStream(vsphereStreamConfig.getStream().getStreamName(), vsphereStartStreamCut, vsphereEndStreamCut))
                    .withDeserializationSchema((DeserializationSchema)new UTF8StringDeserializationSchema()).build();

            AppConfiguration.StreamConfig idracStreamConfig = getConfig().getStreamConfig("idrac");
            log.info("idrac stream: {}", idracStreamConfig);
            createStream(idracStreamConfig);
            StreamCut idracStartStreamCut = resolveStartStreamCut(idracStreamConfig);
            StreamCut idracEndStreamCut = resolveEndStreamCut(idracStreamConfig);
            FlinkPravegaReader<String> idracFlinkPravegaReader = ((FlinkPravegaReader.Builder)((FlinkPravegaReader.Builder)FlinkPravegaReader.builder()
                    .withPravegaConfig(idracStreamConfig.getPravegaConfig())).forStream(idracStreamConfig.getStream().getStreamName(), idracStartStreamCut, idracEndStreamCut))
                    .withDeserializationSchema((DeserializationSchema)new UTF8StringDeserializationSchema()).build();

            AppConfiguration.StreamConfig k8sStreamConfig = getConfig().getStreamConfig("k8s");
            log.info("k8s stream: {}", k8sStreamConfig);
            createStream(k8sStreamConfig);
            StreamCut k8sStartStreamCut = resolveStartStreamCut(k8sStreamConfig);
            StreamCut k8sEndStreamCut = resolveEndStreamCut(k8sStreamConfig);
            FlinkPravegaReader<String> k8sFlinkPravegaReader = ((FlinkPravegaReader.Builder)((FlinkPravegaReader.Builder)FlinkPravegaReader.builder()
                    .withPravegaConfig(k8sStreamConfig.getPravegaConfig())).forStream(k8sStreamConfig.getStream().getStreamName(), k8sStartStreamCut, k8sEndStreamCut))
                    .withDeserializationSchema((DeserializationSchema)new UTF8StringDeserializationSchema()).build();
            SingleOutputStreamOperator singleOutputStreamOperator1 = env.addSource((SourceFunction)vsphereFlinkPravegaReader).name("vpshere-flatten-events");
            SingleOutputStreamOperator singleOutputStreamOperator2 = env.addSource((SourceFunction)idracFlinkPravegaReader).name("idrac-flatten-events");
            SingleOutputStreamOperator singleOutputStreamOperator3 = env.addSource((SourceFunction)k8sFlinkPravegaReader).name("k8s-flatten-events");

            //singleOutputStreamOperator3.printToErr("k8s source  data");
	        //singleOutputStreamOperator2.printToErr("idrac source  data");

            ObjectMapper objectMapper = new ObjectMapper();
            SingleOutputStreamOperator singleOutputStreamOperator4 = singleOutputStreamOperator1.flatMap(new VsphereMetricsProcess(objectMapper, nodeName)).name("vsphere").uid("vsphere");
            SingleOutputStreamOperator singleOutputStreamOperator5 = singleOutputStreamOperator2.flatMap(new IdracMetricsProcess(objectMapper)).name("idrac").uid("idrac");

            //singleOutputStreamOperator4.printToErr("vsphere flatmap data");
            //singleOutputStreamOperator5.printToErr("idrac flatmap data");

            DataStream<VmParents> joinEvents = singleOutputStreamOperator4.join((DataStream)singleOutputStreamOperator5).where(this.esxiHostName)
                    .equalTo(this.systemFqdn).window((WindowAssigner)TumblingProcessingTimeWindows.of(Time.seconds(10L)))
                    .apply(new JoinFunction<VmParents, IdracParents, VmParents>() {
                public VmParents join(VmParents first, IdracParents second) {
                    if (second != null)
                        first.idracServer = second;
                    return first;
                }
            });

	    //joinEvents.printToErr("idrac vsphere join data");

            SingleOutputStreamOperator singleOutputStreamOperator6 = singleOutputStreamOperator3.flatMap(new K8sMetricsProcess(objectMapper)).name("k8s").uid("k8s");

	    //singleOutputStreamOperator6.printToErr("k8s data");
            DataStream<DataMap> joinK8sEvents = singleOutputStreamOperator6.join(joinEvents).where(this.k8sInstance).equalTo(this.guestHostname)
                    .window((WindowAssigner)TumblingProcessingTimeWindows.of(Time.seconds(30L)))
                    .apply(new JoinFunction<KubernetesMetrics, VmParents, DataMap>() {
                public DataMap join(KubernetesMetrics first, VmParents second) {
                    DataMap dataMap = new DataMap();
                    if (first != null && second != null) {
                        dataMap = new DataMap();
                        dataMap.vcenter = second.clusterName;
                        dataMap.instance = first.instance;
                        dataMap.pod = first.jsonNode.get("pod").asText();
                        dataMap.esxhost = second.esxHostname;
                        dataMap.idrac = second.idracServer.systemName;
                        dataMap.timestamp = first.timestamp;
                        dataMap.metricValue = 123;
                    }
                    return dataMap;
                }
            });
            joinK8sEvents.printToErr("mapped events");
            FlinkPravegaWriter<DataMap> writer = ((FlinkPravegaWriter.Builder)((FlinkPravegaWriter.Builder)FlinkPravegaWriter.builder()
                    .withPravegaConfig(outputStreamConfig.getPravegaConfig())).forStream(outputStreamConfig.getStream()))
                    .withSerializationSchema((SerializationSchema)new JsonSerializationSchema()).withEventRouter(new EventRouter()).build();
            joinK8sEvents.addSink((SinkFunction)writer).uid("pravega-writer").name("Pravega writer to " + outputStreamConfig.getStream().getScopedName());

            /*final String measurement = "mapped-metrics";
            final ObjectMapper objMapper = new ObjectMapper();
            DataStream<InfluxDBPoint> dbEvents = joinK8sEvents
                    .flatMap(new MetricsProcess(objMapper, measurement))
                    .name("mapped-events-to-influxdb-point")
                    .uid("mapped-events-to-influxdb-point");
            addMetricsSink(dbEvents, "mapped-metrics"); */

            env.execute(MapMetrics.class.getSimpleName());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private class MetricsProcess implements FlatMapFunction<DataMap, InfluxDBPoint> {
        final ObjectMapper objectMapper;
        final String measurement;

        public MetricsProcess(ObjectMapper objectMapper, String measurement){
            this.objectMapper = objectMapper;
            this.measurement = measurement;
        }
        @Override
        public void flatMap(DataMap in, Collector<InfluxDBPoint> out) throws Exception {
            if (in != null) {
                try {
                    String json = objectMapper.writeValueAsString(in);
                    final JsonNode node = objectMapper.readTree(json);

                    HashMap<String, Object> fields = new HashMap<>();
                    HashMap<String, String> tags = new HashMap<>();
                    long timestamp = System.currentTimeMillis();
                    for (final Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
                        final String key = it.next();
                        final JsonNode tmp = node.get(key);
                        if (tmp == null) {
                            throw new NoSuchFieldException("Field '" + key + "' not found");
                        }
                        if (tmp.getNodeType() == JsonNodeType.NUMBER &&
                                (key == "timestamp" || key == "t" || key == "Timestamp")
                        ) {
                            timestamp = node.get(key).asLong();
                        } else if (tmp.getNodeType() == JsonNodeType.BOOLEAN) {
                            fields.put(key, node.get(key).asBoolean());
                        } else if (tmp.getNodeType() == JsonNodeType.NUMBER) {
                            fields.put(key, node.get(key).asDouble());
                        } else if (tmp.getNodeType() == JsonNodeType.STRING) {
                            tags.put(key, node.get(key).asText());
                        } else {
                            continue;
                        }
                    }
                    out.collect(new InfluxDBPoint(measurement, timestamp, tags, fields));
                } catch ( JsonParseException | NoSuchFieldException | NumberFormatException e) {
                    log.warn("Unable to extract key and counter", e);
                }
            }
        }
    }

    public static class VsphereTSExtractor extends BoundedOutOfOrdernessTimestampExtractor<VmParents> {
        public VsphereTSExtractor(long outOfOrdness) {
            super(Time.seconds(outOfOrdness));
        }

        public long extractTimestamp(VmParents a) {
            return a.timestamp;
        }
    }

    public static class IdracTSExtractor extends BoundedOutOfOrdernessTimestampExtractor<IdracParents> {
        public IdracTSExtractor(long outOfOrdness) {
            super(Time.seconds(outOfOrdness));
        }

        public long extractTimestamp(IdracParents a) {
            return a.timestamp;
        }
    }

    private static class EventRouter implements PravegaEventRouter<DataMap> {
        private EventRouter() {}

        public String getRoutingKey(DataMap event) {
            return event.instance;
        }
    }

    private class VsphereMetricsProcess implements FlatMapFunction<String, VmParents> {
        final ObjectMapper objectMapper;
        final String nodeName;

        public VsphereMetricsProcess(ObjectMapper objectMapper, String nodeName ) {

            this.objectMapper = objectMapper;
            this.nodeName = nodeName;
        }

        public void flatMap(String in, Collector<VmParents> out) throws Exception {
            if (in != null)
                try {
                    JsonNode node = this.objectMapper.readTree(in);
                    long timestamp = System.currentTimeMillis();
                    for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
                        String key = it.next();
                        JsonNode tmp = node.get(key);
                        if (tmp == null)
                            throw new NoSuchFieldException("Field '" + key + "' not found");
                        if (tmp.getNodeType() == JsonNodeType.ARRAY)
                            for (JsonNode jsonNode : tmp) {
                                if (jsonNode == null)
                                    throw new NoSuchFieldException("Field '" + jsonNode + "' not found");
                                if (jsonNode.getNodeType() == JsonNodeType.OBJECT)
                                    if (jsonNode.get("name").asText().contains("vsphere_vm_cpu")) {
                                        VmParents vmparents = new VmParents();
                                        vmparents.clusterName = jsonNode.get("tags").get("clustername").asText();
                                        vmparents.dcName = jsonNode.get("tags").get("dcname").asText();
                                        vmparents.esxHostname = jsonNode.get("tags").get("esxhostname").asText();
                                        vmparents.guest = jsonNode.get("tags").get("guest").asText();
                                        vmparents.moid = jsonNode.get("tags").get("moid").asText();
                                        vmparents.vmName = jsonNode.get("tags").get("vmname").asText();
                                        vmparents.vcenter = jsonNode.get("tags").get("vcenter").asText();
                                        if (jsonNode.get("tags").get("guesthostname") != null) {
                                            String nd = jsonNode.get("tags").get("guesthostname").asText();
                                            if(nd.equals("sdpidracnode") && (nodeName != null || !nodeName.isEmpty()) ){
                                                vmparents.guestHostname = nodeName;
                                            }else{

                                                vmparents.guestHostname = nd;
                                            }
                                        }

                                        vmparents.timestamp = jsonNode.get("timestamp").asLong();
                                        out.collect(vmparents);
                                    }
                            }
                    }
                } catch (JsonParseException|NoSuchFieldException|NumberFormatException e) {
                    MapMetrics.log.warn("Unable to extract key and counter", e);
                }
        }
    }

    private class IdracMetricsProcess implements FlatMapFunction<String, IdracParents> {
        final ObjectMapper objectMapper;

        public IdracMetricsProcess(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        public void flatMap(String in, Collector<IdracParents> out) throws Exception {
            if (in != null)
                try {
                    JsonNode node = this.objectMapper.readTree(in);
                    for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
                        String key = it.next();
                        JsonNode tmp = node.get(key);
                        if (tmp == null)
                            throw new NoSuchFieldException("Field '" + key + "' not found");
                        if (tmp.getNodeType() == JsonNodeType.ARRAY)
                            for (JsonNode jsonNode : tmp) {
                                if (jsonNode == null)
                                    throw new NoSuchFieldException("Field '" + jsonNode + "' not found");
                                if (jsonNode.getNodeType() == JsonNodeType.OBJECT &&
                                        jsonNode.get("name").asText().contains("snmp"))
                                    if (jsonNode.get("fields").get("system-fqdn") != null) {
                                        IdracParents idracParent = new IdracParents();
                                        idracParent.systemFqdn = jsonNode.get("fields").get("system-fqdn").asText();
                                        idracParent.osVersion = jsonNode.get("fields").get("system-osversion").asText();
                                        idracParent.systemOsName = jsonNode.get("fields").get("system-osname").asText();
                                        idracParent.systemServiceTag = jsonNode.get("fields").get("system-servicetag").asText();
                                        idracParent.systemName = jsonNode.get("tags").get("system-name").asText();
                                        idracParent.systemModel = jsonNode.get("fields").get("system-model").asText();
                                        idracParent.timestamp = jsonNode.get("timestamp").asLong();
                                        out.collect(idracParent);
                                    }
                            }
                    }
                } catch (JsonParseException|NoSuchFieldException|NumberFormatException e) {
                    MapMetrics.log.warn("Unable to extract key and counter", e);
                }
        }
    }

    private class K8sMetricsProcess implements FlatMapFunction<String, KubernetesMetrics> {
        final ObjectMapper objectMapper;

        public K8sMetricsProcess(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        public void flatMap(String in, Collector<KubernetesMetrics> out) throws Exception {
            if (in != null)
                try {
                    JsonNode node = this.objectMapper.readTree(in);
                    for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
                        String key = it.next();
                        JsonNode tmp = node.get(key);
                        if (tmp == null)
                            throw new NoSuchFieldException("Field '" + key + "' not found");
                        if (tmp.getNodeType() == JsonNodeType.ARRAY)
                            for (JsonNode jsonNode : tmp) {
                                if (jsonNode == null)
                                    throw new NoSuchFieldException("Field '" + jsonNode + "' not found");
                                if (jsonNode.getNodeType() == JsonNodeType.OBJECT &&
                                        jsonNode.get("tags").get("instance") != null)
                                    if (jsonNode.get("fields").get("container_cpu_usage_seconds_total") != null && jsonNode
                                            .get("tags").get("pod") != null) {
                                        KubernetesMetrics k8sMetics = new KubernetesMetrics();
                                        k8sMetics.instance = jsonNode.get("tags").get("instance").asText();
                                        k8sMetics.jsonNode = jsonNode.get("tags");
                                        k8sMetics.timestamp = jsonNode.get("timestamp").asLong() * 1000L;
                                        Date date = new Date(k8sMetics.timestamp);
                                        Format format = new SimpleDateFormat("yyyy MM dd HH:mm:ss");
                                        k8sMetics.TimeStamp = format.format(date);
                                        out.collect(k8sMetics);
                                    }
                            }
                    }
                } catch (JsonParseException|NoSuchFieldException|NumberFormatException e) {
                    MapMetrics.log.warn("Unable to extract key and counter", e);
                }
        }
    }
}
