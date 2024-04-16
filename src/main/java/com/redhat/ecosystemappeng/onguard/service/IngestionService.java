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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

import com.redhat.ecosystemappeng.onguard.model.Ingestion;
import com.redhat.ecosystemappeng.onguard.model.nvd.NvdResponse;
import com.redhat.ecosystemappeng.onguard.model.nvd.NvdVulnerability;
import com.redhat.ecosystemappeng.onguard.repository.IngestionRepository;
import com.redhat.ecosystemappeng.onguard.repository.VulnerabilityRepository;
import com.redhat.ecosystemappeng.onguard.service.nvd.NvdService;
import com.redhat.ecosystemappeng.onguard.service.osv.OsvService;

import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import io.quarkus.scheduler.Scheduled.SkipPredicate;
import io.quarkus.scheduler.ScheduledExecution;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class IngestionService {

  private static final String INGESTER_PROFILE = "ingester";
  private static final Logger LOGGER = Logger.getLogger(IngestionService.class);

  @Inject
  IngestionRepository repository;

  @Inject
  NvdService nvdSvc;

  @Inject
  VulnerabilityRepository vulnRepository;

  @Inject
  OsvService osvService;

  @ConfigProperty(name = "onguard.ingester.pageSize", defaultValue = "1000")
  Integer pageSize;

  @Inject
  ExecutorService executor;

  @Inject
  SkipSchedulerPredicate skipScheduler;

  @Startup
  void purgeUncompletedSyncs() {
    if (!skipScheduler.test(null)) {
      repository.getSync().onItem().ifNotNull().transformToUni(i -> {
        if (Ingestion.Status.PROCESSING.equals(i.status())) {
          return repository.updateSync(Ingestion.builder(i).status(Ingestion.Status.COMPLETED_WITH_ERRORS).build());
        }
        return Uni.createFrom().item(i);
      }).subscribeAsCompletionStage().getNow(null);
    }
    return;
  }

  @Scheduled(every = "{onguard.ingester.schedule.every:1h}", skipExecutionIf = SkipSchedulerPredicate.class, delay = 10, delayUnit = TimeUnit.SECONDS, concurrentExecution = ConcurrentExecution.SKIP)
  Uni<Void> sync() {
    LOGGER.info("Loading vulnerabilities from NVD");
    return repository.getSync()
        .onItem().ifNull().continueWith(Ingestion.builder().build()).chain(this::ingestData);
  }

  public Uni<Ingestion> getStatus() {
    return repository.getSync();
  }

  public Uni<Void> deleteAll() {
    return repository.deleteAll();
  }

  private Uni<Void> ingestData(Ingestion previous) {
    var sync = Ingestion.builder().started(LocalDateTime.now()).status(Ingestion.Status.PROCESSING)
        .pageSize(pageSize);
    if (Ingestion.Status.COMPLETED.equals(previous.status())) {
      sync.since(previous.started()).index(0);
    } else if (Ingestion.Status.COMPLETED_WITH_ERRORS.equals(previous.status())) {
      sync.since(previous.since()).index(previous.index());
    }

    return repository.saveSync(sync.build()).chain(ingestion -> Multi.createBy().repeating().uni(
        () -> new AtomicInteger(ingestion.index()),
        state -> readNvdData(ingestion, state.getAndAdd(pageSize)))
        .until(r -> r.totalResults() == 0 || r.vulnerabilities() == null || r.vulnerabilities().isEmpty())
        .onItem().call(this::processPage)
        .onItem().call(resp -> this.updateSyncProgress(resp, ingestion))
        .onItem().ignore()
        .onCompletion().call(() -> {
          var completed = Ingestion.builder(ingestion).status(Ingestion.Status.COMPLETED).completed(LocalDateTime.now())
              .index(ingestion.total()).build();
          return repository.updateSync(completed);
        })
        .onFailure().call(e -> {
          LOGGER.error("Unable to load vulnerabilities", e);
          var errIng = Ingestion.builder(ingestion).status(Ingestion.Status.COMPLETED_WITH_ERRORS).build();
          return repository.updateSync(errIng);
        }).toUni());
  }

  private Uni<NvdResponse> readNvdData(Ingestion ingestion, int index) {
    return nvdSvc.bulkLoad(index, pageSize, ingestion.since())
        .onFailure().retry().withBackOff(Duration.ofSeconds(10)).expireIn(Duration.ofMinutes(5).toMillis())
        .onFailure().call(e -> {
          LOGGER.error("Unable to read page from NVD", e);
          var errBulk = Ingestion.builder(ingestion).completed(LocalDateTime.now())
              .status(Ingestion.Status.COMPLETED_WITH_ERRORS)
              .index(index).build();
          return repository.updateSync(errBulk);
        });
  }

  private Uni<Void> processPage(NvdResponse response) {
    Multi<NvdVulnerability> vulnMulti = Uni.createFrom().item(response).onItem().transform(NvdResponse::vulnerabilities)
        .onItem().disjoint();
    return vulnMulti.onItem()
        .transform(this::toCve)
        .onItem()
        .transformToUniAndMerge(this::ingestVulnerability)
        .toUni();
  }

  private Uni<NvdResponse> updateSyncProgress(NvdResponse response, Ingestion i) {
    var updated = Ingestion.builder(i).status(Ingestion.Status.PROCESSING).total(response.totalResults())
        .index(response.startIndex()).completed(null).pageSize(pageSize).build();

    return repository.updateSync(updated).replaceWith(response);
  }

  private String toCve(NvdVulnerability nvdVuln) {
    return nvdVuln.cve().id();
  }

  private Uni<Void> ingestVulnerability(String cve) {
    LOGGER.debugf("Ingest Vulnerability %s", cve);
    return osvService.get(cve)
        .onFailure(ClientWebApplicationException.class).recoverWithItem(error -> {
          var e = (ClientWebApplicationException) error;
          if (e.getResponse() != null && e.getResponse().getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
            LOGGER.debugf("Not found vulnerability: %s in OSV. Ignoring", cve);
            return null;
          }
          throw e;
        })
        .onItem().ifNotNull()
        .transformToUni(o -> vulnRepository.save(o))
        .onFailure()
        .retry()
        .withBackOff(Duration.ofSeconds(10))
        .expireIn(Duration.ofMinutes(5).toMillis());
  }

  @Singleton
  public static class SkipSchedulerPredicate implements SkipPredicate {

    @ConfigProperty(name = "quarkus.profile")
    String profile;

    public boolean test(ScheduledExecution execution) {
      return profile != null && !profile.contains(INGESTER_PROFILE);
    }
  }

  public Multi<String> exportVulnerabilities() {
    return repository.exportVulnerabilities();
  }

  public Multi<String> exportAliases() {
    return repository.exportAliases();
  }

  public Uni<String> exportIngestion() {
    return repository.exportIngestion();
  }

  public Uni<Void> importScript(byte[] data) {
    return repository.importScript(data);
  }

}
