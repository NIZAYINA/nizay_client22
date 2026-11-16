// Собирает ДВА exe: обычный (adminNames пустой) и админский (adminNames из config.json).
// Запуск: node build-all.js   -> dist/user/Nizay client.exe и dist/admin/Nizay client Admin.exe
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const cfgPath = path.join(__dirname, 'config.json');
const original = fs.readFileSync(cfgPath, 'utf8');
const cfg = JSON.parse(original);
const variants = [
  { dir: 'user',  name: 'Nizay client',       admins: [] },
  { dir: 'admin', name: 'Nizay client Admin', admins: cfg.adminNames || [] },
];
let failed = false;
try {
  for (const v of variants) {
    console.log(`\n=== Сборка: ${v.name} ===`);
    fs.writeFileSync(cfgPath, JSON.stringify({ ...cfg, adminNames: v.admins }, null, 2));
    const r = spawnSync('npx', ['electron-builder', '--win', 'portable', '--publish', 'never',
      `-c.productName=${v.name}`, `-c.directories.output=dist/${v.dir}`, '-c.win.artifactName=${productName}.exe'],
      { cwd: __dirname, stdio: 'inherit', shell: true });
    if (r.status !== 0) { failed = true; break; }
  }
} finally {
  fs.writeFileSync(cfgPath, original);   // вернуть исходный config.json
}
if (failed) { console.error('Сборка не удалась'); process.exit(1); }
console.log('\nГотово: dist\\user\\Nizay client.exe (для друзей), dist\\admin\\Nizay client Admin.exe (для вас)');
