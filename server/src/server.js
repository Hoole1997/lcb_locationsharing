import crypto from 'node:crypto';
import cors from 'cors';
import express from 'express';
import helmet from 'helmet';
import { z } from 'zod';
import { config } from './config.js';
import { closeDatabase, query } from './db.js';
import {
  badRequest,
  forbidden,
  HttpError,
  notFound,
  unauthorized,
} from './httpError.js';
import { closeRedis, connectRedis, redis } from './redis.js';

const app = express();

app.use(helmet());
app.use(cors({ origin: config.corsOrigin === '*' ? true : config.corsOrigin }));
app.use(express.json({ limit: '64kb' }));

const uuidSchema = z.string().uuid();
const registerDeviceSchema = z.object({
  deviceName: z.string().trim().min(1).max(80),
});
const profileDisplayNameSchema = z.string().trim().max(80).nullable().optional();
const profileGenderSchema = z.enum(['male', 'female']).nullable().optional();
const joinPairingSchema = z.object({
  inviteCode: z.string().trim().min(4).max(12),
  displayName: profileDisplayNameSchema,
  gender: profileGenderSchema,
});
const friendProfileSchema = z.object({
  displayName: profileDisplayNameSchema,
  gender: profileGenderSchema,
});
const locationSchema = z.object({
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  accuracy: z.number().min(0).optional(),
  speed: z.number().min(0).optional(),
  bearing: z.number().min(0).lt(360).optional(),
  battery: z.number().int().min(0).max(100).optional(),
  address: z.string().trim().max(300).nullable().optional(),
  recordedAt: z.string().datetime().optional(),
});

function asyncHandler(handler) {
  return (req, res, next) => {
    Promise.resolve(handler(req, res, next)).catch(next);
  };
}

function parseBody(schema, body) {
  const parsed = schema.safeParse(body);
  if (!parsed.success) {
    throw badRequest(parsed.error.issues.map((issue) => issue.message).join(', '));
  }
  return parsed.data;
}

function generateInviteCode(length = 7) {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let code = '';
  for (let i = 0; i < length; i += 1) {
    code += alphabet[crypto.randomInt(0, alphabet.length)];
  }
  return code;
}

function locationCacheKey(deviceId) {
  return `location:${deviceId}`;
}

function mapDevice(row) {
  return {
    deviceId: row.device_id,
    deviceName: row.device_name,
    inviteCode: row.invite_code,
    createdAt: row.created_at,
  };
}

function mapLocation(row) {
  if (!row) return null;
  return {
    deviceId: row.device_id,
    lat: Number(row.lat),
    lng: Number(row.lng),
    accuracy: row.accuracy === null ? null : Number(row.accuracy),
    speed: row.speed === null ? null : Number(row.speed),
    bearing: row.bearing === null ? null : Number(row.bearing),
    battery: row.battery,
    address: row.address,
    recordedAt: row.recorded_at,
    updatedAt: row.updated_at,
  };
}

function normalizeDisplayName(displayName) {
  return displayName?.trim() || null;
}

function mapPairing(row) {
  return {
    id: row.id,
    friendDeviceId: row.friend_device_id,
    friendDeviceName: row.friend_device_name,
    friendDisplayName: row.friend_display_name,
    friendGender: row.friend_gender,
    initiatorDeviceId: row.initiator_device_id,
    status: row.status,
    createdAt: row.created_at,
    latestLocation:
      row.lat === null
        ? null
        : mapLocation({
            device_id: row.friend_device_id,
            lat: row.lat,
            lng: row.lng,
            accuracy: row.accuracy,
            speed: row.speed,
            bearing: row.bearing,
            battery: row.battery,
            address: row.address,
            recorded_at: row.recorded_at,
            updated_at: row.updated_at,
          }),
  };
}

async function upsertFriendProfile(ownerDeviceId, friendDeviceId, displayName, gender) {
  await query(
    `INSERT INTO friend_profiles
        (owner_device_id, friend_device_id, display_name, gender)
     VALUES ($1, $2, $3, $4)
     ON CONFLICT (owner_device_id, friend_device_id)
     DO UPDATE SET
        display_name = EXCLUDED.display_name,
        gender = EXCLUDED.gender,
        updated_at = now()`,
    [ownerDeviceId, friendDeviceId, displayName, gender ?? null]
  );
}

async function activePairingForRequester(requesterDeviceId, friendDeviceId) {
  const result = await query(
    `SELECT
        p.id,
        p.device_a_id,
        p.device_b_id,
        p.initiator_device_id,
        p.status,
        p.created_at,
        friend.device_id AS friend_device_id,
        friend.device_name AS friend_device_name,
        fp.display_name AS friend_display_name,
        fp.gender AS friend_gender,
        l.lat,
        l.lng,
        l.accuracy,
        l.speed,
        l.bearing,
        l.battery,
        l.address,
        l.recorded_at,
        l.updated_at
     FROM pairings p
     JOIN devices friend ON friend.device_id = $2
     LEFT JOIN friend_profiles fp
       ON fp.owner_device_id = $1
      AND fp.friend_device_id = friend.device_id
     LEFT JOIN latest_locations l ON l.device_id = friend.device_id
     WHERE p.status = 'active'
       AND (
         (p.device_a_id = $1 AND p.device_b_id = $2) OR
         (p.device_a_id = $2 AND p.device_b_id = $1)
       )
     LIMIT 1`,
    [requesterDeviceId, friendDeviceId]
  );
  if (result.rowCount === 0) return null;
  return mapPairing(result.rows[0]);
}

async function requireDevice(req, _res, next) {
  const deviceId = req.header('x-device-id');
  if (!deviceId || !uuidSchema.safeParse(deviceId).success) {
    throw unauthorized();
  }

  const result = await query('SELECT * FROM devices WHERE device_id = $1', [
    deviceId,
  ]);
  if (result.rowCount === 0) {
    throw unauthorized();
  }

  req.device = mapDevice(result.rows[0]);
  next();
}

function orderedDevicePair(firstDeviceId, secondDeviceId) {
  return firstDeviceId < secondDeviceId
    ? [firstDeviceId, secondDeviceId]
    : [secondDeviceId, firstDeviceId];
}

async function canViewLocation(requesterDeviceId, targetDeviceId) {
  if (requesterDeviceId === targetDeviceId) return true;

  const result = await query(
    `SELECT 1
       FROM pairings
      WHERE status = 'active'
        AND (
          (device_a_id = $1 AND device_b_id = $2) OR
          (device_a_id = $2 AND device_b_id = $1)
        )
      LIMIT 1`,
    [requesterDeviceId, targetDeviceId]
  );
  return result.rowCount > 0;
}

app.get('/health', (_req, res) => {
  res.json({ ok: true });
});

app.post(
  '/devices/register',
  asyncHandler(async (req, res) => {
    const body = parseBody(registerDeviceSchema, req.body);
    const deviceId = crypto.randomUUID();

    for (let attempt = 0; attempt < 5; attempt += 1) {
      const inviteCode = generateInviteCode();
      try {
        const result = await query(
          `INSERT INTO devices (device_id, device_name, invite_code)
           VALUES ($1, $2, $3)
           RETURNING *`,
          [deviceId, body.deviceName, inviteCode]
        );
        res.status(201).json({ device: mapDevice(result.rows[0]) });
        return;
      } catch (error) {
        if (error.code !== '23505') throw error;
      }
    }

    throw badRequest('Unable to generate unique invite code');
  })
);

app.get(
  '/devices/me',
  asyncHandler(requireDevice),
  asyncHandler(async (req, res) => {
    res.json({ device: req.device });
  })
);

app.post(
  '/devices/invite-code/regenerate',
  asyncHandler(requireDevice),
  asyncHandler(async (req, res) => {
    for (let attempt = 0; attempt < 5; attempt += 1) {
      const inviteCode = generateInviteCode();
      try {
        const result = await query(
          `UPDATE devices
              SET invite_code = $1
            WHERE device_id = $2
            RETURNING *`,
          [inviteCode, req.device.deviceId]
        );
        res.json({ device: mapDevice(result.rows[0]) });
        return;
      } catch (error) {
        if (error.code !== '23505') throw error;
      }
    }

    throw badRequest('Unable to generate unique invite code');
  })
);

app.post(
  '/pairings/join',
  asyncHandler(requireDevice),
  asyncHandler(async (req, res) => {
    const body = parseBody(joinPairingSchema, req.body);
    const friendResult = await query(
      'SELECT * FROM devices WHERE invite_code = $1',
      [body.inviteCode.toUpperCase()]
    );
    if (friendResult.rowCount === 0) {
      throw notFound('Invite code not found');
    }

    const friend = mapDevice(friendResult.rows[0]);
    if (friend.deviceId === req.device.deviceId) {
      throw badRequest('Cannot pair with your own invite code');
    }

    const [deviceAId, deviceBId] = orderedDevicePair(
      req.device.deviceId,
      friend.deviceId
    );
    await query(
      `INSERT INTO pairings
          (id, device_a_id, device_b_id, initiator_device_id, status)
       VALUES ($1, LEAST($2::uuid, $3::uuid), GREATEST($2::uuid, $3::uuid), $4, 'active')
       ON CONFLICT (device_a_id, device_b_id)
       DO UPDATE SET
          status = 'active',
          initiator_device_id = EXCLUDED.initiator_device_id,
          updated_at = now()
       RETURNING *`,
      [
        crypto.randomUUID(),
        deviceAId,
        deviceBId,
        req.device.deviceId,
      ]
    );

    await upsertFriendProfile(
      req.device.deviceId,
      friend.deviceId,
      normalizeDisplayName(body.displayName),
      body.gender
    );

    res.status(201).json({
      pairing: await activePairingForRequester(req.device.deviceId, friend.deviceId),
    });
  })
);

app.get(
  '/pairings',
  asyncHandler(requireDevice),
  asyncHandler(async (req, res) => {
    const result = await query(
      `SELECT
          p.id,
          p.device_a_id,
          p.device_b_id,
          p.initiator_device_id,
          p.status,
          p.created_at,
          friend.device_id AS friend_device_id,
          friend.device_name AS friend_device_name,
          fp.display_name AS friend_display_name,
          fp.gender AS friend_gender,
          l.lat,
          l.lng,
          l.accuracy,
          l.speed,
          l.bearing,
          l.battery,
          l.address,
          l.recorded_at,
          l.updated_at
       FROM pairings p
       JOIN devices friend
         ON friend.device_id = CASE
              WHEN p.device_a_id = $1 THEN p.device_b_id
              ELSE p.device_a_id
            END
       LEFT JOIN latest_locations l ON l.device_id = friend.device_id
       LEFT JOIN friend_profiles fp
         ON fp.owner_device_id = $1
        AND fp.friend_device_id = friend.device_id
       WHERE (p.device_a_id = $1 OR p.device_b_id = $1)
         AND p.status = 'active'
       ORDER BY p.created_at DESC`,
      [req.device.deviceId]
    );

    res.json({
      pairings: result.rows.map(mapPairing),
    });
  })
);

app.patch(
  '/pairings/:friendDeviceId/profile',
  asyncHandler(requireDevice),
  asyncHandler(async (req, res) => {
    const friendDeviceId = req.params.friendDeviceId;
    if (!uuidSchema.safeParse(friendDeviceId).success) {
      throw badRequest('Invalid friendDeviceId');
    }

    const body = parseBody(friendProfileSchema, req.body);
    if (!Object.hasOwn(body, 'displayName') && !Object.hasOwn(body, 'gender')) {
      throw badRequest('No profile fields to update');
    }

    const currentPairing = await activePairingForRequester(
      req.device.deviceId,
      friendDeviceId
    );
    if (!currentPairing) {
      throw notFound('Active pairing not found');
    }

    const displayName = Object.hasOwn(body, 'displayName')
      ? normalizeDisplayName(body.displayName)
      : currentPairing.friendDisplayName;
    const gender = Object.hasOwn(body, 'gender')
      ? body.gender
      : currentPairing.friendGender;

    await upsertFriendProfile(
      req.device.deviceId,
      friendDeviceId,
      displayName,
      gender
    );

    res.json({
      pairing: await activePairingForRequester(req.device.deviceId, friendDeviceId),
    });
  })
);

app.delete(
  '/pairings/:friendDeviceId',
  asyncHandler(requireDevice),
  asyncHandler(async (req, res) => {
    const friendDeviceId = req.params.friendDeviceId;
    if (!uuidSchema.safeParse(friendDeviceId).success) {
      throw badRequest('Invalid friendDeviceId');
    }

    await query(
      `UPDATE pairings
          SET status = 'revoked',
              updated_at = now()
        WHERE status = 'active'
          AND (
            (device_a_id = $1 AND device_b_id = $2) OR
            (device_a_id = $2 AND device_b_id = $1)
          )`,
      [friendDeviceId, req.device.deviceId]
    );
    await query(
      `DELETE FROM friend_profiles
        WHERE owner_device_id = $1
          AND friend_device_id = $2`,
      [req.device.deviceId, friendDeviceId]
    );
    res.status(204).end();
  })
);

app.post(
  '/locations/current',
  asyncHandler(requireDevice),
  asyncHandler(async (req, res) => {
    const body = parseBody(locationSchema, req.body);
    const recordedAt = body.recordedAt || new Date().toISOString();
    const address = body.address?.trim() || null;

    const result = await query(
      `INSERT INTO latest_locations
          (device_id, lat, lng, accuracy, speed, bearing, battery, address, recorded_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
       ON CONFLICT (device_id)
       DO UPDATE SET
          lat = EXCLUDED.lat,
          lng = EXCLUDED.lng,
          accuracy = EXCLUDED.accuracy,
          speed = EXCLUDED.speed,
          bearing = EXCLUDED.bearing,
          battery = EXCLUDED.battery,
          address = EXCLUDED.address,
          recorded_at = EXCLUDED.recorded_at,
          updated_at = now()
       RETURNING *`,
      [
        req.device.deviceId,
        body.lat,
        body.lng,
        body.accuracy ?? null,
        body.speed ?? null,
        body.bearing ?? null,
        body.battery ?? null,
        address,
        recordedAt,
      ]
    );

    const location = mapLocation(result.rows[0]);
    await redis.set(locationCacheKey(req.device.deviceId), JSON.stringify(location), {
      EX: config.locationCacheTtlSeconds,
    });

    res.json({ location });
  })
);

app.get(
  '/locations/current/:deviceId',
  asyncHandler(requireDevice),
  asyncHandler(async (req, res) => {
    const targetDeviceId = req.params.deviceId;
    if (!uuidSchema.safeParse(targetDeviceId).success) {
      throw badRequest('Invalid deviceId');
    }
    if (!(await canViewLocation(req.device.deviceId, targetDeviceId))) {
      throw forbidden('No active pairing for this location');
    }

    const cached = await redis.get(locationCacheKey(targetDeviceId));
    if (cached) {
      res.json({ location: JSON.parse(cached) });
      return;
    }

    const result = await query(
      'SELECT * FROM latest_locations WHERE device_id = $1',
      [targetDeviceId]
    );
    if (result.rowCount === 0) {
      throw notFound('Location not found');
    }

    const location = mapLocation(result.rows[0]);
    await redis.set(locationCacheKey(targetDeviceId), JSON.stringify(location), {
      EX: config.locationCacheTtlSeconds,
    });
    res.json({ location });
  })
);

app.use((req, _res, next) => {
  next(notFound(`Route not found: ${req.method} ${req.path}`));
});

app.use((error, _req, res, _next) => {
  if (error instanceof HttpError) {
    res.status(error.status).json({ error: error.message });
    return;
  }

  console.error(error);
  res.status(500).json({ error: 'Internal server error' });
});

let server;

async function start() {
  await connectRedis();
  server = app.listen(config.port, () => {
    console.log(`Location sharing server listening on ${config.port}`);
  });
}

async function shutdown() {
  if (server) {
    await new Promise((resolve) => server.close(resolve));
  }
  await closeRedis();
  await closeDatabase();
}

process.on('SIGINT', async () => {
  await shutdown();
  process.exit(0);
});

process.on('SIGTERM', async () => {
  await shutdown();
  process.exit(0);
});

start().catch(async (error) => {
  console.error(error);
  await shutdown();
  process.exitCode = 1;
});
