using Ford.Workshop.Api.Application;
using Ford.Workshop.Api.Domain;
using Microsoft.AspNetCore.Diagnostics;

namespace Ford.Workshop.Api.Web;

public sealed class ApiExceptionHandler : IExceptionHandler
{
    public async ValueTask<bool> TryHandleAsync(HttpContext context, Exception exception, CancellationToken cancellationToken)
    {
        var (status, title) = exception switch
        {
            WorkOrderNotFoundException => (StatusCodes.Status404NotFound, "Work order not found"),
            WorkOrderConflictException => (StatusCodes.Status409Conflict, "Work order conflict"),
            DomainException => (StatusCodes.Status422UnprocessableEntity, "Business rule violation"),
            _ => (StatusCodes.Status500InternalServerError, "Unexpected error")
        };
        await ApiProblems.WriteAsync(context, status, title,
            status == 500 ? "An unexpected error occurred." : exception.Message);
        return true;
    }
}
