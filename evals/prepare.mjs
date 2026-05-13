import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const sharedResources = resolve(here, "../shared/src/commonMain/resources");

mkdirSync(resolve(here, "prompts"), { recursive: true });

// Build a chat-template JSON file for each system prompt. Promptfoo
// treats each as a multi-turn conversation: the system message is fixed
// (the .md content with {locale} -> de), the user message templates
// `{{userMessage}}` from each test case's vars.

const prompts = [
  { src: "workout-system-prompt.md", dst: "prompts/workout-system.json" },
  { src: "recipe-system-prompt.md", dst: "prompts/recipe-system.json" }
];

for (const { src, dst } of prompts) {
  const md = readFileSync(resolve(sharedResources, src), "utf8");
  const systemContent = md.replaceAll("{locale}", "de");
  const chat = [
    { role: "system", content: systemContent },
    { role: "user", content: "{{userMessage}}" }
  ];
  writeFileSync(resolve(here, dst), JSON.stringify(chat, null, 2), "utf8");
  console.log(`prepared ${dst}`);
}
