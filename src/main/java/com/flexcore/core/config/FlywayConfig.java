package com.flexcore.core.config;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig implements FlywayConfigurationCustomizer {

    @Override
    public void customize(FluentConfiguration configuration) {
        configuration.getPluginRegister()
                .getPlugin(PostgreSQLConfigurationExtension.class)
                .setTransactionalLock(false);
    }
}