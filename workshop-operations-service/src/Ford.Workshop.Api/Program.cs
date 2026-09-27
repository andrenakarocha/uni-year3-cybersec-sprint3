using System.Text;
using System.Text.Json.Serialization;
using Ford.Workshop.Api.Application;
using Ford.Workshop.Api.Infrastructure;
using Ford.Workshop.Api.Web;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.WebUtilities;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;

var builder = WebApplication.CreateBuilder(args);
builder.Logging.ClearProviders();
builder.Logging.AddJsonConsole(options => options.IncludeScopes = true);
builder.Services.AddControllers().AddJsonOptions(options =>
    options.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter()))
    .ConfigureApiBehaviorOptions(options =>
    {
        options.SuppressMapClientErrors = true;
        options.InvalidModelStateResponseFactory = context =>
        {
            var detail = string.Join("; ", context.ModelState.Values.SelectMany(value => value.Errors)
                .Select(error => string.IsNullOrEmpty(error.ErrorMessage) ? "Invalid request body." : error.ErrorMessage));
            return new ObjectResult(ApiProblems.Create(context.HttpContext, 400, "Invalid request", detail))
            {
                StatusCode = 400,
                ContentTypes = { "application/problem+json" }
            };
        };
    });
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(options =>
{
    options.SwaggerDoc("v1", new OpenApiInfo
    {
        Title = "Ford Workshop Operations API",
        Version = "v1",
        Description = "Clean Architecture service for Zero Touch workshop execution."
    });
    options.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
    {
        Type = SecuritySchemeType.Http,
        Scheme = "bearer",
        BearerFormat = "JWT"
    });
    options.AddSecurityRequirement(new OpenApiSecurityRequirement
    {
        [new OpenApiSecurityScheme { Reference = new OpenApiReference { Type = ReferenceType.SecurityScheme, Id = "Bearer" } }] = Array.Empty<string>()
    });
});
builder.Services.AddDbContext<WorkshopDbContext>(options =>
    options.UseNpgsql(builder.Configuration.GetConnectionString("Postgres")));
builder.Services.AddScoped<IWorkOrderRepository, PostgresWorkOrderRepository>();
builder.Services.AddScoped<WorkOrderService>();
builder.Services.AddSingleton<IPriorityStrategy, RiskBasedPriorityStrategy>();
builder.Services.AddSingleton(TimeProvider.System);
builder.Services.AddExceptionHandler<ApiExceptionHandler>();
builder.Services.AddProblemDetails();
builder.Services.AddHealthChecks();

var secret = builder.Configuration["Jwt:Secret"] ?? throw new InvalidOperationException("JWT secret is required");
// HS256 exige chave de no mínimo 256 bits (RFC 7518 §3.2).
if (Encoding.UTF8.GetByteCount(secret) < 32)
    throw new InvalidOperationException("JWT secret must have at least 256 bits");
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme).AddJwtBearer(options =>
{
    options.MapInboundClaims = false;
    options.TokenValidationParameters = new TokenValidationParameters
    {
        ValidateIssuer = true,
        ValidIssuer = "ford-zero-touch",
        ValidateAudience = true,
        ValidAudience = "ford-api",
        ValidateLifetime = true,
        RequireExpirationTime = true,
        // Aceita só HS256: outro HMAC assinado com a mesma chave também seria aceito sem a allowlist.
        ValidAlgorithms = [SecurityAlgorithms.HmacSha256],
        ValidateIssuerSigningKey = true,
        IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(secret)),
        RoleClaimType = "roles",
        ClockSkew = TimeSpan.FromSeconds(30)
    };
    options.Events = new JwtBearerEvents
    {
        OnChallenge = context =>
        {
            SecurityAudit.TokenRejected(context.HttpContext, context.AuthenticateFailure?.GetType().Name ?? "MissingToken");
            return Task.CompletedTask;
        },
        OnForbidden = context =>
        {
            SecurityAudit.AccessDenied(context.HttpContext);
            return Task.CompletedTask;
        }
    };
});
builder.Services.AddAuthorization();

var app = builder.Build();
// Correlação: request_id do gateway entra no escopo de todo log desta requisição.
app.Use(async (context, next) =>
{
    var incoming = context.Request.Headers[SecurityAudit.RequestIdHeader].ToString();
    var requestId = SecurityAudit.SafeRequestId().IsMatch(incoming) ? incoming : Guid.NewGuid().ToString();
    context.Response.Headers[SecurityAudit.RequestIdHeader] = requestId;
    using (app.Logger.BeginScope(new Dictionary<string, object> { ["request_id"] = requestId }))
    {
        await next(context);
    }
});
app.UseExceptionHandler();
app.UseStatusCodePages(context =>
{
    var status = context.HttpContext.Response.StatusCode;
    var title = ReasonPhrases.GetReasonPhrase(status);
    return ApiProblems.WriteAsync(context.HttpContext, status, title, title);
});
app.UseSwagger();
app.UseSwaggerUI();
app.UseAuthentication();
app.UseAuthorization();
app.MapControllers();
app.MapHealthChecks("/health").AllowAnonymous();

await using (var scope = app.Services.CreateAsyncScope())
{
    var database = scope.ServiceProvider.GetRequiredService<WorkshopDbContext>().Database;
    await database.EnsureCreatedAsync();
}

await app.RunAsync();

public partial class Program;
