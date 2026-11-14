package com.maxieventos.lugares.repository;

import com.maxieventos.lugares.entity.Lugar;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LugarRepository extends JpaRepository<Lugar, UUID>, JpaSpecificationExecutor<Lugar> {
}
