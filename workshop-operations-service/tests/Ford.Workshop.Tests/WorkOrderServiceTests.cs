using Ford.Workshop.Api.Application;
using Ford.Workshop.Api.Domain;
using Moq;

namespace Ford.Workshop.Tests;

public sealed class WorkOrderServiceTests
{
    private static readonly DateTimeOffset Now = new(2026, 9, 26, 12, 0, 0, TimeSpan.Zero);
    private readonly Mock<IWorkOrderRepository> _repository = new();
    private readonly Mock<IPriorityStrategy> _priority = new();
    private readonly WorkOrderService _service;

    public WorkOrderServiceTests()
    {
        _service = new WorkOrderService(_repository.Object, _priority.Object, new FixedTimeProvider(Now));
    }

    [Fact]
    public async Task CreatesPersistsAndMapsWorkOrder()
    {
        var command = new CreateWorkOrder(Guid.NewGuid(), "1FMCU9GDXMUA12345", "Oil", "HIGH", false);
        _priority.Setup(item => item.Select("HIGH", false)).Returns(WorkOrderPriority.Urgent);
        var result = await _service.CreateAsync(command, CancellationToken.None);
        Assert.Equal(WorkOrderPriority.Urgent, result.Priority);
        Assert.Equal(Now, result.CreatedAt);
        _repository.Verify(item => item.AddAsync(It.IsAny<WorkOrder>(), CancellationToken.None), Times.Once);
        _repository.Verify(item => item.SaveChangesAsync(CancellationToken.None), Times.Once);
    }

    [Fact]
    public async Task DuplicateAppointmentDoesNotWriteOrCalculatePriority()
    {
        var command = new CreateWorkOrder(Guid.NewGuid(), "1FMCU9GDXMUA12345", "Oil", "HIGH", false);
        _repository.Setup(item => item.ExistsByAppointmentIdAsync(command.AppointmentId, CancellationToken.None))
            .ReturnsAsync(true);
        await Assert.ThrowsAsync<WorkOrderConflictException>(() => _service.CreateAsync(command, CancellationToken.None));
        _repository.Verify(item => item.AddAsync(It.IsAny<WorkOrder>(), It.IsAny<CancellationToken>()), Times.Never);
        _repository.Verify(item => item.SaveChangesAsync(It.IsAny<CancellationToken>()), Times.Never);
        _priority.VerifyNoOtherCalls();
    }

    [Fact]
    public async Task FindsExistingWorkOrder()
    {
        var entity = NewOrder();
        _repository.Setup(item => item.FindAsync(entity.Id, CancellationToken.None)).ReturnsAsync(entity);
        var result = await _service.FindAsync(entity.Id, CancellationToken.None);
        Assert.Equal(entity.Id, result.Id);
        Assert.Equal(entity.AppointmentId, result.AppointmentId);
    }

    [Fact]
    public async Task TransitionsAndSavesExistingWorkOrder()
    {
        var entity = NewOrder();
        _repository.Setup(item => item.FindAsync(entity.Id, CancellationToken.None)).ReturnsAsync(entity);
        var result = await _service.TransitionAsync(entity.Id, WorkOrderStatus.Diagnosing, CancellationToken.None);
        Assert.Equal(WorkOrderStatus.Diagnosing, result.Status);
        Assert.Equal(Now, result.UpdatedAt);
        _repository.Verify(item => item.SaveChangesAsync(CancellationToken.None), Times.Once);
    }

    [Fact]
    public async Task MissingWorkOrderRaisesTypedException()
    {
        var id = Guid.NewGuid();
        _repository.Setup(item => item.FindAsync(id, CancellationToken.None)).ReturnsAsync((WorkOrder?)null);
        var error = await Assert.ThrowsAsync<WorkOrderNotFoundException>(
            () => _service.FindAsync(id, CancellationToken.None));
        Assert.Contains(id.ToString(), error.Message, StringComparison.Ordinal);
    }

    private static WorkOrder NewOrder() =>
        WorkOrder.Create(Guid.NewGuid(), "1FMCU9GDXMUA12345", "Inspection", WorkOrderPriority.Normal, Now);

    private sealed class FixedTimeProvider(DateTimeOffset now) : TimeProvider
    {
        public override DateTimeOffset GetUtcNow() => now;
    }
}
