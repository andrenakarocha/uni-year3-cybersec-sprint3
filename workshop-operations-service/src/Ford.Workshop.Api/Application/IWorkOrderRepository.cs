using Ford.Workshop.Api.Domain;

namespace Ford.Workshop.Api.Application;

public interface IWorkOrderRepository
{
    Task AddAsync(WorkOrder workOrder, CancellationToken cancellationToken);
    Task<WorkOrder?> FindAsync(Guid id, CancellationToken cancellationToken);
    Task<bool> ExistsByAppointmentIdAsync(Guid appointmentId, CancellationToken cancellationToken);
    Task SaveChangesAsync(CancellationToken cancellationToken);
}
