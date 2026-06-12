# Location Sharing Server

Small Node.js service for the Android location sharing flow.

## Stack

- Node.js + Express
- PostgreSQL for devices, pairings, and latest location persistence
- Redis for latest-location cache

The `devices` table stores only:

- `deviceId`
- `deviceName`
- `inviteCode`
- `createdAt`

## Run Locally

```bash
cd server
cp .env.example .env
docker compose up -d
npm install
npm run migrate
npm run dev
```

If ports `5432` or `6379` are already used, override them when starting
Compose and keep `DATABASE_URL` / `REDIS_URL` aligned:

```bash
POSTGRES_PORT=15432 REDIS_PORT=16379 docker compose up -d
DATABASE_URL=postgres://location:location@localhost:15432/location_sharing \
REDIS_URL=redis://localhost:16379 \
npm run migrate
```

Health check:

```bash
curl http://localhost:8080/health
```

## API

### Register Device

```bash
curl -X POST http://localhost:8080/devices/register \
  -H 'Content-Type: application/json' \
  -d '{"deviceName":"Alice phone"}'
```

### Get Current Device

```bash
curl http://localhost:8080/devices/me \
  -H 'x-device-id: DEVICE_ID'
```

### Regenerate Invite Code

```bash
curl -X POST http://localhost:8080/devices/invite-code/regenerate \
  -H 'x-device-id: DEVICE_ID'
```

### Join Pairing

Entering either device's invite code creates a mutual relationship. Both devices
can view each other's latest location.

```bash
curl -X POST http://localhost:8080/pairings/join \
  -H 'Content-Type: application/json' \
  -H 'x-device-id: CURRENT_DEVICE_ID' \
  -d '{"inviteCode":"E524W66"}'
```

### List Pairings

```bash
curl http://localhost:8080/pairings \
  -H 'x-device-id: CURRENT_DEVICE_ID'
```

### Upload Current Location

```bash
curl -X POST http://localhost:8080/locations/current \
  -H 'Content-Type: application/json' \
  -H 'x-device-id: DEVICE_ID' \
  -d '{"lat":31.2304,"lng":121.4737,"accuracy":12,"battery":76}'
```

### Get Friend Current Location

```bash
curl http://localhost:8080/locations/current/FRIEND_DEVICE_ID \
  -H 'x-device-id: CURRENT_DEVICE_ID'
```

## Notes

- Pairings are mutual. Once one side joins with an invite code, both devices can
  read each other's latest location.
- This is a minimal REST server. Android can poll `GET /locations/current/:deviceId`.
- Add WebSocket later if the map needs lower-latency live movement.
- This does not use FCM and does not store `fcmToken`.
