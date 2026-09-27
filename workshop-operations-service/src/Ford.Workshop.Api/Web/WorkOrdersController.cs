using System.Security.Claims;
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
    public async Task<ActionResult<WorkOrderView>> Find(Guid id, CancellationToken cancellationToken)
    {
        var workOrder = await service.FindAsync(id, cancellationToken);
        if (!CanRead(User, workOrder.Vin))
        {
            SecurityAudit.ObjectAccessDenied(HttpContext, id);
            // 404 e não 403: não confirma a existência de ordens de outros clientes.
            throw new WorkOrderNotFoundException(id);
        }
        return Ok(workOrder);
    }

    // OWASP API1:2023 (BOLA): equipe atende qualquer veículo; CUSTOMER só os do claim "vins".
    private static readonly string[] StaffRoles = ["ADVISER", "TECHNICIAN", "ADMIN"];

    private static bool CanRead(ClaimsPrincipal user, string vin) =>
        StaffRoles.Any(user.IsInRole)
        || user.FindAll("vins").Any(claim => string.Equals(claim.Value, vin, StringComparison.OrdinalIgnoreCase));

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

