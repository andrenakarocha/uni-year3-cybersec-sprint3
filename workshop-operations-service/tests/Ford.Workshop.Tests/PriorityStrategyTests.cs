using Ford.Workshop.Api.Application;
using Ford.Workshop.Api.Domain;

namespace Ford.Workshop.Tests;

public sealed class PriorityStrategyTests
{
    private readonly RiskBasedPriorityStrategy _strategy = new();

    [Theory]
    [InlineData("LOW", false, WorkOrderPriority.Normal)]
    [InlineData("high", false, WorkOrderPriority.Urgent)]
    [InlineData("CRITICAL", false, WorkOrderPriority.Critical)]
    [InlineData("LOW", true, WorkOrderPriority.Critical)]
    public void SelectsPriorityFromRiskAndImmobilization(string risk, bool immobilized, WorkOrderPriority expected) =>
        Assert.Equal(expected, _strategy.Select(risk, immobilized));
}

