// Implement these two functions (from Topic 1.5):
function availabilityInSequence(...availabilities: number[]): number {
  return availabilities.reduce((acc, a) => acc * a, 1);
}

function availabilityInParallel(...availabilities: number[]): number {
  const unavailability = availabilities.reduce((acc, a) => acc * (1 - a), 1);
  return 1 - unavailability;
}

function toDowntimePerYear(availability: number): string {
  const minutes = (1 - availability) * 365 * 24 * 60;
  return minutes < 1 ? `${(minutes * 60).toFixed(1)}s/year` : `${minutes.toFixed(1)} min/year`;
}

// Given these individual availabilities:
const NGINX = 0.9999;
const APP_REPLICA = 0.999;   // one replica
const REDIS_NODE = 0.9999;   // one Redis node
const POSTGRES_NODE = 0.9995; // one Postgres node

// Calculate and print:
// 1. Availability with 3 app replicas
// 2. Availability with Redis primary + replica
// 3. Availability with Postgres primary + standby
// 4. End-to-end availability of the critical path
// 5. Which component is still the weakest link?

// 1. Availability with 3 app replicas
const appAvailablity = availabilityInParallel(APP_REPLICA, APP_REPLICA, APP_REPLICA);
console.log(`Availability with 3 app replicas: ${appAvailablity} (${toDowntimePerYear(appAvailablity)})`);

// 2. Availability with Redis primary + replica
const redisAvailability = availabilityInParallel(REDIS_NODE, REDIS_NODE);
console.log(`Availability with Redis primary + replica: ${redisAvailability} (${toDowntimePerYear(redisAvailability)})`);

// 3. Availability with Postgres primary + standby
const postgresAvailability = availabilityInParallel(POSTGRES_NODE, POSTGRES_NODE);
console.log(`Availability with Postgres primary + standby: ${postgresAvailability} (${toDowntimePerYear(postgresAvailability)})`);

// 4. End-to-end availability of the critical path
const endToEndAvailability = availabilityInSequence(NGINX, appAvailablity, redisAvailability, postgresAvailability);
console.log(`End-to-end availability of the critical path: ${endToEndAvailability} (${toDowntimePerYear(endToEndAvailability)})`);

// 5. Which component is still the weakest link?
const components = [
  { name: 'NGINX', availability: NGINX },
  { name: 'App Replica', availability: APP_REPLICA },
  { name: 'Redis Node', availability: REDIS_NODE },
  { name: 'Postgres Node', availability: POSTGRES_NODE }
];

const weakestLink = components.reduce((weakest, component) => {
  return component.availability < weakest.availability ? component : weakest;
}, components[0]);

console.log(`The weakest link is: ${weakestLink.name} with availability ${weakestLink.availability} (${toDowntimePerYear(weakestLink.availability)})`);
