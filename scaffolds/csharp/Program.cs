var builder = WebApplication.CreateBuilder(args);
var app = builder.Build();

var config = new
{
    Port = Environment.GetEnvironmentVariable("PORT") ?? "3000",
    FleetUrl = Environment.GetEnvironmentVariable("FLEET_URL") ?? "http://localhost:4001",
    PartnerUrl = Environment.GetEnvironmentVariable("PARTNER_URL") ?? "http://localhost:4002",
    BusUrl = Environment.GetEnvironmentVariable("BUS_URL") ?? "http://localhost:4003",
};

app.MapGet("/health", () => Results.Json(new { status = "ok" }));

app.Urls.Add($"http://0.0.0.0:{config.Port}");
Console.WriteLine($"listening on :{config.Port}");
app.Run();
