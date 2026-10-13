[Trait("Category", "integration")]
public class HangryHippoIntegrationTests
{
    [Fact(DisplayName = "given a robot registered with HangryHippo, when its serial is looked up, then HangryHippo returns its vehicle id")]
    public async Task RegisteredSerialMapsToVehicleId()
    {
        var res = await Http.Get($"{Config.PartnerUrl}/v1/vehicles?serial=C10393");

        Assert.Equal(200, res.Status);
        Assert.Equal("veh_8f21c4", res.Body?.GetProperty("vehicleId").GetString());
    }
}
