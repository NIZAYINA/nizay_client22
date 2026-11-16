// Собирает ДВА exe: обычный (adminNames пустой) и админский (adminNames из config.json).
// Запуск: node build-all.js   -> dist/user/Nizay client.exe и dist/admin/Nizay client Admin.exe
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const cfgPath = path.join(__dirname, 'config.json');
const original = fs.readFileSync(cfgPath, 'utf8');
const cfg = JSON.parse(original);
const pkg = JSON.parse(fs.readFileSync(path.join(__dirname, 'package.json'), 'utf8'));
const variants = [
  { dir: 'user',  name: 'Nizay client',       admins: [] },
  { dir: 'admin', name: 'Nizay client Admin', admins: cfg.adminNames || [] },
];
let failed = false;
const tmpFiles = [];
try {
  for (const v of variants) {
    console.log(`\n=== Сборка: ${v.name} ===`);
    fs.writeFileSync(cfgPath, JSON.stringify({ ...cfg, adminNames: v.admins }, null, 2));
    // отдельный файл конфигурации electron-builder (так нет проблем с пробелами в аргументах)
    const ebCfg = {
      ...pkg.build,
      productName: v.name,
      directories: { output: `dist/${v.dir}` },
      win: { ...pkg.build.win, target: 'portable', artifactName: '${productName}.exe' },
    };
    const ebFile = path.join(__dirname, `eb-${v.dir}.json`);
    fs.writeFileSync(ebFile, JSON.stringify(ebCfg, null, 2));
    tmpFiles.push(ebFile);
    const r = spawnSync('npx', ['electron-builder', '--win', 'portable', '--publish', 'never', '--config', `eb-${v.dir}.json`],
      { cwd: __dirname, stdio: 'inherit', shell: true });
    if (r.status !== 0) { failed = true; break; }
  }
} finally {
  fs.writeFileSync(cfgPath, original);   // вернуть исходный config.json
  for (const f of tmpFiles) { try { fs.rmSync(f, { force: true }); } catch {} }
}
if (failed) { console.error('Сборка не удалась'); process.exit(1); }
console.log('\nГотово: dist\\user\\Nizay client.exe (для друзей), dist\\admin\\Nizay client Admin.exe (для вас)');
