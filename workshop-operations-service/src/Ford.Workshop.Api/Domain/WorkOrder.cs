namespace Ford.Workshop.Api.Domain;

public sealed class WorkOrder
{
    private WorkOrder() { }

    private WorkOrder(Guid id, Guid appointmentId, string vin, string description, WorkOrderPriority priority, DateTimeOffset now)
    {
        Id = id;
        AppointmentId = appointmentId;
        Vin = ValidateVin(vin);
        Description = ValidateDescription(description);
        Priority = priority;
        Status = WorkOrderStatus.Created;
        CreatedAt = UpdatedAt = now;
    }

    public Guid Id { get; private set; }
    public Guid AppointmentId { get; private set; }
    public string Vin { get; private set; } = null!;
    public string Description { get; private set; } = null!;
    public WorkOrderPriority Priority { get; private set; }
    public WorkOrderStatus Status { get; private set; }
    public DateTimeOffset CreatedAt { get; private set; }
    public DateTimeOffset UpdatedAt { get; private set; }
    public uint Version { get; private set; }

    public static WorkOrder Create(Guid appointmentId, string vin, string description, WorkOrderPriority priority, DateTimeOffset now)
    {
        if (appointmentId == Guid.Empty) throw new DomainException("Appointment is required");
        return new WorkOrder(Guid.NewGuid(), appointmentId, vin, description, priority, now);
    }

    public void TransitionTo(WorkOrderStatus target, DateTimeOffset now)
    {
        var state = WorkOrderStateFactory.For(Status);
        if (!state.CanTransitionTo(target))
            throw new DomainException($"Invalid work order transition from {Status} to {target}");
        Status = target;
        UpdatedAt = now;
    }

    private static string ValidateVin(string vin)
    {
        var normalized = vin?.ToUpperInvariant();
        if (normalized is null || !System.Text.RegularExpressions.Regex.IsMatch(normalized, "^[A-HJ-NPR-Z0-9]{17}$"))
            throw new DomainException("VIN must contain 17 valid characters");
        return normalized;
    }

    private static string ValidateDescription(string description)
    {
        if (string.IsNullOrWhiteSpace(description)) throw new DomainException("Description is required");
        return description.Trim();
    }
}

