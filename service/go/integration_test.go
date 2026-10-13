package main

import "testing"

func TestIntegrationGivenARobotRegisteredWithHangryHippoWhenTheFleetIsAskedForItsHangryHippoAccountThenItReturnsTheVehicleId(t *testing.T) {
	res, err := Get(config.FleetURL + "/v1/devices/C10393/partner-accounts/hangry-hippo")
	if err != nil {
		t.Fatal(err)
	}

	if res.Status != 200 || res.Body["vehicleId"] != "veh_8f21c4" {
		t.Fatalf("expected 200 with vehicleId veh_8f21c4, got %d %v", res.Status, res.Body)
	}
}
