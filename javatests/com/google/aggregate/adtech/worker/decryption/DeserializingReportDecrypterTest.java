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

package com.google.aggregate.adtech.worker.decryption;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.acai.Acai;
import com.google.acai.TestScoped;
import com.google.aggregate.adtech.worker.decryption.RecordDecrypter.DecryptionException;
import com.google.aggregate.adtech.worker.decryption.hybrid.HybridDecryptionCipherFactory;
import com.google.aggregate.adtech.worker.model.EncryptedReport;
import com.google.aggregate.adtech.worker.model.Report;
import com.google.aggregate.adtech.worker.model.serdes.PayloadSerdes;
import com.google.aggregate.adtech.worker.model.serdes.SharedInfoSerdes;
import com.google.aggregate.adtech.worker.model.serdes.cbor.CborPayloadSerdes;
import com.google.aggregate.adtech.worker.testing.FakeDecryptionKeyService;
import com.google.aggregate.adtech.worker.testing.FakeReportGenerator;
import com.google.aggregate.shared.mapper.TimeObjectMapper;
import com.google.aggregate.simulation.encryption.EncryptionCipher;
import com.google.aggregate.simulation.encryption.hybrid.HybridEncryptionCipher;
import com.google.common.io.ByteSource;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.scp.operator.cpio.cryptoclient.DecryptionKeyService;
import java.security.GeneralSecurityException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeserializingReportDecrypterTest {

  @Rule public final Acai acai = new Acai(TestEnv.class);

  // Under test
  @Inject private DeserializingReportDecrypter deserializingReportDecrypter;

  @Inject EncryptionCipher encryptionCipher;

  @Inject PayloadSerdes payloadSerdes;
  @Inject SharedInfoSerdes sharedInfoSerdes;

  // Report used in testing
  private Report report;

  // Should decrypt and deserialize without exceptions
  private EncryptedReport encryptedReport;

  // Should decrypt correctly but fail to deserialize
  private EncryptedReport garbageReportEncryptedWithCorrectKey;

  private static final String DECRYPTION_KEY_ID = "8dab7951-e459-4a19-bd6c-d81c0a600f86";

  private String sharedInfo;

  @Before
  public void setUp() throws Exception {
    report = FakeReportGenerator.generateWithParam(1, /* reportVersion */ "");
    sharedInfo = sharedInfoSerdes.reverse().convert(Optional.of(report.sharedInfo()));
    encryptReport();
  }

  /** Test for decrypting with shared info a single report with no errors */
  @Test
  public void testSimpleDecryption() throws Exception {
    // No setup

    Report decryptedReport = deserializingReportDecrypter.decryptSingleReport(encryptedReport);

    assertThat(decryptedReport).isEqualTo(report);
  }

  /** Test error handling for failed sharedInfo deserialization */
  @Test
  public void testExceptionInSharedInfoDeserialization() throws Exception {
    sharedInfo = "{ \"bad_field\": \"foo\" }";
    encryptReport();

    DecryptionException decryptionException =
        assertThrows(
            DecryptionException.class,
            () -> deserializingReportDecrypter.decryptSingleReport(encryptedReport));
    assertThat(decryptionException)
        .hasCauseThat()
        .hasMessageThat()
        .contains("Couldn't deserialize shared_info");
  }

  /** Test error handling for failed payload deserialization */
  @Test
  public void testExceptionInPayloadDeserialization() throws Exception {
    // No setup

    DecryptionException decryptionException =
        assertThrows(
            DecryptionException.class,
            () ->
                deserializingReportDecrypter.decryptSingleReport(
                    garbageReportEncryptedWithCorrectKey));
    assertThat(decryptionException)
        .hasCauseThat()
        .hasMessageThat()
        .contains("Decrypted payload could not be deserialized");
  }

  private void encryptReport() throws Exception {
    ByteSource serializedPayload = payloadSerdes.reverse().convert(Optional.of(report.payload()));
    encryptedReport =
        EncryptedReport.builder()
            .setPayload(encryptionCipher.encryptReport(serializedPayload, sharedInfo))
            .setKeyId(DECRYPTION_KEY_ID)
            .setSharedInfo(sharedInfo)
            .build();
    ByteSource garbageBytesEncryptedWithCorrectKey =
        encryptionCipher.encryptReport(ByteSource.wrap(new byte[] {0x00, 0x01}), sharedInfo);
    garbageReportEncryptedWithCorrectKey =
        EncryptedReport.builder()
            .setPayload(garbageBytesEncryptedWithCorrectKey)
            .setKeyId(DECRYPTION_KEY_ID)
            .setSharedInfo(sharedInfo)
            .build();
  }

  private static final class TestEnv extends AbstractModule {

    @Override
    protected void configure() {
      bind(ObjectMapper.class).to(TimeObjectMapper.class);
      bind(PayloadSerdes.class).to(CborPayloadSerdes.class);
      bind(DecryptionCipherFactory.class).to(HybridDecryptionCipherFactory.class);
      bind(FakeDecryptionKeyService.class).in(TestScoped.class);
      bind(DecryptionKeyService.class).to(FakeDecryptionKeyService.class);
    }

    @Provides
    EncryptionCipher provideEncryptionCipher(FakeDecryptionKeyService decryptionService)
        throws GeneralSecurityException {
      return HybridEncryptionCipher.of(
          decryptionService.getKeysetHandle(DECRYPTION_KEY_ID).getPublicKeysetHandle());
    }
  }
}
