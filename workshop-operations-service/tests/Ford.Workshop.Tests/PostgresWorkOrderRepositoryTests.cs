using Ford.Workshop.Api.Application;
using Ford.Workshop.Api.Infrastructure;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Diagnostics;
using Npgsql;

namespace Ford.Workshop.Tests;

public sealed class PostgresWorkOrderRepositoryTests
{
    [Theory]
    [InlineData(PostgresErrorCodes.UniqueViolation, "IX_work_orders_AppointmentId", true)]
    [InlineData(PostgresErrorCodes.UniqueViolation, "PK_work_orders", false)]
    [InlineData(PostgresErrorCodes.ConnectionFailure, "IX_work_orders_AppointmentId", false)]
    public async Task MapsOnlyAppointmentUniqueViolationToConflict(string sqlState, string constraint, bool conflict)
    {
        var failure = new DbUpdateException("database failure",
            new PostgresException("database failure", "ERROR", "ERROR", sqlState, constraintName: constraint));
        var options = new DbContextOptionsBuilder<WorkshopDbContext>()
            .UseNpgsql("Host=localhost;Database=unused;Username=unused")
            .AddInterceptors(new FailedSaveInterceptor(failure)).Options;
        using var context = new WorkshopDbContext(options);
        var repository = new PostgresWorkOrderRepository(context);
        if (conflict)
            await Assert.ThrowsAsync<WorkOrderConflictException>(() => repository.SaveChangesAsync(CancellationToken.None));
        else
            Assert.Same(failure, await Assert.ThrowsAsync<DbUpdateException>(
                () => repository.SaveChangesAsync(CancellationToken.None)));
    }

    private sealed class FailedSaveInterceptor(Exception failure) : SaveChangesInterceptor
    {
        public override ValueTask<InterceptionResult<int>> SavingChangesAsync(
            DbContextEventData eventData, InterceptionResult<int> result, CancellationToken cancellationToken = default) =>
            throw failure;
    }
}
