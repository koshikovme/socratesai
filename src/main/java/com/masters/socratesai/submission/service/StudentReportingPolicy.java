package com.masters.socratesai.submission.service;

import com.masters.socratesai.user.model.User;
import com.masters.socratesai.user.model.UserRole;
import jakarta.validation.constraints.*;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Operator-managed immutable account IDs, independent of editable names/emails. History is retained. */
@Component @ConfigurationProperties("app.reporting") @Validated @Getter @Setter
public class StudentReportingPolicy {
    @NotNull @Size(max = 1000)
    private Set<@NotNull @Positive Long> internalUserIds = Set.of();

    public boolean includes(User user) {
        return user.getRole() == UserRole.STUDENT && !internalUserIds.contains(user.getId());
    }
}
