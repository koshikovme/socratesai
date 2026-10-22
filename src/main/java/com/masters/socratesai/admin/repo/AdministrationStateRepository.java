package com.masters.socratesai.admin.repo;

import com.masters.socratesai.admin.model.AdministrationState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AdministrationStateRepository extends JpaRepository<AdministrationState, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from AdministrationState state where state.id = 1")
    AdministrationState lockState();
}
