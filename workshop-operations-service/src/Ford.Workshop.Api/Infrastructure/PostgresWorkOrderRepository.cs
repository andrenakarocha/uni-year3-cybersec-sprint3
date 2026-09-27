using Ford.Workshop.Api.Application;
using Ford.Workshop.Api.Domain;
using Microsoft.EntityFrameworkCore;
using Npgsql;

namespace Ford.Workshop.Api.Infrastructure;

public sealed class PostgresWorkOrderRepository(WorkshopDbContext context) : IWorkOrderRepository
{
    public async Task AddAsync(WorkOrder workOrder, CancellationToken cancellationToken) =>
        await context.WorkOrders.AddAsync(workOrder, cancellationToken);

    public Task<WorkOrder?> FindAsync(Guid id, CancellationToken cancellationToken) =>
        context.WorkOrders.SingleOrDefaultAsync(item => item.Id == id, cancellationToken);

    public Task<bool> ExistsByAppointmentIdAsync(Guid appointmentId, CancellationToken cancellationToken) =>
        context.WorkOrders.AnyAsync(item => item.AppointmentId == appointmentId, cancellationToken);

    public async Task SaveChangesAsync(CancellationToken cancellationToken)
    {
        try
        {
            await context.SaveChangesAsync(cancellationToken);
        }
        catch (DbUpdateException error) when (error.InnerException is PostgresException
               { SqlState: PostgresErrorCodes.UniqueViolation, ConstraintName: "IX_work_orders_AppointmentId" })
        {
            throw new WorkOrderConflictException();
        }
    }
}
