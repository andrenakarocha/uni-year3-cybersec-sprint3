using Ford.Workshop.Api.Domain;

namespace Ford.Workshop.Api.Application;

public sealed class WorkOrderService(IWorkOrderRepository repository, IPriorityStrategy priorityStrategy, TimeProvider timeProvider)
{
    public async Task<WorkOrderView> CreateAsync(CreateWorkOrder command, CancellationToken cancellationToken)
    {
        if (await repository.ExistsByAppointmentIdAsync(command.AppointmentId, cancellationToken))
            throw new WorkOrderConflictException();
        var priority = priorityStrategy.Select(command.RiskLevel, command.VehicleImmobilized);
        var entity = WorkOrder.Create(command.AppointmentId, command.Vin, command.Description, priority, timeProvider.GetUtcNow());
        await repository.AddAsync(entity, cancellationToken);
        await repository.SaveChangesAsync(cancellationToken);
        return WorkOrderView.From(entity);
    }

    public async Task<WorkOrderView> FindAsync(Guid id, CancellationToken cancellationToken) =>
        WorkOrderView.From(await LoadAsync(id, cancellationToken));

    public async Task<WorkOrderView> TransitionAsync(Guid id, WorkOrderStatus target, CancellationToken cancellationToken)
    {
        var entity = await LoadAsync(id, cancellationToken);
        entity.TransitionTo(target, timeProvider.GetUtcNow());
        await repository.SaveChangesAsync(cancellationToken);
        return WorkOrderView.From(entity);
    }

    private async Task<WorkOrder> LoadAsync(Guid id, CancellationToken cancellationToken) =>
        await repository.FindAsync(id, cancellationToken) ?? throw new WorkOrderNotFoundException(id);
}

public sealed class WorkOrderNotFoundException(Guid id) : Exception($"Work order not found: {id}");

public sealed class WorkOrderConflictException() : Exception("Appointment already has a work order");

public sealed record CreateWorkOrder(Guid AppointmentId, string Vin, string Description, string RiskLevel, bool VehicleImmobilized);

public sealed record WorkOrderView(Guid Id, Guid AppointmentId, string Vin, string Description, WorkOrderPriority Priority, WorkOrderStatus Status, DateTimeOffset CreatedAt, DateTimeOffset UpdatedAt)
{
    public static WorkOrderView From(WorkOrder item) => new(
        item.Id, item.AppointmentId, item.Vin, item.Description, item.Priority, item.Status, item.CreatedAt, item.UpdatedAt);
}
