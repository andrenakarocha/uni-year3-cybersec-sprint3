using Ford.Workshop.Api.Domain;
using Microsoft.EntityFrameworkCore;

namespace Ford.Workshop.Api.Infrastructure;

public sealed class WorkshopDbContext(DbContextOptions<WorkshopDbContext> options) : DbContext(options)
{
    public DbSet<WorkOrder> WorkOrders => Set<WorkOrder>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        var entity = modelBuilder.Entity<WorkOrder>();
        entity.ToTable("work_orders");
        entity.HasKey(item => item.Id);
        entity.Property(item => item.Vin).HasMaxLength(17).IsRequired();
        entity.Property(item => item.Description).HasMaxLength(500).IsRequired();
        entity.Property(item => item.Status).HasConversion<string>().HasMaxLength(32);
        entity.Property(item => item.Priority).HasConversion<string>().HasMaxLength(16);
        entity.Property(item => item.Version).IsConcurrencyToken();
        entity.HasIndex(item => item.AppointmentId).IsUnique();
        entity.HasIndex(item => item.Vin);
    }
}

