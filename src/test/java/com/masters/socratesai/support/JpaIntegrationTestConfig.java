package com.masters.socratesai.support;

import com.masters.socratesai.interaction.model.InteractionLog;
import com.masters.socratesai.interaction.repo.InteractionLogRepository;
import com.masters.socratesai.interaction.service.InteractionLogService;
import com.masters.socratesai.session.model.StudentTaskSession;
import com.masters.socratesai.session.repo.StudentTaskSessionRepository;
import com.masters.socratesai.session.service.StudentTaskSessionService;
import com.masters.socratesai.task.model.Task;
import com.masters.socratesai.task.repo.TaskRepository;
import com.masters.socratesai.task.service.TaskService;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@TestConfiguration
@EnableTransactionManagement
@EnableJpaRepositories(basePackageClasses = {
        TaskRepository.class,
        StudentTaskSessionRepository.class,
        InteractionLogRepository.class
})
@ComponentScan(basePackageClasses = {
        TaskService.class,
        StudentTaskSessionService.class,
        InteractionLogService.class
})
public class JpaIntegrationTestConfig {

    @Bean
    DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
        bean.setDataSource(dataSource);
        bean.setPackagesToScan(
                Task.class.getPackageName(),
                StudentTaskSession.class.getPackageName(),
                InteractionLog.class.getPackageName()
        );
        bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        properties.put("hibernate.show_sql", "false");
        bean.setJpaPropertyMap(properties);
        return bean;
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
