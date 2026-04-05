// src/cli/index.ts
// Added in Module 05 — File System & CLI
// Stub: the full CLI is built during exercises

import { Command } from 'commander';

const program = new Command();

program
  .name('pipeforge')
  .description('Data pipeline management CLI')
  .version('0.1.0');

// TODO (Module 05): Add pipeline commands (create, list, run, status, logs)
// TODO (Module 05): Add file-based config loading
// TODO (Module 05): Add interactive prompts (inquirer)

program
  .command('health')
  .description('Check the PipeForge API health')
  .action(async () => {
    console.log('Checking PipeForge API health...');
    // TODO: Fetch from API
  });

program.parse(process.argv);
