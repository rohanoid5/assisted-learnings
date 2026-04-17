// experiments/module-cache.ts
// 1. Create two imports of the same module and verify they're identical
// 2. Observe that module code runs only once
// 3. Use import.meta.url to get the current file path

console.log('Module URL:', import.meta.url);

// Create a counter module inline using a dynamic import
const counter1 = await import('../src/core/pipeline-engine.js');
const counter2 = await import('../src/core/pipeline-engine.js');

// Are they the same reference?
console.log('Same module instance?', counter1 === counter2)
