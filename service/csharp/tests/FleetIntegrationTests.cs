[Trait("Category", "integration")]
public class FleetIntegrationTests
{
    [Fact(DisplayName = "given a robot registered with HangryHippo, when the fleet is asked for its HangryHippo account, then it returns the vehicle id")]
    public async Task RegisteredSerialMapsToVehicleId()
    {
        var res = await Http.Get($"{Config.FleetUrl}/v1/devices/C10393/partner-accounts/hangry-hippo");

        Assert.Equal(200, res.Status);
        Assert.Equal("veh_8f21c4", res.Body?.GetProperty("vehicleId").GetString());
    }
}
