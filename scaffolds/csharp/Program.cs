using System.Text;
using System.Text.Json;

var builder = WebApplication.CreateBuilder(args);
builder.Logging.ClearProviders();
var app = builder.Build();

var config = new
{
    Port = Environment.GetEnvironmentVariable("PORT") ?? "3000",
    FleetUrl = Environment.GetEnvironmentVariable("FLEET_URL") ?? "http://localhost:4001",
    PartnerUrl = Environment.GetEnvironmentVariable("PARTNER_URL") ?? "http://localhost:4002",
    BusUrl = Environment.GetEnvironmentVariable("BUS_URL") ?? "http://localhost:4003",
};

app.MapPost("/v1/devices/{serial}/status", async (string serial, HttpRequest request) =>
{
    var changeId = request.Headers["X-Change-Id"].ToString();
    var change = await request.ReadFromJsonAsync<StatusChange>();

    Console.WriteLine($"change={changeId} serial={serial} status={change?.Status} factors={string.Join(",", change?.LimitingFactors ?? [])}");

    // TODO: the three steps in TASK.md go here.

    return Results.Json(new { status = "ok" });
});

app.MapGet("/health", () => Results.Json(new { status = "ok" }));

app.Urls.Add($"http://0.0.0.0:{config.Port}");
Console.WriteLine($"listening on :{config.Port}");
app.Run();

record StatusChange(string Status, string[] LimitingFactors, string ObservedAt);

// --- plumbing, nothing below here is part of the exercise ---

record Response(int Status, JsonElement? Body);

static class Http
{
    static readonly HttpClient Client = new() { Timeout = TimeSpan.FromSeconds(5) };

    public static Task<Response> Get(string url) => Call(HttpMethod.Get, url, null);

    public static Task<Response> Put(string url, object body) => Call(HttpMethod.Put, url, body);

    public static Task<Response> Post(string url, object body) => Call(HttpMethod.Post, url, body);

    static async Task<Response> Call(HttpMethod method, string url, object? body)
    {
        using var request = new HttpRequestMessage(method, url);
        if (body is not null)
        {
            request.Content = new StringContent(JsonSerializer.Serialize(body), Encoding.UTF8, "application/json");
        }

        using var res = await Client.SendAsync(request);
        var text = await res.Content.ReadAsStringAsync();

        return new Response((int)res.StatusCode, string.IsNullOrWhiteSpace(text) ? null : JsonDocument.Parse(text).RootElement.Clone());
    }
}
