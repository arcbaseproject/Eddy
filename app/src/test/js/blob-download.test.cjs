// Exercises the injected script with two downloads awaiting acknowledgements together.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/java/app/eddy/browser/downloads/BlobDownloader.kt', 'utf8');
const script = source.split('private fun script(id: Int, url: String): String = """')[1].split('""".trimIndent()')[0];
const messages = [];
const context = vm.createContext({
  window: { EddyBlob: { postMessage: text => messages.push(JSON.parse(text)) } },
  fetch: async () => ({ blob: async () => ({ size: 1, type: 'text/plain', slice() { return this; } }) }),
  FileReader: class {
    readAsDataURL() { this.result = 'data:;base64,YQ=='; this.onload(); }
  },
});
for (const id of [1, 2]) {
  vm.runInContext(script.replaceAll('$CHUNK', '786432').replace('${id}', String(id)).replace('${JSONObject.quote(url)}', '"blob:test"'), context);
}
setImmediate(() => {
  assert.deepEqual(messages.map(m => [m.id, m.t]), [[1, 'chunk'], [2, 'chunk']]);
  context.window.EddyBlob.onmessage({ data: 'ack:2' });
  context.window.EddyBlob.onmessage({ data: 'ack:1' });
  assert.deepEqual(messages.slice(2).map(m => [m.id, m.t]), [[2, 'end'], [1, 'end']]);
  context.window.EddyBlob.onmessage({ data: 'ack:1' });
  assert.equal(messages.length, 4);
  console.log('Concurrent blob acknowledgement regression passed');
});
