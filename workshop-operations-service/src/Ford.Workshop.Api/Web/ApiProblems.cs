using System.Text.Json;
using Microsoft.AspNetCore.Mvc;

namespace Ford.Workshop.Api.Web;

public static class ApiProblems
{
    public static ProblemDetails Create(HttpContext context, int status, string title, string detail) => new()
    {
        Status = status,
        Title = title,
        Detail = detail,
        Type = $"https://ford.example/problems/{status}",
        Instance = context.Request.Path
    };

    public static Task WriteAsync(HttpContext context, int status, string title, string detail)
    {
        context.Response.StatusCode = status;
        return context.Response.WriteAsJsonAsync(Create(context, status, title, detail),
            options: (JsonSerializerOptions?)null, contentType: "application/problem+json",
            cancellationToken: context.RequestAborted);
    }
}
