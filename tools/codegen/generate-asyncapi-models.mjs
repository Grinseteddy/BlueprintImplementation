// Generates Java records for the message payloads of an AsyncAPI document (ADR0014).
// Usage: node generate-asyncapi-models.mjs <asyncapi.yaml> <output dir> <java package>
// Output is generated on every build and never edited.
import { readFileSync, rmSync } from 'node:fs';
import { JavaFileGenerator, JAVA_JACKSON_PRESET } from '@asyncapi/modelina';

const [spec, outputDir, packageName] = process.argv.slice(2);
if (!spec || !outputDir || !packageName) {
  console.error('usage: generate-asyncapi-models.mjs <asyncapi.yaml> <output dir> <java package>');
  process.exit(2);
}

rmSync(outputDir, { recursive: true, force: true });
const generator = new JavaFileGenerator({
  modelType: 'record',
  collectionType: 'List',
  presets: [JAVA_JACKSON_PRESET],
});
const models = await generator.generateToFiles(readFileSync(spec, 'utf8'), outputDir, { packageName });
if (models.length === 0) {
  // Modelina generates nothing for a document it does not support (e.g. an unsupported AsyncAPI version)
  console.error(`no models generated from ${spec}`);
  process.exit(1);
}
const anonymous = models.filter((model) => model.modelName.startsWith('AnonymousSchema'));
if (anonymous.length > 0) {
  // an invented name is fixed in the spec (a title on the schema), never in the output
  console.error(`unnamed schemas in ${spec}: ${anonymous.map((model) => model.modelName).join(', ')}`);
  process.exit(1);
}
console.log(`generated ${models.length} models: ${models.map((model) => model.modelName).join(', ')}`);
