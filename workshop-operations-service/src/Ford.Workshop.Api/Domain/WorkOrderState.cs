namespace Ford.Workshop.Api.Domain;

public interface IWorkOrderState
{
    WorkOrderStatus Status { get; }
    bool CanTransitionTo(WorkOrderStatus target);
}

public static class WorkOrderStateFactory
{
    public static IWorkOrderState For(WorkOrderStatus status) => status switch
    {
        WorkOrderStatus.Created => new CreatedState(),
        WorkOrderStatus.Diagnosing => new DiagnosingState(),
        WorkOrderStatus.AwaitingApproval => new AwaitingApprovalState(),
        WorkOrderStatus.InProgress => new InProgressState(),
        WorkOrderStatus.QualityCheck => new QualityCheckState(),
        WorkOrderStatus.Completed => new TerminalState(WorkOrderStatus.Completed),
        WorkOrderStatus.Cancelled => new TerminalState(WorkOrderStatus.Cancelled),
        _ => throw new DomainException("Unknown work order status")
    };
}

file abstract record ActiveState(WorkOrderStatus Status, params WorkOrderStatus[] Allowed) : IWorkOrderState
{
    public bool CanTransitionTo(WorkOrderStatus target) => target == WorkOrderStatus.Cancelled || Allowed.Contains(target);
}

file sealed record CreatedState() : ActiveState(WorkOrderStatus.Created, WorkOrderStatus.Diagnosing);
file sealed record DiagnosingState() : ActiveState(WorkOrderStatus.Diagnosing, WorkOrderStatus.AwaitingApproval, WorkOrderStatus.InProgress);
file sealed record AwaitingApprovalState() : ActiveState(WorkOrderStatus.AwaitingApproval, WorkOrderStatus.InProgress);
file sealed record InProgressState() : ActiveState(WorkOrderStatus.InProgress, WorkOrderStatus.QualityCheck);
file sealed record QualityCheckState() : ActiveState(WorkOrderStatus.QualityCheck, WorkOrderStatus.InProgress, WorkOrderStatus.Completed);
file sealed record TerminalState(WorkOrderStatus Status) : IWorkOrderState
{
    public bool CanTransitionTo(WorkOrderStatus target) => false;
}

