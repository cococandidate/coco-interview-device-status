import importlib
import json
import socket
import urllib.request
from types import SimpleNamespace
from unittest.mock import MagicMock

import pytest

import server

PARTNER = server.CONFIG["partner_url"]


@pytest.fixture(autouse=True)
def fresh_server():
    """Reload so any per-serial state the handler keeps doesn't leak between tests."""
    importlib.reload(server)


@pytest.fixture
def channel():
    return MagicMock()


@pytest.fixture
def method():
    return SimpleNamespace(delivery_tag=7, redelivered=False)


@pytest.fixture
def properties():
    return SimpleNamespace(message_id="msg-1")


@pytest.fixture
def partner(monkeypatch):
    """Stub the partner API. Set `partner.lookup` to the GET response."""
    state = SimpleNamespace(
        lookup={"status": 200, "body": {"vehicleId": "veh-1"}},
        gets=[],
        puts=[],
    )

    def fake_get(url, **_):
        state.gets.append(url)
        return state.lookup

    def fake_put(url, body=None, **_):
        state.puts.append((url, body))
        return {"status": 204, "body": None}

    monkeypatch.setattr(server, "get", fake_get)
    monkeypatch.setattr(server, "put", fake_put)
    return state


def message(status, factors=None, serial="SN-1", observed_at="2026-09-24T17:02:11.482Z"):
    return json.dumps(
        {
            "serial": serial,
            "status": status,
            "limitingFactors": factors or [],
            "observedAt": observed_at,
        }
    ).encode()


def test_online_device_is_marked_available(channel, method, properties, partner):
    server.handle_status_change(channel, method, properties, message("ONLINE"))

    assert partner.gets == [f"{PARTNER}/v1/vehicles?serial=SN-1"]
    assert partner.puts == [
        (f"{PARTNER}/v1/vehicles/veh-1/availability", {"available": True})
    ]
    channel.basic_ack.assert_called_once_with(7)
    channel.basic_nack.assert_not_called()


def test_offline_device_is_marked_unavailable(channel, method, properties, partner):
    server.handle_status_change(channel, method, properties, message("OFFLINE"))

    assert partner.puts == [
        (f"{PARTNER}/v1/vehicles/veh-1/availability", {"available": False})
    ]
    channel.basic_ack.assert_called_once_with(7)
    channel.basic_nack.assert_not_called()


def test_requeues_when_partner_unavailable(channel, method, properties, partner):
    partner.lookup = {"status": 503, "body": None}

    server.handle_status_change(channel, method, properties, message("ONLINE"))

    assert partner.puts == []
    channel.basic_nack.assert_called_once_with(7, requeue=True)
    channel.basic_ack.assert_not_called()


def test_dead_letters_when_robot_not_found(channel, method, properties, partner):
    partner.lookup = {"status": 404, "body": None}

    server.handle_status_change(channel, method, properties, message("ONLINE"))

    assert partner.puts == []
    channel.basic_nack.assert_called_once_with(7, requeue=False)
    channel.basic_ack.assert_not_called()


def test_requeues_when_partner_times_out(channel, method, properties, monkeypatch):
    """Partner answers in 2.5s; the handler must give up after 2s and requeue."""
    latency_s = 2.5

    def slow_urlopen(req, timeout=None):
        if timeout is None or timeout > latency_s:
            body = json.dumps({"vehicleId": "veh-1"}).encode()
            res = MagicMock(status=200)
            res.read.return_value = body
            res.__enter__.return_value = res
            return res
        raise socket.timeout("timed out")

    monkeypatch.setattr(urllib.request, "urlopen", slow_urlopen)

    server.handle_status_change(channel, method, properties, message("ONLINE"))

    channel.basic_nack.assert_called_once_with(7, requeue=True)
    channel.basic_ack.assert_not_called()


def test_drops_event_older_than_last_seen_for_serial(channel, properties, partner):
    newer = SimpleNamespace(delivery_tag=1, redelivered=False)
    older = SimpleNamespace(delivery_tag=2, redelivered=False)

    server.handle_status_change(
        channel, newer, properties,
        message("ONLINE", observed_at="2026-09-24T17:02:11.482Z"),
    )
    server.handle_status_change(
        channel, older, properties,
        message("OFFLINE", observed_at="2026-09-24T17:01:00.000Z"),
    )

    assert partner.puts == [
        (f"{PARTNER}/v1/vehicles/veh-1/availability", {"available": True})
    ]
    channel.basic_ack.assert_called_once_with(1)
    channel.basic_nack.assert_not_called()
