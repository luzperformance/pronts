package br.com.primeiroprontuario.auth;

import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
class DoctorAccountProvisioner implements ApplicationRunner {

    private final DoctorAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String configuredUsername;
    private final String configuredPassword;

    DoctorAccountProvisioner(
            DoctorAccountRepository accounts,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${app.doctor.username}") String configuredUsername,
            @Value("${app.doctor.password}") String configuredPassword) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.configuredUsername = configuredUsername;
        this.configuredPassword = configuredPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!StringUtils.hasText(configuredUsername) || !StringUtils.hasText(configuredPassword)) {
            throw new IllegalStateException("Doctor credentials must be provided by secure configuration");
        }

        var existingAccounts = accounts.findAll();
        if (existingAccounts.size() > 1) {
            throw new IllegalStateException("The MVP supports exactly one doctor account");
        }
        if (existingAccounts.isEmpty()) {
            accounts.save(new DoctorAccount(
                    UUID.randomUUID(),
                    configuredUsername,
                    passwordEncoder.encode(configuredPassword),
                    clock.instant()));
            return;
        }

        var account = existingAccounts.getFirst();
        if (!account.getUsername().equals(configuredUsername)) {
            throw new IllegalStateException("Configured doctor does not match the provisioned account");
        }
        if (!passwordEncoder.matches(configuredPassword, account.getPasswordHash())) {
            account.changePasswordHash(passwordEncoder.encode(configuredPassword));
        }
    }
}
