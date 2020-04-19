package com.google.swarm.tokenization.common;

import org.apache.beam.sdk.schemas.transforms.Group;
import org.apache.beam.sdk.transforms.Count;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.transforms.Sum;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AudioInspectDataTransform extends PTransform<PCollection<Row>, PCollection<Row>> {
  public static final Logger LOG = LoggerFactory.getLogger(AudioInspectDataTransform.class);

  @Override
  public PCollection<Row> expand(PCollection<Row> inspectedRow) {
    return inspectedRow
        .apply(
            "AggrAuditData",
            Group.<Row>byFieldNames("source_file")
//                .aggregateField(
//                    "bytes_inspected", new ByteValueCombineFn(), "total_bytes_inspected")
                .aggregateField("source_file", Count.combineFn(), "total_findings"))
        .apply("MergePartialStatsRow", MapElements.via(new MergeLogAggrMap()))
        .setRowSchema(Util.bqAuditSchema)
        .apply(
            Group.<Row>byFieldNames("source_file")
                .aggregateField("total_bytes_inspected", Sum.ofLongs(), "total_bytes_inspected")
                .aggregateField("total_findings", Sum.ofIntegers(), "total_findings"))
        .apply("MergeFullStatus", MapElements.via(new MergeFileStats()));
  }

  public class MergeLogAggrMap extends SimpleFunction<KV<Row, Row>, Row> {
    @Override
    public Row apply(KV<Row, Row> input) {
      Row aggrRow =
          Row.withSchema(Util.bqAuditSchema)
              .addValues(
                  input.getKey().getString("source_file"),
                  Util.getTimeStamp(),
                  input.getValue().getInt64("total_bytes_inspected").longValue(),
                  input.getValue().getInt64("total_findings").intValue(),
                  Util.INSPECTED)
              .build();
      LOG.info("Audit May Be Partial Row {}", aggrRow.toString());
      return aggrRow;
    }
  }

  public class MergeFileStats extends SimpleFunction<KV<Row, Row>, Row> {
    @Override
    public Row apply(KV<Row, Row> input) {
      Row aggrRow =
          Row.withSchema(Util.bqAuditSchema)
              .addValues(
                  input.getKey().getString("source_file"),
                  Util.getTimeStamp(),
                  input.getValue().getInt64("bytes_inspected").longValue(),
                  input.getValue().getInt32("total_findings").intValue(),
                  Util.INSPECTED)
              .build();
      LOG.info("Audit Row {}", aggrRow.toString());
      return aggrRow;
    }
  }
}