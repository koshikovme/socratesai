package com.masters.socratesai.admin.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "administration_state")
@Getter @Setter @NoArgsConstructor
public class AdministrationState {
    @Id private Integer id;
    private boolean bootstrapCompleted;
}
