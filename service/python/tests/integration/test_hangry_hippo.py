from server import CONFIG, get


def test_given_a_robot_registered_with_hangry_hippo_when_its_serial_is_looked_up_then_hangry_hippo_returns_its_vehicle_id():
    res = get(f"{CONFIG['partner_url']}/v1/vehicles?serial=C10393")

    assert res == {"status": 200, "body": {"serial": "C10393", "vehicleId": "veh_8f21c4"}}
