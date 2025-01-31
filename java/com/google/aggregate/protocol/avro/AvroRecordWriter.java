/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.aggregate.protocol.avro;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericRecord;

/**
 * Reader that writes reports to an Avro file following the defined schema.
 *
 * <p>The schema is provided by the schema supplier. For convenience, the writer object can be
 * created through the factory, which allows the supplier to be bound with dependency injection,
 * thus requiring only the input stream to be passed.
 */
public abstract class AvroRecordWriter<Record> implements AutoCloseable {

  private final DataFileWriter<GenericRecord> avroWriter;
  private final OutputStream outStream;
  private final AvroSchemaSupplier schemaSupplier;

  /**
   * Creates a writer based on the given Avro writer and schema supplier (where Avro writer should
   * *NOT* be open, just initialized; check the Avro docs for details)
   */
  AvroRecordWriter(
      DataFileWriter<GenericRecord> avroWriter,
      OutputStream outStream,
      AvroSchemaSupplier schemaSupplier) {
    this.avroWriter = avroWriter;
    this.outStream = outStream;
    this.schemaSupplier = schemaSupplier;
  }

  /** Logic to construct an Avro {@code GenericRecord} from generic Java type {@code Record}. */
  public abstract GenericRecord serializeRecordToGeneric(Record record, Schema avroSchema)
      throws IOException;

  /** Writes out records with the given {@link AvroReportRecord} */
  public void writeRecords(ImmutableList<MetadataElement> metadata, ImmutableList<Record> records)
      throws IOException {
    Schema schema = schemaSupplier.get();

    metadata.forEach(meta -> avroWriter.setMeta(meta.key(), meta.value()));

    try (DataFileWriter<GenericRecord> streamWriter = avroWriter.create(schema, outStream)) {
      for (Record record : records) {
        GenericRecord genericAvroRecord = serializeRecordToGeneric(record, schema);
        streamWriter.append(genericAvroRecord);
      }

      streamWriter.flush();
      outStream.flush();
    }
  }

  @Override
  public void close() throws IOException {
    avroWriter.close();
  }

  /** One metadata element (key/value string pair) */
  @AutoValue
  public abstract static class MetadataElement {

    public static MetadataElement create(String key, String value) {
      return new AutoValue_AvroRecordWriter_MetadataElement(key, value);
    }

    public abstract String key();

    public abstract String value();
  }
}
