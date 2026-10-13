from server import CONFIG, get


def test_given_a_robot_registered_with_hangry_hippo_when_the_fleet_is_asked_for_its_hangry_hippo_account_then_it_returns_the_vehicle_id():
    res = get(f"{CONFIG['fleet_url']}/v1/devices/C10393/partner-accounts/hangry-hippo")

    assert res == {"status": 200, "body": {"serial": "C10393", "partner": "hangry-hippo", "vehicleId": "veh_8f21c4"}}
