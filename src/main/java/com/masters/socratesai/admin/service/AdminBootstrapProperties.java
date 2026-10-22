package com.masters.socratesai.admin.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties("app.admin.bootstrap")
public class AdminBootstrapProperties {
    private boolean enabled;
    private String email;
    private String password;
    private String fullName;
}
