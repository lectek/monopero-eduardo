package br.com.minimercadinho.saas.application.core.settings;

import br.com.minimercadinho.saas.adapters.outbound.persistence.entity.AppSettingEntity;
import br.com.minimercadinho.saas.adapters.outbound.persistence.repository.AppSettingRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppSettingService {
    private final AppSettingRepository repository;

    public AppSettingService(AppSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly=true)
    public Page<AppSettingEntity> list(String q, Pageable pageable) {
        return this.repository.search(q, pageable);
    }

    @Transactional
    public AppSettingEntity create(String key, String value, String description) {
        if (this.repository.existsBySettingKey(key)) {
            throw new IllegalArgumentException("J\u00e1 existe uma configura\u00e7\u00e3o com a chave: " + key);
        }
        AppSettingEntity entity = new AppSettingEntity(key, value, description);
        return (AppSettingEntity)this.repository.save(entity);
    }

    @Transactional
    public AppSettingEntity update(Long id, String key, String value, String description) {
        AppSettingEntity entity = (AppSettingEntity)this.repository.findById(id).orElseThrow(() -> new NoSuchElementException("Config n\u00e3o encontrada: id=" + id));
        if (!entity.getSettingKey().equals(key) && this.repository.existsBySettingKey(key)) {
            throw new IllegalArgumentException("J\u00e1 existe uma configura\u00e7\u00e3o com a chave: " + key);
        }
        entity.setSettingKey(key);
        entity.setSettingValue(value);
        entity.setDescription(description);
        return (AppSettingEntity)this.repository.save(entity);
    }

    @Transactional
    public void delete(Long id) {
        this.repository.deleteById(id);
    }

    @Transactional
    public AppSettingEntity upsert(String key, String value, String description) {
        return this.repository.findBySettingKey(key).map(existing -> {
            existing.setSettingKey(key);
            existing.setSettingValue(value);
            existing.setDescription(description);
            return (AppSettingEntity)this.repository.save(existing);
        }).orElseGet(() -> (AppSettingEntity)this.repository.save(new AppSettingEntity(key, value, description)));
    }

    @Transactional(readOnly=true)
    public Optional<AppSettingEntity> findById(Long id) {
        return this.repository.findById(id);
    }

    @Transactional(readOnly=true)
    public Optional<String> get(String key) {
        return this.repository.findBySettingKey(key).map(AppSettingEntity::getSettingValue);
    }

    @Transactional(readOnly=true)
    public Map<String, String> getAllByKeys(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new HashMap<>();
        for (AppSettingEntity entity : this.repository.findBySettingKeyIn(keys)) {
            if (entity.getSettingKey() != null && entity.getSettingValue() != null) {
                out.put(entity.getSettingKey(), entity.getSettingValue());
            }
        }
        return out;
    }

    @Transactional(readOnly=true)
    public String getOrDefault(String key, String defaultValue) {
        return this.get(key).orElse(defaultValue);
    }

    @Transactional(readOnly=true)
    public String get(String key, String defaultValue) {
        return this.getOrDefault(key, defaultValue);
    }

    @Transactional(readOnly=true)
    public boolean getBoolean(String key, boolean defaultValue) {
        return this.get(key).map(v -> {
            String s = v.trim().toLowerCase();
            return "true".equals(s) || "1".equals(s) || "yes".equals(s) || "on".equals(s);
        }).orElse(defaultValue);
    }

    @Transactional(readOnly=true)
    public int getInt(String key, int defaultValue) {
        return this.get(key).map(v -> {
            try {
                return Integer.parseInt(v.trim());
            }
            catch (NumberFormatException e) {
                return defaultValue;
            }
        }).orElse(defaultValue);
    }

    @Transactional(readOnly=true)
    public long getLong(String key, long defaultValue) {
        return this.get(key).map(v -> {
            try {
                return Long.parseLong(v.trim());
            }
            catch (NumberFormatException e) {
                return defaultValue;
            }
        }).orElse(defaultValue);
    }

    @Transactional(readOnly=true)
    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        return this.get(key).map(v -> {
            try {
                return new BigDecimal(v.trim());
            }
            catch (Exception e) {
                return defaultValue;
            }
        }).orElse(defaultValue);
    }
}
