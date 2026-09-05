package io.github.klisman99.collectorsauctionplatform.identity;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class InitialAdministratorInitializer implements ApplicationRunner {

    private final InitialAdministratorProperties properties;
    private final InitialAdministratorService service;

    InitialAdministratorInitializer(
            InitialAdministratorProperties properties,
            InitialAdministratorService service) {
        this.properties = properties;
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        service.ensureConfiguredAdministrator(properties.email(), properties.password());
    }
}
