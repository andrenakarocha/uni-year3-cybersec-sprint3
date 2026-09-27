using Ford.Workshop.Api.Infrastructure;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;

namespace Ford.Workshop.Tests;

public sealed class WorkshopApiFactory : WebApplicationFactory<Program>
{
    private readonly SqliteConnection _connection = new("Data Source=:memory:");

    public WorkshopApiFactory() => _connection.Open();

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.ConfigureServices(services =>
        {
            services.RemoveAll<WorkshopDbContext>();
            services.RemoveAll<DbContextOptions<WorkshopDbContext>>();
            services.AddSingleton(new DbContextOptionsBuilder<WorkshopDbContext>()
                .UseSqlite(_connection).Options);
            services.AddScoped<WorkshopDbContext>();
        });
    }

    protected override void Dispose(bool disposing)
    {
        base.Dispose(disposing);
        if (disposing) _connection.Dispose();
    }
}
