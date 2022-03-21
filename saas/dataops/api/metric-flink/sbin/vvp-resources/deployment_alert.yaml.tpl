apiVersion: v1
kind: Deployment
metadata:
  displayName: health-alert
  labels: {}
  name: health-alert
  namespace: ${VVP_WORK_NS}
  resourceVersion: 20
spec:
  maxJobCreationAttempts: 4
  maxSavepointCreationAttempts: 4
  restoreStrategy:
    allowNonRestoredState: false
    kind: LATEST_STATE
  sessionClusterName: sreworks-session-cluster
  state: RUNNING
  template:
    spec:
      artifact:
        additionalDependencies:
          - >-
            s3://vvp/artifacts/namespaces/${VVP_WORK_NS}/udfs/${UDF_ARTIFACT_JAR}
        flinkVersion: '1.14'
        kind: SQLSCRIPT
        sqlScript: |-
          BEGIN STATEMENT SET;

          INSERT INTO `vvp`.`${VVP_WORK_NS}`.`print_alert_instance`
          SELECT
          E.alert_def_id AS def_id
          FROM  `vvp`.`${VVP_WORK_NS}`.`metric_data_alert_rule_view` v1,
          lateral table (HealthAlert(v1.uid, v1.`metricId`, v1.metricName, v1.labels, v1.ts, v1.`value`, v1.def_id, v1.`app_id`, v1.ex_config)) as
          E (alert_def_id, metric_id, metric_name, app_instance_id, app_component_instance_id, metric_instance_id, metric_instance_labels, alert_rule_group, rule_name, `level`, content, ts, source)
          ;

          END;
      flinkConfiguration:
        execution.checkpointing.externalized-checkpoint-retention: RETAIN_ON_CANCELLATION
        execution.checkpointing.interval: 10s
        execution.checkpointing.min-pause: 10s
        high-availability: vvp-kubernetes
        metrics.reporter.prom.class: org.apache.flink.metrics.prometheus.PrometheusReporter
        state.backend: filesystem
        taskmanager.memory.managed.fraction: '0.0'
      parallelism: 1
  upgradeStrategy:
    kind: STATEFUL

