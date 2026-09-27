using Ford.Workshop.Api.Domain;

namespace Ford.Workshop.Tests;

public sealed class WorkOrderTests
{
    private static readonly DateTimeOffset Now = new(2026, 9, 26, 12, 0, 0, TimeSpan.Zero);
    private const string Vin = "1FMCU9GDXMUA12345";

    [Fact]
    public void PrivateConstructorSupportsEntityFrameworkMaterialization()
    {
        var entity = Activator.CreateInstance(typeof(WorkOrder), nonPublic: true);
        Assert.IsType<WorkOrder>(entity);
    }

    [Fact]
    public void CreateBuildsValidAggregate()
    {
        var appointment = Guid.NewGuid();
        var result = WorkOrder.Create(appointment, Vin.ToLowerInvariant(), " Oil change ", WorkOrderPriority.Urgent, Now);
        Assert.NotEqual(Guid.Empty, result.Id);
        Assert.Equal(appointment, result.AppointmentId);
        Assert.Equal(Vin, result.Vin);
        Assert.Equal("Oil change", result.Description);
        Assert.Equal(WorkOrderPriority.Urgent, result.Priority);
        Assert.Equal(WorkOrderStatus.Created, result.Status);
        Assert.Equal(Now, result.CreatedAt);
        Assert.Equal(Now, result.UpdatedAt);
    }

    [Fact]
    public void CreateRejectsInvalidInputs()
    {
        Assert.Throws<DomainException>(() => WorkOrder.Create(Guid.Empty, Vin, "ok", WorkOrderPriority.Normal, Now));
        Assert.Throws<DomainException>(() => WorkOrder.Create(Guid.NewGuid(), "bad", "ok", WorkOrderPriority.Normal, Now));
        Assert.Throws<DomainException>(() => WorkOrder.Create(Guid.NewGuid(), null!, "ok", WorkOrderPriority.Normal, Now));
        Assert.Throws<DomainException>(() => WorkOrder.Create(Guid.NewGuid(), Vin, " ", WorkOrderPriority.Normal, Now));
    }

    [Fact]
    public void SupportsCompleteHappyPath()
    {
        var order = NewOrder();
        Transition(order, WorkOrderStatus.Diagnosing);
        Transition(order, WorkOrderStatus.AwaitingApproval);
        Transition(order, WorkOrderStatus.InProgress);
        Transition(order, WorkOrderStatus.QualityCheck);
        Transition(order, WorkOrderStatus.Completed);
        Assert.Equal(WorkOrderStatus.Completed, order.Status);
        Assert.Throws<DomainException>(() => order.TransitionTo(WorkOrderStatus.Cancelled, Now));
    }

    [Fact]
    public void SupportsDiagnosisShortcutAndQualityRework()
    {
        var order = NewOrder();
        Transition(order, WorkOrderStatus.Diagnosing);
        Transition(order, WorkOrderStatus.InProgress);
        Transition(order, WorkOrderStatus.QualityCheck);
        Transition(order, WorkOrderStatus.InProgress);
        Assert.Equal(WorkOrderStatus.InProgress, order.Status);
    }

    [Theory]
    [InlineData(WorkOrderStatus.Created)]
    [InlineData(WorkOrderStatus.Diagnosing)]
    [InlineData(WorkOrderStatus.AwaitingApproval)]
    [InlineData(WorkOrderStatus.InProgress)]
    [InlineData(WorkOrderStatus.QualityCheck)]
    public void ActiveStatesCanBeCancelled(WorkOrderStatus initial)
    {
        var state = WorkOrderStateFactory.For(initial);
        Assert.True(state.CanTransitionTo(WorkOrderStatus.Cancelled));
    }

    [Fact]
    public void RejectsInvalidAndUnknownTransitions()
    {
        var order = NewOrder();
        Assert.Throws<DomainException>(() => order.TransitionTo(WorkOrderStatus.Completed, Now));
        Assert.False(WorkOrderStateFactory.For(WorkOrderStatus.Cancelled).CanTransitionTo(WorkOrderStatus.Created));
        Assert.Throws<DomainException>(() => WorkOrderStateFactory.For((WorkOrderStatus)999));
    }

    private static WorkOrder NewOrder() =>
        WorkOrder.Create(Guid.NewGuid(), Vin, "Inspection", WorkOrderPriority.Normal, Now);

    private static void Transition(WorkOrder order, WorkOrderStatus target) =>
        order.TransitionTo(target, order.UpdatedAt.AddMinutes(1));
}
