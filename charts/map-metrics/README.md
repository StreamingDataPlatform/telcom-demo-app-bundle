# flink SDP -> influxDB

# Introduction

This charts sink a Pravega stream to an InfluxDB instance in SDP

## Parameters

| Name                      | Description                                     | Value |
| ------------------------- | ----------------------------------------------- | ----- |
| `appParameters.input-stream`    | input stream                    | `"project-1/stream1"`  |
| `appParameters.checkpointIntervalMs` | Flink check points interval | `"1000"`  |
| `appParameters.enableOperatorChaining`     | enable operator chaining    | `"false"`  |
| `appParameters.input-startAtTail`    | start at tail of the stream                | `"true"`  |
| `appParameters.input-endAtTail` | End reading at the end of the stream | `"false"`  |
| `appParameters.input-minNumSegments`     | input stream minimum segments    | `"1"`  |
| `appParameters.output-minNumSegments`    | output stream minimum ssegments                    | `"1"`  |
| `mainClass` | Flink application main class | `"io.pravega.flinkprocessor.MapMetrics"`  |
| `appParametersJson`     |   These parameters are converted to JSON and then base-64 encoded.  | `{}`  |
