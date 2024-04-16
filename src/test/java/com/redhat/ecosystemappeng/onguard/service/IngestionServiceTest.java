/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.ecosystemappeng.onguard.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.redhat.ecosystemappeng.onguard.model.Ingestion;
import com.redhat.ecosystemappeng.onguard.model.Ingestion.Status;
import com.redhat.ecosystemappeng.onguard.repository.IngestionRepository;
import com.redhat.ecosystemappeng.onguard.repository.VulnerabilityRepository;
import com.redhat.ecosystemappeng.onguard.service.IngestionService.SkipSchedulerPredicate;
import com.redhat.ecosystemappeng.onguard.service.IngestionServiceTest.SchedulerProfile;
import com.redhat.ecosystemappeng.onguard.service.nvd.NvdService;
import com.redhat.ecosystemappeng.onguard.service.osv.OsvService;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.MockitoConfig;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(SchedulerProfile.class)
public class IngestionServiceTest {

  @InjectMock
  IngestionRepository repository;

  @Inject
  IngestionService svc;

  @InjectMock
  NvdService nvdSvc;

  @InjectMock
  OsvService osvSvc;

  @InjectMock
  VulnerabilityRepository vulnRepo;

  @InjectMock
  @MockitoConfig(convertScopes = true)
  SkipSchedulerPredicate skipScheduler;

  @Test
  void testPurgeUncompletedSyncs_noPreviousMigration() {
    when(repository.getSync()).thenReturn(Uni.createFrom().nullItem());
    when(skipScheduler.test(any())).thenReturn(Boolean.FALSE);

    svc.purgeUncompletedSyncs();

    verify(repository, times(0)).updateSync(any(Ingestion.class));
  }

  @Test
  void testPurgeUncompletedSyncs_CompletedMigration() {
    when(repository.getSync()).thenReturn(Uni.createFrom()
        .item(Ingestion.builder().started(LocalDateTime.now()).status(Status.COMPLETED).build()));
    when(skipScheduler.test(any())).thenReturn(Boolean.FALSE);
    
    svc.purgeUncompletedSyncs();

    verify(repository, times(0)).updateSync(any(Ingestion.class));
  }

  @Test
  void testPurgeUncompletedSyncs_CompletedWithErrors() {
    when(repository.getSync()).thenReturn(Uni.createFrom()
        .item(Ingestion.builder().started(LocalDateTime.now()).status(Status.PROCESSING).build()));
    when(skipScheduler.test(null)).thenReturn(Boolean.FALSE);
    
    svc.purgeUncompletedSyncs();

    verify(repository, times(1)).updateSync(assertArg(i -> Status.COMPLETED_WITH_ERRORS.equals(i.status())));
  }

  public static class SchedulerProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("onguard.ingester.schedule.every", "10s");
    }

  }

}
