# Fleet API

`http://localhost:4001`

Owns device state for the fleet. Read-only to us.

## Availability rule

A device is available to take work when its status is `ONLINE` and none of its
limiting factors block it. The limiting factors that block availability are:

| Factor | Meaning |
|---|---|
| `PILOT_REVIEW` | Held for remote pilot review |
| `LOW_BATTERY` | Below the dispatch threshold |
| `HARDWARE_FAULT` | A component reported a critical fault |
| `MAINTENANCE` | Pulled from service by a field operator |
| `OUT_OF_ZONE` | Outside its hub's operating area |

Any other limiting factor is informational and does not block availability.

## `POST /v1/availability`

Applies the rule above.

```
POST /v1/availability
{ "status": "ONLINE", "limitingFactors": ["LOW_BATTERY"] }

200 OK
{ "available": false, "blockingFactors": ["LOW_BATTERY"] }
```

## `GET /v1/devices/{serial}`

The fleet's current record for a device.

```
200 OK
{
  "serial": "C10393",
  "status": "ONLINE",
  "limitingFactors": [],
  "observedAt": "2026-09-18T17:02:11Z",
  "hub": "LAX-COLONY"
}
```

404 if the serial is unknown.

## `GET /v1/devices`

Every seeded device. Useful for finding serials to test with.

## `GET /v1/health`
