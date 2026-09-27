package br.com.fiap.ford.journey.web;

import br.com.fiap.ford.journey.application.IntelligencePort;
import br.com.fiap.ford.journey.application.JourneyService;
import br.com.fiap.ford.journey.domain.JourneyStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/journeys")
class JourneyController {
    private final JourneyService service;

    JourneyController(JourneyService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADVISER','ADMIN')")
    ResponseEntity<JourneyService.JourneyView> create(@Valid @RequestBody CreateJourneyRequest request) {
        var created = service.create(request.customerId(), request.vin());
        return ResponseEntity.created(URI.create("/api/v1/journeys/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADVISER','ADMIN')")
    JourneyService.JourneyView find(@PathVariable UUID id) {
        return service.find(id);
    }

    @PostMapping("/{id}/transitions")
    @PreAuthorize("hasAnyRole('ADVISER','ADMIN')")
    JourneyService.JourneyView transition(
            @PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
        return service.transition(id, request.target(), request.nextAction());
    }

    @PostMapping("/{id}/recommendation")
    @PreAuthorize("hasAnyRole('ADVISER','ADMIN')")
    JourneyService.RecommendationView recommend(
            @PathVariable UUID id, @Valid @RequestBody TelemetryRequest request) {
        return service.recommend(id, request.toCommand());
    }

    record CreateJourneyRequest(@NotNull UUID customerId, @NotBlank String vin) {}
    record TransitionRequest(@NotNull JourneyStatus target, @NotBlank String nextAction) {}
    record TelemetryRequest(
            @NotBlank String vin,
            @NotNull @DecimalMin("0") @DecimalMax("2000000") Double odometerKm,
            @NotNull @DecimalMin("0") @DecimalMax("100") Double oilLifePercent,
            @NotNull @DecimalMin("0") @DecimalMax("30") Double batteryVoltage,
            @NotNull @DecimalMin("-50") @DecimalMax("250") Double engineTemperatureC,
            @Size(max = 50) List<@NotNull String> diagnosticCodes) {
        IntelligencePort.TelemetryCommand toCommand() {
            return new IntelligencePort.TelemetryCommand(
                    vin, odometerKm, oilLifePercent, batteryVoltage, engineTemperatureC,
                    diagnosticCodes == null ? List.of() : List.copyOf(diagnosticCodes));
        }
    }
}
