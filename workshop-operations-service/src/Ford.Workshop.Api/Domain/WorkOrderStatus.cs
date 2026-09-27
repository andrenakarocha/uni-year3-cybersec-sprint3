namespace Ford.Workshop.Api.Domain;

public enum WorkOrderStatus
{
    Created,
    Diagnosing,
    AwaitingApproval,
    InProgress,
    QualityCheck,
    Completed,
    Cancelled
}

public enum WorkOrderPriority { Normal, Urgent, Critical }

