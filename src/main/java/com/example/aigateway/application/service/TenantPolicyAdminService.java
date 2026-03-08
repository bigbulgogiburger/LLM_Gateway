package com.example.aigateway.application.service;

import com.example.aigateway.application.dto.TenantPolicyOverrideHistoryItem;
import com.example.aigateway.common.exception.GatewayErrorCodes;
import com.example.aigateway.common.exception.GatewayException;
import com.example.aigateway.infrastructure.config.GatewayTenantPolicyProperties;
import com.example.aigateway.infrastructure.policy.TenantPolicyOverrideEntity;
import com.example.aigateway.infrastructure.policy.TenantPolicyOverrideHistoryEntity;
import com.example.aigateway.infrastructure.policy.TenantPolicyOverrideHistoryRepository;
import com.example.aigateway.infrastructure.policy.TenantPolicyOverrideRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TenantPolicyAdminService {

    private final TenantPolicyOverrideRepository repository;
    private final TenantPolicyOverrideHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    public TenantPolicyAdminService(
            TenantPolicyOverrideRepository repository,
            TenantPolicyOverrideHistoryRepository historyRepository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.objectMapper = objectMapper;
    }

    public static TenantPolicyAdminService inMemory(ObjectMapper objectMapper) {
        return new TenantPolicyAdminService(
                new InMemoryTenantPolicyOverrideRepository(),
                new InMemoryTenantPolicyOverrideHistoryRepository(),
                objectMapper
        );
    }

    public GatewayTenantPolicyProperties.TenantPolicyOverride get(String tenantId) {
        return repository.findByTenantId(tenantId)
                .map(TenantPolicyOverrideEntity::getOverrideJson)
                .map(this::deserialize)
                .orElse(null);
    }

    public GatewayTenantPolicyProperties.TenantPolicyOverride put(
            String tenantId,
            GatewayTenantPolicyProperties.TenantPolicyOverride override
    ) {
        return saveOverride(tenantId, override, "UPSERT", null);
    }

    public List<TenantPolicyOverrideHistoryItem> history(String tenantId) {
        return historyRepository.findByTenantIdOrderByVersionDesc(tenantId).stream()
                .map(entity -> new TenantPolicyOverrideHistoryItem(
                        entity.getVersion(),
                        entity.getAction(),
                        entity.getSourceVersion(),
                        entity.getCreatedAt(),
                        deserialize(entity.getOverrideJson())
                ))
                .toList();
    }

    public GatewayTenantPolicyProperties.TenantPolicyOverride rollback(String tenantId, long version) {
        TenantPolicyOverrideHistoryEntity versionEntity = historyRepository.findByTenantIdAndVersion(tenantId, version)
                .orElseThrow(() -> new GatewayException(
                        GatewayErrorCodes.RESOURCE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "요청한 tenant policy version을 찾을 수 없습니다."
                ));
        return saveOverride(tenantId, deserialize(versionEntity.getOverrideJson()), "ROLLBACK", version);
    }

    private GatewayTenantPolicyProperties.TenantPolicyOverride saveOverride(
            String tenantId,
            GatewayTenantPolicyProperties.TenantPolicyOverride override,
            String action,
            Long sourceVersion
    ) {
        String json = serialize(override);
        Instant now = Instant.now();
        TenantPolicyOverrideEntity entity = repository.findByTenantId(tenantId)
                .map(existing -> new TenantPolicyOverrideEntity(existing.getId(), tenantId, json, now))
                .orElseGet(() -> new TenantPolicyOverrideEntity(tenantId, json, now));
        repository.save(entity);

        long nextVersion = historyRepository.findTopByTenantIdOrderByVersionDesc(tenantId)
                .map(history -> history.getVersion() + 1L)
                .orElse(1L);
        historyRepository.save(new TenantPolicyOverrideHistoryEntity(
                tenantId,
                nextVersion,
                json,
                action,
                sourceVersion,
                now
        ));
        return override;
    }

    private String serialize(GatewayTenantPolicyProperties.TenantPolicyOverride override) {
        try {
            return objectMapper.writeValueAsString(override);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Tenant policy override 직렬화에 실패했습니다.", exception);
        }
    }

    private GatewayTenantPolicyProperties.TenantPolicyOverride deserialize(String json) {
        try {
            return objectMapper.readValue(json, GatewayTenantPolicyProperties.TenantPolicyOverride.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Tenant policy override 역직렬화에 실패했습니다.", exception);
        }
    }

    private static final class InMemoryTenantPolicyOverrideRepository implements TenantPolicyOverrideRepository {
        private final ConcurrentHashMap<String, TenantPolicyOverrideEntity> store = new ConcurrentHashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        @Override
        public Optional<TenantPolicyOverrideEntity> findByTenantId(String tenantId) {
            return Optional.ofNullable(store.get(tenantId));
        }

        @Override
        public <S extends TenantPolicyOverrideEntity> S save(S entity) {
            Long id = entity.getId() != null ? entity.getId() : sequence.incrementAndGet();
            @SuppressWarnings("unchecked")
            S stored = (S) new TenantPolicyOverrideEntity(id, entity.getTenantId(), entity.getOverrideJson(), entity.getUpdatedAt());
            store.put(stored.getTenantId(), stored);
            return stored;
        }

        @Override
        public List<TenantPolicyOverrideEntity> findAll() {
            return store.values().stream().toList();
        }

        @Override
        public List<TenantPolicyOverrideEntity> findAllById(Iterable<Long> ids) {
            throw unsupported();
        }

        @Override
        public <S extends TenantPolicyOverrideEntity> List<S> saveAll(Iterable<S> entities) {
            throw unsupported();
        }

        @Override
        public Optional<TenantPolicyOverrideEntity> findById(Long id) {
            return store.values().stream().filter(entity -> id.equals(entity.getId())).findFirst();
        }

        @Override
        public boolean existsById(Long id) {
            return findById(id).isPresent();
        }

        @Override
        public long count() {
            return store.size();
        }

        @Override
        public void deleteById(Long id) {
            findById(id).ifPresent(entity -> store.remove(entity.getTenantId()));
        }

        @Override
        public void delete(TenantPolicyOverrideEntity entity) {
            store.remove(entity.getTenantId());
        }

        @Override
        public void deleteAllById(Iterable<? extends Long> ids) {
            throw unsupported();
        }

        @Override
        public void deleteAll(Iterable<? extends TenantPolicyOverrideEntity> entities) {
            entities.forEach(this::delete);
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        @Override
        public void flush() {
        }

        @Override
        public <S extends TenantPolicyOverrideEntity> S saveAndFlush(S entity) {
            return save(entity);
        }

        @Override
        public <S extends TenantPolicyOverrideEntity> List<S> saveAllAndFlush(Iterable<S> entities) {
            throw unsupported();
        }

        @Override
        public void deleteAllInBatch(Iterable<TenantPolicyOverrideEntity> entities) {
            deleteAll(entities);
        }

        @Override
        public void deleteAllByIdInBatch(Iterable<Long> ids) {
            throw unsupported();
        }

        @Override
        public void deleteAllInBatch() {
            store.clear();
        }

        @Override
        public TenantPolicyOverrideEntity getOne(Long id) {
            return getReferenceById(id);
        }

        @Override
        public TenantPolicyOverrideEntity getById(Long id) {
            return getReferenceById(id);
        }

        @Override
        public TenantPolicyOverrideEntity getReferenceById(Long id) {
            return findById(id).orElseThrow(this::unsupported);
        }

        @Override
        public <S extends TenantPolicyOverrideEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> example) {
            throw unsupported();
        }

        @Override
        public <S extends TenantPolicyOverrideEntity> List<S> findAll(org.springframework.data.domain.Example<S> example) {
            throw unsupported();
        }

        @Override
        public <S extends TenantPolicyOverrideEntity> List<S> findAll(
                org.springframework.data.domain.Example<S> example,
                org.springframework.data.domain.Sort sort
        ) {
            throw unsupported();
        }

        @Override
        public <S extends TenantPolicyOverrideEntity> org.springframework.data.domain.Page<S> findAll(
                org.springframework.data.domain.Example<S> example,
                org.springframework.data.domain.Pageable pageable
        ) {
            throw unsupported();
        }

        @Override
        public <S extends TenantPolicyOverrideEntity> long count(org.springframework.data.domain.Example<S> example) {
            throw unsupported();
        }

        @Override
        public <S extends TenantPolicyOverrideEntity> boolean exists(org.springframework.data.domain.Example<S> example) {
            throw unsupported();
        }

        @Override
        public <S extends TenantPolicyOverrideEntity, R> R findBy(
                org.springframework.data.domain.Example<S> example,
                java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction
        ) {
            throw unsupported();
        }

        @Override
        public List<TenantPolicyOverrideEntity> findAll(org.springframework.data.domain.Sort sort) {
            throw unsupported();
        }

        @Override
        public org.springframework.data.domain.Page<TenantPolicyOverrideEntity> findAll(org.springframework.data.domain.Pageable pageable) {
            throw unsupported();
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("test-only repository");
        }
    }

    private static final class InMemoryTenantPolicyOverrideHistoryRepository implements TenantPolicyOverrideHistoryRepository {
        private final ConcurrentHashMap<Long, TenantPolicyOverrideHistoryEntity> store = new ConcurrentHashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        @Override
        public Optional<TenantPolicyOverrideHistoryEntity> findTopByTenantIdOrderByVersionDesc(String tenantId) {
            return store.values().stream()
                    .filter(entity -> entity.getTenantId().equals(tenantId))
                    .sorted((left, right) -> Long.compare(right.getVersion(), left.getVersion()))
                    .findFirst();
        }

        @Override
        public Optional<TenantPolicyOverrideHistoryEntity> findByTenantIdAndVersion(String tenantId, Long version) {
            return store.values().stream()
                    .filter(entity -> entity.getTenantId().equals(tenantId) && entity.getVersion().equals(version))
                    .findFirst();
        }

        @Override
        public List<TenantPolicyOverrideHistoryEntity> findByTenantIdOrderByVersionDesc(String tenantId) {
            return store.values().stream()
                    .filter(entity -> entity.getTenantId().equals(tenantId))
                    .sorted((left, right) -> Long.compare(right.getVersion(), left.getVersion()))
                    .toList();
        }

        @Override
        public <S extends TenantPolicyOverrideHistoryEntity> S save(S entity) {
            store.put(sequence.incrementAndGet(), entity);
            return entity;
        }

        @Override
        public List<TenantPolicyOverrideHistoryEntity> findAll() {
            return store.values().stream().toList();
        }

        @Override
        public List<TenantPolicyOverrideHistoryEntity> findAllById(Iterable<Long> ids) {
            throw unsupported();
        }

        @Override
        public <S extends TenantPolicyOverrideHistoryEntity> List<S> saveAll(Iterable<S> entities) {
            throw unsupported();
        }

        @Override
        public Optional<TenantPolicyOverrideHistoryEntity> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public boolean existsById(Long id) {
            return store.containsKey(id);
        }

        @Override
        public long count() {
            return store.size();
        }

        @Override
        public void deleteById(Long id) {
            store.remove(id);
        }

        @Override
        public void delete(TenantPolicyOverrideHistoryEntity entity) {
            store.entrySet().removeIf(entry -> entry.getValue().equals(entity));
        }

        @Override
        public void deleteAllById(Iterable<? extends Long> ids) {
            ids.forEach(store::remove);
        }

        @Override
        public void deleteAll(Iterable<? extends TenantPolicyOverrideHistoryEntity> entities) {
            entities.forEach(this::delete);
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        @Override
        public void flush() {
        }

        @Override
        public <S extends TenantPolicyOverrideHistoryEntity> S saveAndFlush(S entity) {
            return save(entity);
        }

        @Override
        public <S extends TenantPolicyOverrideHistoryEntity> List<S> saveAllAndFlush(Iterable<S> entities) {
            throw unsupported();
        }

        @Override
        public void deleteAllInBatch(Iterable<TenantPolicyOverrideHistoryEntity> entities) {
            deleteAll(entities);
        }

        @Override
        public void deleteAllByIdInBatch(Iterable<Long> ids) {
            deleteAllById(ids);
        }

        @Override
        public void deleteAllInBatch() {
            store.clear();
        }

        @Override
        public TenantPolicyOverrideHistoryEntity getOne(Long id) {
            return getReferenceById(id);
        }

        @Override
        public TenantPolicyOverrideHistoryEntity getById(Long id) {
            return getReferenceById(id);
        }

        @Override
        public TenantPolicyOverrideHistoryEntity getReferenceById(Long id) {
            return findById(id).orElseThrow(this::unsupported);
        }

        @Override
        public <S extends TenantPolicyOverrideHistoryEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> example) {
            throw unsupported();
        }

        @Override
        public <S extends TenantPolicyOverrideHistoryEntity> List<S> findAll(org.springframework.data.domain.Example<S> example) {
            throw unsupported();
        }

        @Override
        public <S extends TenantPolicyOverrideHistoryEntity> List<S> findAll(
                org.springframework.data.domain.Example<S> example,
                org.springframework.data.domain.Sort sort
        ) {
            throw unsupported();
        }

        @Override
        public <S extends TenantPolicyOverrideHistoryEntity> org.springframework.data.domain.Page<S> findAll(
                org.springframework.data.domain.Example<S> example,
                org.springframework.data.domain.Pageable pageable
        ) {
            throw unsupported();
        }

        @Override
        public <S extends TenantPolicyOverrideHistoryEntity> long count(org.springframework.data.domain.Example<S> example) {
            throw unsupported();
        }

        @Override
        public <S extends TenantPolicyOverrideHistoryEntity> boolean exists(org.springframework.data.domain.Example<S> example) {
            throw unsupported();
        }

        @Override
        public <S extends TenantPolicyOverrideHistoryEntity, R> R findBy(
                org.springframework.data.domain.Example<S> example,
                java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction
        ) {
            throw unsupported();
        }

        @Override
        public List<TenantPolicyOverrideHistoryEntity> findAll(org.springframework.data.domain.Sort sort) {
            throw unsupported();
        }

        @Override
        public org.springframework.data.domain.Page<TenantPolicyOverrideHistoryEntity> findAll(org.springframework.data.domain.Pageable pageable) {
            throw unsupported();
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("test-only repository");
        }
    }
}
