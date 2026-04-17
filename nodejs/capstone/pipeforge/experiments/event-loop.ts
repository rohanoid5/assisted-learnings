// experiments/event-loop.ts
import { readFile } from 'node:fs/promises';

async function main() {
  console.log('1. sync start');

  // Schedule a setTimeout (timers phase)
  setTimeout(() => console.log('6. setTimeout(0)'), 0);

  // Schedule a setImmediate (check phase)
  setImmediate(() => console.log('7. setImmediate'));

  // Schedule a nextTick
  process.nextTick(() => console.log('3. nextTick'));

  // Promise (microtask)
  Promise.resolve().then(() => console.log('4. Promise.then'));

  // Async I/O — which phase does the callback land in?
  readFile('package.json').then(() => {
    console.log('5. fs.readFile resolved');
    setTimeout(() => console.log('8. setTimeout inside I/O callback'), 0);
    setImmediate(() => console.log('8b. setImmediate inside I/O callback'));
  });

  console.log('2. sync end');
}

main();
