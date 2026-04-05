// src/plugins/index.ts
// Added in Module 07 — Design Patterns
// Plugin registry stub — built during exercises

export interface PipeForgePlugin {
  name: string;
  version: string;
  register(registry: PluginRegistry): void | Promise<void>;
}

export interface StepProcessor {
  type: string;
  process(input: unknown, config: Record<string, unknown>): Promise<unknown>;
}

// PluginRegistry — built in Module 07 (Plugin Architecture topic)
export class PluginRegistry {
  private stepProcessors = new Map<string, StepProcessor>();

  registerStepProcessor(processor: StepProcessor): void {
    this.stepProcessors.set(processor.type, processor);
  }

  getStepProcessor(type: string): StepProcessor | undefined {
    return this.stepProcessors.get(type);
  }

  listStepProcessors(): string[] {
    return [...this.stepProcessors.keys()];
  }
}

// TODO (Module 07): Add plugin loading from filesystem, lifecycle hooks, tapable
