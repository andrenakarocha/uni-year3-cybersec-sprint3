using Ford.Workshop.Api.Domain;

namespace Ford.Workshop.Api.Application;

public interface IPriorityStrategy
{
    WorkOrderPriority Select(string riskLevel, bool vehicleImmobilized);
}

public sealed class RiskBasedPriorityStrategy : IPriorityStrategy
{
    public WorkOrderPriority Select(string riskLevel, bool vehicleImmobilized) => (riskLevel.ToUpperInvariant(), vehicleImmobilized) switch
    {
        (_, true) => WorkOrderPriority.Critical,
        ("CRITICAL", _) => WorkOrderPriority.Critical,
        ("HIGH", _) => WorkOrderPriority.Urgent,
        _ => WorkOrderPriority.Normal
    };
}

