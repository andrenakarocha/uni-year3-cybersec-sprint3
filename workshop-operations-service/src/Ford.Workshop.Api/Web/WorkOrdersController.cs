using Ford.Workshop.Api.Application;
using Ford.Workshop.Api.Domain;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace Ford.Workshop.Api.Web;

[ApiController]
[Route("api/v1/work-orders")]
[Authorize]
public sealed class WorkOrdersController(WorkOrderService service) : ControllerBase
{
    [HttpPost]
    [Authorize(Roles = "ADVISER,ADMIN")]
    [ProducesResponseType<WorkOrderView>(StatusCodes.Status201Created)]
    public async Task<ActionResult<WorkOrderView>> Create(CreateWorkOrder request, CancellationToken cancellationToken)
    {
        var created = await service.CreateAsync(request, cancellationToken);
        SecurityAudit.CriticalChange(HttpContext, "workorder.created", created.Id, $"status={created.Status}");
        return CreatedAtAction(nameof(Find), new { id = created.Id }, created);
    }

    [HttpGet("{id:guid}")]
    [Authorize(Roles = "CUSTOMER,ADVISER,TECHNICIAN,ADMIN")]
    [ProducesResponseType<WorkOrderView>(StatusCodes.Status200OK)]
    public async Task<ActionResult<WorkOrderView>> Find(Guid id, CancellationToken cancellationToken) =>
        Ok(await service.FindAsync(id, cancellationToken));

    [HttpPost("{id:guid}/transitions")]
    [Authorize(Roles = "TECHNICIAN,ADMIN")]
    public async Task<ActionResult<WorkOrderView>> Transition(
        Guid id, TransitionRequest request, CancellationToken cancellationToken)
    {
        var updated = await service.TransitionAsync(id, request.Target, cancellationToken);
        SecurityAudit.CriticalChange(HttpContext, "workorder.status.changed", id, $"status={updated.Status}");
        return Ok(updated);
    }

    public sealed record TransitionRequest(WorkOrderStatus Target);
}

