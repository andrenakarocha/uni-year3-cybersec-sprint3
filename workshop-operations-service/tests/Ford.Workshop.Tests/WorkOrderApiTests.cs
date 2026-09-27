using System.IdentityModel.Tokens.Jwt;
using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Security.Claims;
using System.Text;
using System.Text.Json;
using Ford.Workshop.Api.Application;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.IdentityModel.Tokens;
using Moq;

namespace Ford.Workshop.Tests;

public sealed class WorkOrderApiTests(WorkshopApiFactory factory) : IClassFixture<WorkshopApiFactory>
{
    private const string Secret = WorkshopApiFactory.JwtSecret;

    private HttpClient Client(string? token = null)
    {
        var client = factory.CreateClient();
        if (token is not null) client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
        return client;
    }

    private static string Token(string role, bool expired = false, string secret = Secret,
        string algorithm = SecurityAlgorithms.HmacSha256)
    {
        var now = DateTime.UtcNow;
        return new JwtSecurityTokenHandler().WriteToken(new JwtSecurityToken(
            issuer: "ford-zero-touch", audience: "ford-api",
            claims: new[] { new Claim("sub", "api-test-user"), new Claim("roles", role) },
            notBefore: now.AddHours(-1), expires: expired ? now.AddMinutes(-5) : now.AddHours(1),
            signingCredentials: new SigningCredentials(new SymmetricSecurityKey(Encoding.UTF8.GetBytes(secret)), algorithm)));
    }

    [Fact]
    public async Task EchoesSafeRequestIdAndReplacesForgedOne()
    {
        using var client = Client();
        using var safe = new HttpRequestMessage(HttpMethod.Get, "/health");
        safe.Headers.Add("X-Request-ID", "gw-5f2c9a1e-trace");
        Assert.Equal("gw-5f2c9a1e-trace", (await client.SendAsync(safe)).Headers.GetValues("X-Request-ID").Single());

        using var forged = new HttpRequestMessage(HttpMethod.Get, "/health");
        forged.Headers.TryAddWithoutValidation("X-Request-ID", "forged level=ERROR");
        var replaced = (await client.SendAsync(forged)).Headers.GetValues("X-Request-ID").Single();
        Assert.DoesNotContain("forged", replaced);
        Assert.True(Guid.TryParse(replaced, out _));
    }

    [Fact]
    public void MasksEmailInAuditTrail()
    {
        Assert.Equal("cu***@ford.com", Ford.Workshop.Api.Web.SecurityAudit.Mask("customer@ford.com"));
        Assert.Equal("***", Ford.Workshop.Api.Web.SecurityAudit.Mask("api-test-user"));
    }

    [Fact]
    public async Task RejectsTokenSignedWithAlgorithmOutsideAllowlist()
    {
        using var client = Client(Token("ADMIN", algorithm: SecurityAlgorithms.HmacSha384));
        await AssertProblem(await client.GetAsync($"/api/v1/work-orders/{Guid.NewGuid()}"), HttpStatusCode.Unauthorized);
    }

    private static object Payload() => new
    {
        appointmentId = Guid.NewGuid(), vin = "1FMCU9GDXMUA12345", description = "Inspection",
        riskLevel = "HIGH", vehicleImmobilized = false
    };

    private static async Task AssertProblem(HttpResponseMessage response, HttpStatusCode status)
    {
        Assert.Equal(status, response.StatusCode);
        Assert.Equal("application/problem+json", response.Content.Headers.ContentType?.MediaType);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();
        Assert.Equal(new[] { "detail", "instance", "status", "title", "type" },
            body.EnumerateObject().Select(item => item.Name).Order().ToArray());
        Assert.Equal((int)status, body.GetProperty("status").GetInt32());
        Assert.Equal($"https://ford.example/problems/{(int)status}", body.GetProperty("type").GetString());
        Assert.False(string.IsNullOrWhiteSpace(body.GetProperty("title").GetString()));
        Assert.False(string.IsNullOrWhiteSpace(body.GetProperty("detail").GetString()));
        Assert.Equal(response.RequestMessage!.RequestUri!.AbsolutePath, body.GetProperty("instance").GetString());
    }

    [Theory]
    [InlineData("/health")]
    [InlineData("/api/v1/workshops")]
    [InlineData("/swagger/v1/swagger.json")]
    public async Task PublicEndpointsDoNotRequireToken(string path)
    {
        using var client = Client();
        Assert.Equal(HttpStatusCode.OK, (await client.GetAsync(path)).StatusCode);
    }

    [Fact]
    public async Task CreatesReadsAndTransitionsThroughHttpAndPersistence()
    {
        using var client = Client(Token("ADMIN"));
        var created = await client.PostAsJsonAsync("/api/v1/work-orders", Payload());
        Assert.Equal(HttpStatusCode.Created, created.StatusCode);
        Assert.NotNull(created.Headers.Location);
        var body = await created.Content.ReadFromJsonAsync<JsonElement>();
        var id = body.GetProperty("id").GetGuid();
        Assert.Equal("Created", body.GetProperty("status").GetString());
        Assert.Equal("Urgent", body.GetProperty("priority").GetString());

        using var customer = Client(Token("CUSTOMER"));
        var found = await customer.GetAsync($"/api/v1/work-orders/{id}");
        Assert.Equal(HttpStatusCode.OK, found.StatusCode);
        Assert.Equal(id, (await found.Content.ReadFromJsonAsync<JsonElement>()).GetProperty("id").GetGuid());

        using var technician = Client(Token("TECHNICIAN"));
        var transitioned = await technician.PostAsJsonAsync($"/api/v1/work-orders/{id}/transitions", new { target = "Diagnosing" });
        Assert.Equal(HttpStatusCode.OK, transitioned.StatusCode);
        Assert.Equal("Diagnosing", (await transitioned.Content.ReadFromJsonAsync<JsonElement>()).GetProperty("status").GetString());
        var persisted = await customer.GetFromJsonAsync<JsonElement>($"/api/v1/work-orders/{id}");
        Assert.Equal("Diagnosing", persisted.GetProperty("status").GetString());
    }

    [Fact]
    public async Task ProtectedEndpointWithoutTokenReturns401()
    {
        using var client = Client();
        var response = await client.GetAsync($"/api/v1/work-orders/{Guid.NewGuid()}");
        await AssertProblem(response, HttpStatusCode.Unauthorized);
        Assert.Contains(response.Headers.WwwAuthenticate, item => item.Scheme == "Bearer");
    }

    [Theory]
    [InlineData("malformed")]
    [InlineData("expired")]
    [InlineData("wrong-signature")]
    public async Task InvalidTokensReturn401(string scenario)
    {
        var token = scenario switch
        {
            "expired" => Token("ADMIN", expired: true),
            "wrong-signature" => Token("ADMIN", secret: new string('x', 80)),
            _ => "not-a-jwt"
        };
        using var client = Client(token);
        await AssertProblem(await client.GetAsync($"/api/v1/work-orders/{Guid.NewGuid()}"), HttpStatusCode.Unauthorized);
    }

    [Theory]
    [InlineData("CUSTOMER")]
    [InlineData("TECHNICIAN")]
    [InlineData("VEHICLE")]
    public async Task RolesWithoutCreatePermissionReturn403(string role)
    {
        using var client = Client(Token(role));
        await AssertProblem(await client.PostAsJsonAsync("/api/v1/work-orders", Payload()), HttpStatusCode.Forbidden);
    }

    [Fact]
    public async Task AdviserCanCreateButCannotTransition()
    {
        using var client = Client(Token("ADVISER"));
        var created = await client.PostAsJsonAsync("/api/v1/work-orders", Payload());
        Assert.Equal(HttpStatusCode.Created, created.StatusCode);
        var id = (await created.Content.ReadFromJsonAsync<JsonElement>()).GetProperty("id").GetGuid();
        await AssertProblem(await client.PostAsJsonAsync($"/api/v1/work-orders/{id}/transitions", new { target = "Diagnosing" }), HttpStatusCode.Forbidden);
    }

    [Theory]
    [InlineData("{")]
    [InlineData("{}")]
    public async Task InvalidRequestBodyReturns400(string body)
    {
        using var client = Client(Token("ADMIN"));
        await AssertProblem(await client.PostAsync("/api/v1/work-orders", new StringContent(body, Encoding.UTF8, "application/json")), HttpStatusCode.BadRequest);
    }

    [Fact]
    public async Task UnsupportedContentTypeReturns415()
    {
        using var client = Client(Token("ADMIN"));
        await AssertProblem(await client.PostAsync("/api/v1/work-orders", new StringContent("invalid", Encoding.UTF8, "text/plain")), HttpStatusCode.UnsupportedMediaType);
    }

    [Fact]
    public async Task UnknownEndpointReturns404()
    {
        using var client = Client();
        await AssertProblem(await client.GetAsync("/api/v1/missing"), HttpStatusCode.NotFound);
    }

    [Fact]
    public async Task InvalidBusinessInputReturns422()
    {
        using var client = Client(Token("ADMIN"));
        var payload = new { appointmentId = Guid.NewGuid(), vin = "INVALID", description = "Inspection", riskLevel = "HIGH", vehicleImmobilized = false };
        await AssertProblem(await client.PostAsJsonAsync("/api/v1/work-orders", payload), HttpStatusCode.UnprocessableEntity);
    }

    [Fact]
    public async Task MissingWorkOrderReturns404()
    {
        using var client = Client(Token("ADMIN"));
        await AssertProblem(await client.GetAsync($"/api/v1/work-orders/{Guid.NewGuid()}"), HttpStatusCode.NotFound);
    }

    [Fact]
    public async Task InvalidTransitionReturns422WithoutChangingState()
    {
        using var client = Client(Token("ADMIN"));
        var created = await client.PostAsJsonAsync("/api/v1/work-orders", Payload());
        var id = (await created.Content.ReadFromJsonAsync<JsonElement>()).GetProperty("id").GetGuid();
        await AssertProblem(await client.PostAsJsonAsync($"/api/v1/work-orders/{id}/transitions", new { target = "Completed" }), HttpStatusCode.UnprocessableEntity);
        Assert.Equal("Created", (await client.GetFromJsonAsync<JsonElement>($"/api/v1/work-orders/{id}")).GetProperty("status").GetString());
    }

    [Fact]
    public async Task DuplicateAppointmentReturns409AndPreservesOriginalOrder()
    {
        using var client = Client(Token("ADMIN"));
        var payload = Payload();
        var created = await client.PostAsJsonAsync("/api/v1/work-orders", payload);
        Assert.Equal(HttpStatusCode.Created, created.StatusCode);
        var original = await created.Content.ReadFromJsonAsync<JsonElement>();
        await AssertProblem(await client.PostAsJsonAsync("/api/v1/work-orders", payload), HttpStatusCode.Conflict);
        var found = await client.GetFromJsonAsync<JsonElement>($"/api/v1/work-orders/{original.GetProperty("id").GetGuid()}");
        Assert.Equal(original.GetRawText(), found.GetRawText());
    }

    [Fact]
    public async Task UnexpectedFailureReturns500WithoutLeakingException()
    {
        var repository = new Mock<IWorkOrderRepository>();
        repository.Setup(item => item.FindAsync(It.IsAny<Guid>(), It.IsAny<CancellationToken>()))
            .ThrowsAsync(new InvalidOperationException("private database connection"));
        using var failingFactory = factory.WithWebHostBuilder(builder => builder.ConfigureServices(services =>
        {
            services.RemoveAll<IWorkOrderRepository>();
            services.AddSingleton(repository.Object);
        }));
        using var client = failingFactory.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", Token("ADMIN"));
        var response = await client.GetAsync($"/api/v1/work-orders/{Guid.NewGuid()}");
        await AssertProblem(response, HttpStatusCode.InternalServerError);
        Assert.DoesNotContain("private database connection", await response.Content.ReadAsStringAsync(), StringComparison.Ordinal);
    }
}
