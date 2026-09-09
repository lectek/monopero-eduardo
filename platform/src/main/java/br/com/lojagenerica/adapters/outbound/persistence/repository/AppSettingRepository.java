package br.com.lojagenerica.adapters.outbound.persistence.repository;

import br.com.lojagenerica.adapters.outbound.persistence.entity.AppSettingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppSettingRepository extends JpaRepository<AppSettingEntity, Long> {

    Optional<AppSettingEntity> findBySettingKey(String settingKey);

    boolean existsBySettingKey(String settingKey);

    java.util.List<AppSettingEntity> findBySettingKeyIn(java.util.Collection<String> keys);

    @Query("""
        select s
        from AppSettingEntity s
        where (:q is null or :q = ''
               or lower(s.settingKey) like lower(concat('%', :q, '%'))
               or lower(s.description) like lower(concat('%', :q, '%')))
        order by s.settingKey asc
        """)
    Page<AppSettingEntity> search(@Param("q") String q, Pageable pageable);
}
