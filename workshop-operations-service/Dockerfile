FROM mcr.microsoft.com/dotnet/sdk:8.0-alpine AS builder
WORKDIR /src
COPY . .
RUN dotnet publish src/Ford.Workshop.Api/Ford.Workshop.Api.csproj -c Release -o /app --no-self-contained

FROM mcr.microsoft.com/dotnet/aspnet:8.0-alpine
WORKDIR /app
COPY --from=builder /app .
USER app
EXPOSE 8080
ENTRYPOINT ["dotnet", "Ford.Workshop.Api.dll"]
