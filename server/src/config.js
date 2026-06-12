import dotenv from 'dotenv';

dotenv.config();

export const config = {
  port: Number.parseInt(process.env.PORT || '8080', 10),
  databaseUrl:
    process.env.DATABASE_URL ||
    'postgres://location:location@localhost:5432/location_sharing',
  redisUrl: process.env.REDIS_URL || 'redis://localhost:6379',
  corsOrigin: process.env.CORS_ORIGIN || '*',
  locationCacheTtlSeconds: Number.parseInt(
    process.env.LOCATION_CACHE_TTL_SECONDS || '86400',
    10
  ),
};
