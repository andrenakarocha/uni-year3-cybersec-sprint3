using System.Text.RegularExpressions;

namespace Ford.Workshop.Api.Web;

/// <summary>
/// Trilha de segurança estruturada na categoria "security.audit" (JSON no console). Mesmos nomes
/// de evento dos serviços Java e Python, para uma única regra de alerta cobrir os três.
/// </summary>
public static partial class SecurityAudit
{
    public const string RequestIdHeader = "X-Request-ID";

    // X-Request-ID vem do gateway; valor fora do formato é trocado para não permitir injeção de log.
    [GeneratedRegex("^[A-Za-z0-9-]{8,64}$")]
    public static partial Regex SafeRequestId();

    public static void TokenRejected(HttpContext context, string reason) =>
        Logger(context).LogWarning(
            "{EventAction} {EventCategory} {EventOutcome} {EventReason} {HttpMethod} {UrlPath} {SourceIp}",
            "auth.token.rejected", "authentication", "failure", reason,
            context.Request.Method, context.Request.Path.Value, SourceIp(context));

    public static void AccessDenied(HttpContext context) =>
        Logger(context).LogWarning(
            "{EventAction} {EventCategory} {EventOutcome} {UserName} {HttpMethod} {UrlPath} {SourceIp}",
            "authz.denied", "authorization", "denied", UserName(context),
            context.Request.Method, context.Request.Path.Value, SourceIp(context));

    public static void ObjectAccessDenied(HttpContext context, Guid resourceId) =>
        Logger(context).LogWarning(
            "{EventAction} {EventCategory} {EventOutcome} {UserName} {ResourceType} {ResourceId} {UrlPath} {SourceIp}",
            "authz.object.denied", "authorization", "denied", UserName(context), "work_order", resourceId,
            context.Request.Path.Value, SourceIp(context));

    public static void CriticalChange(HttpContext context, string action, Guid resourceId, string detail) =>
        Logger(context).LogInformation(
            "{EventAction} {EventCategory} {EventOutcome} {UserName} {ResourceType} {ResourceId} {ChangeDetail}",
            action, "configuration", "success", UserName(context), "work_order", resourceId, detail);

    public static string Mask(string email)
    {
        var at = email.IndexOf('@');
        return at <= 0 ? "***" : email[..Math.Min(2, at)] + "***" + email[at..];
    }

    private static ILogger Logger(HttpContext context) =>
        context.RequestServices.GetRequiredService<ILoggerFactory>().CreateLogger("security.audit");

    // X-Real-IP é sobrescrito pelo gateway; confiável porque o serviço não publica porta.
    private static string SourceIp(HttpContext context) =>
        context.Request.Headers["X-Real-IP"].FirstOrDefault()
        ?? context.Connection.RemoteIpAddress?.ToString() ?? "unknown";

    private static string UserName(HttpContext context) =>
        Mask(context.User.FindFirst("sub")?.Value ?? "anonymous");
}
