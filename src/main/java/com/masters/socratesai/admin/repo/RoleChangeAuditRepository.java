package com.masters.socratesai.admin.repo;

import com.masters.socratesai.admin.model.RoleChangeAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleChangeAuditRepository extends JpaRepository<RoleChangeAudit, Long> {}
