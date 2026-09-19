# Partner Supply API

`http://localhost:4002`

The partner's platform. We do not own it and we cannot change it.

## Known characteristics

- A write typically takes **600 to 800ms**.
- It returns **503** intermittently under load.
- Vehicle ids are the partner's, not our serials. Look them up.

## `GET /v1/vehicles?serial={serial}`

Maps one of our serials to the partner's vehicle id.

```
200 OK
{ "serial": "C10393", "vehicleId": "veh_8f21c4" }
```

404 if the robot is not registered with the partner.

## `PUT /v1/vehicles/{vehicleId}/availability`

Sets whether the partner may send work to that vehicle.

```
PUT /v1/vehicles/veh_8f21c4/availability
{ "available": true }

204 No Content
```

- 404 if the vehicle id is unknown.
- 503 when the partner is shedding load.

## `GET /v1/vehicles/{vehicleId}/availability`

What the partner currently believes. 404 before anything has been written.

## `GET /v1/_debug/calls`

Every write the partner has received, in order, with timing and status. Not part
of the real partner API. It is here so you can see what your service actually did.

## `POST /v1/_debug/reset`

Clears recorded availability and the call log.
