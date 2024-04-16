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
package com.redhat.ecosystemappeng.onguard.repository.redis;

import java.util.Arrays;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.ecosystemappeng.onguard.model.Alias;
import com.redhat.ecosystemappeng.onguard.model.Ingestion;
import com.redhat.ecosystemappeng.onguard.model.Vulnerability;
import com.redhat.ecosystemappeng.onguard.repository.IngestionRepository;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.json.ReactiveJsonCommands;
import io.quarkus.redis.datasource.keys.KeyScanArgs;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.list.ReactiveListCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class IngestionRedisRepository implements IngestionRepository {

  private static final String SYNCS = "ingestions:updates";
  private static final int MAX_HISTORY = 100;

  private final ReactiveListCommands<String, Ingestion> syncCommands;
  private final ReactiveKeyCommands<String> keyCommands;
  private final ReactiveValueCommands<String, Alias> valueCommands;
  private final ReactiveJsonCommands<String> jsonCommands;
  private final ReactiveRedisDataSource ds;

  public IngestionRedisRepository(ReactiveRedisDataSource ds) {
    this.ds = ds;
    this.syncCommands = ds.list(String.class, Ingestion.class);
    this.keyCommands = ds.key();
    this.jsonCommands = ds.json(String.class);
    this.valueCommands = ds.value(Alias.class);
  }

  @Inject
  ObjectMapper mapper;

  @Override
  public Uni<Ingestion> getSync() {
    return syncCommands.lindex(SYNCS, 0);
  }

  @Override
  public Uni<Ingestion> saveSync(Ingestion ingestion) {
    return syncCommands.llen(SYNCS).chain(len -> {
      Uni<Ingestion> res = Uni.createFrom().item(ingestion);
      if (len == MAX_HISTORY) {
        res = syncCommands.rpop(SYNCS);
      }
      return res.chain(i -> syncCommands.lpush(SYNCS, ingestion)).replaceWith(ingestion);
    });
  }

  @Override
  public Uni<Ingestion> updateSync(Ingestion ingestion) {
    return syncCommands.lset(SYNCS, 0, ingestion).replaceWith(ingestion);
  }

  @Override
  public Uni<Void> deleteAll() {
    return syncCommands.getDataSource().flushall();
  }

  @Override
  public Multi<String> exportVulnerabilities() {
    return keyCommands.scan(new KeyScanArgs().match("cves:*").count(2000)).toMulti().onItem()
        .transformToUniAndMerge(this::toVulnerability).onItem().transform(v -> {
          try {
            return String.format("JSON.SET %s $ '%s'\n", v.cveId(), mapper.writeValueAsString(v));
          } catch (JsonProcessingException e) {
            return "-- " + e.getMessage();
          }
        });
  }

  @Override
  public Multi<String> exportAliases() {
    return keyCommands.scan(new KeyScanArgs().match("alias:*").count(2000)).toMulti().onItem()
        .transformToUniAndMerge(this::toAlias)
        .onItem()
        .transform(a -> String.format("SET %s '%s'\n", a.id(), a.cveId()));
  }

  @Override
  public Uni<String> exportIngestion() {
    return getSync().onItem().transform(i -> {
      try {
        return String.format("LPUSH %s '%s'", SYNCS, mapper.writeValueAsString(i));
      } catch (JsonProcessingException e) {
        return "-- " + e.getMessage();
      }
    });
  }

  @Override
  public Uni<Void> importScript(byte[] data) {
    return Uni.createFrom().item(new String(data)).onItem().transformToMulti(d -> Multi.createFrom().items(d.lines()))
        .onItem().invoke(line -> {
          if (line.startsWith("--")) {
            return;
          }
          var args = line.split(" ");
          if (args.length > 1) {
            ds.execute(args[0], Arrays.copyOfRange(args, 1, args.length));
          } else {
            ds.execute(args[0]);
          }
        }).onItem().ignoreAsUni();

  }

  private Uni<Vulnerability> toVulnerability(String key) {
    return jsonCommands
        .jsonGet(key, Vulnerability.class);
  }

  private Uni<Alias> toAlias(String key) {
    return valueCommands.get(key);
  }

}
