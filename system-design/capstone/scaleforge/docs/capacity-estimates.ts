const SECONDS_PER_DAY = 86_400;

const assumptions = {
  dailyRedirects: 100_000_000,          // 100M/day
  dailyUrlCreations: 1_000_000,         // 1M/day (100:1 read:write ratio)
  urlSizeBytes: 500,
  clickSizeBytes: 200,
  peakMultiplier: 10,                   // peak = 10× average
};

const qps = {
  readAvg: assumptions.dailyRedirects / SECONDS_PER_DAY,
  readPeak: (assumptions.dailyRedirects * assumptions.peakMultiplier) / SECONDS_PER_DAY,
  writeAvg: assumptions.dailyUrlCreations / SECONDS_PER_DAY,
};

const storageBytesPerYear = {
  urls: assumptions.dailyUrlCreations * 365 * assumptions.urlSizeBytes,
  clicks: assumptions.dailyRedirects * 365 * assumptions.clickSizeBytes,
};

console.log('=== ScaleForge Capacity Estimates ===');
console.log(`Read QPS (avg):  ${qps.readAvg.toFixed(0)} req/s`);
console.log(`Read QPS (peak): ${qps.readPeak.toFixed(0)} req/s`);
console.log(`Write QPS (avg): ${qps.writeAvg.toFixed(2)} req/s`);
console.log(`URL storage:     ${(storageBytesPerYear.urls / 1e9).toFixed(1)} GB/year`);
console.log(`Click storage:   ${(storageBytesPerYear.clicks / 1e12).toFixed(1)} TB/year`);

// Derived decisions
console.log('\n=== Architectural Decisions ===');
console.log(`Need caching?    ${qps.readPeak > 1000 ? 'YES — ' + qps.readPeak.toFixed(0) + ' peak req/s exceeds single-DB capacity' : 'No'}`);
console.log(`Need async writes? YES — 100M click rows/day blocks redirect if synchronous`);
console.log(`Need partitioning? ${storageBytesPerYear.clicks > 1e12 ? 'YES — clicks table grows ' + (storageBytesPerYear.clicks / 1e12).toFixed(1) + ' TB/year' : 'No'}`);

// Output:
// === ScaleForge Capacity Estimates ===
// Read QPS (avg):  1157 req/s
// Read QPS (peak): 11574 req/s
// Write QPS (avg): 11.57 req/s
// URL storage:     182.5 GB/year
// Click storage:   7.3 TB/year
//
// === Architectural Decisions ===
// Need caching?    YES — 11574 peak req/s exceeds single-DB capacity
// Need async writes? YES — 100M click rows/day blocks redirect if synchronous
// Need partitioning? YES — clicks table grows 7.3 TB/year
