// Локальный режим: аккаунты, ключи и подписки без сервера и без базы данных.
// Данные лежат в файле <userData>/nizay-local.json на компьютере пользователя.
// Включается автоматически, если в config.json пустой apiUrl.
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const ALPHA = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // 32 символа
const SESSION_MS = 30 * 86400000;
const DAYS_BY_TYPE = [1, 1, 7, 30];               // тип 0 = пробный (1 день), 1 = 1 день, 2 = 7, 3 = 30

module.exports = function createLocalApi({ dbFile, secret, admins }) {
  const adminSet = new Set((admins || []).map((x) => String(x).toLowerCase()));
  const nowIso = () => new Date().toISOString();
  const sha = (s) => crypto.createHash('sha256').update(s).digest('hex');
  const normKey = (s) => String(s || '').toUpperCase().replace(/[^A-Z0-9]/g, '');

  let db;
  function load() {
    if (db) return db;
    try { db = JSON.parse(fs.readFileSync(dbFile, 'utf8')); } catch { db = {}; }
    db.users ||= []; db.sessions ||= {}; db.subs ||= {}; db.usedKeys ||= {};
    db.issued ||= []; db.trials ||= {}; db.nextId ||= 1;
    return db;
  }
  function save() {
    fs.mkdirSync(path.dirname(dbFile), { recursive: true });
    fs.writeFileSync(dbFile + '.tmp', JSON.stringify(db));
    fs.renameSync(dbFile + '.tmp', dbFile);
  }

  // ---- пароли
  function hashPw(pw) {
    const salt = crypto.randomBytes(16);
    return 'scrypt$' + salt.toString('hex') + '$' + crypto.scryptSync(pw, salt, 32).toString('hex');
  }
  function checkPw(pw, stored) {
    const [, saltHex, hashHex] = String(stored).split('$');
    if (!saltHex || !hashHex) return false;
    const h = crypto.scryptSync(pw, Buffer.from(saltHex, 'hex'), 32);
    const exp = Buffer.from(hashHex, 'hex');
    return exp.length === h.length && crypto.timingSafeEqual(h, exp);
  }

  // ---- защита от перебора (в памяти)
  const tries = new Map();
  function limited(name, max, windowMs) {
    const now = Date.now(), r = tries.get(name);
    if (!r || r.reset < now) { tries.set(name, { n: 1, reset: now + windowMs }); return false; }
    if (r.n >= max) return true;
    r.n++; return false;
  }

  // ---- роль: администратор = логин из списка admins в config.json
  const roleOf = (u) => (adminSet.has(u.username.toLowerCase()) ? 'admin' : 'user');
  const pubUser = (u) => ({ id: u.id, username: u.username, role: roleOf(u) });

  function newSession(userId) {
    const token = crypto.randomBytes(32).toString('hex');
    db.sessions[sha(token)] = { userId, exp: Date.now() + SESSION_MS };
    return token;
  }
  function authed(token) {
    load();
    const s = db.sessions[sha(token || '')];
    if (!s || s.exp < Date.now()) return null;
    return db.users.find((u) => u.id === s.userId) || null;
  }

  // ---- подписанные ключи: MC + 12 символов = 60 бит: тип(2) | случайное(22) | подпись(36)
  const macOf = (head24) => {
    const b = Buffer.from([(head24 >> 16) & 255, (head24 >> 8) & 255, head24 & 255]);
    const d = crypto.createHmac('sha256', secret).update('nzk1').update(b).digest().subarray(0, 5);
    return BigInt('0x' + d.toString('hex')) >> 4n;                              // 36 бит
  };
  function encodeKey(type) {
    const nonce = crypto.randomBytes(3).readUIntBE(0, 3) & 0x3fffff;           // 22 бита
    const head = (type << 22) | nonce;                                          // 24 бита
    const v = (BigInt(head) << 36n) | macOf(head);                              // 60 бит
    let s = ''; for (let i = 11; i >= 0; i--) s += ALPHA[Number((v >> BigInt(i * 5)) & 31n)];
    return { norm: 'MC' + s, pretty: `MC-${s.slice(0, 4)}-${s.slice(4, 8)}-${s.slice(8, 12)}` };
  }
  function decodeKey(norm) {
    if (norm.length !== 14 || !norm.startsWith('MC')) return null;
    let v = 0n;
    for (const ch of norm.slice(2)) { const i = ALPHA.indexOf(ch); if (i < 0) return null; v = (v << 5n) | BigInt(i); }
    const head = Number(v >> 36n), mac = v & ((1n << 36n) - 1n);
    if (mac !== macOf(head)) return null;
    const type = head >> 22;
    return { type, days: DAYS_BY_TYPE[type], trial: type === 0 };
  }

  // ---- коды сброса пароля: без базы, действуют 30–60 минут и работают на любом компьютере
  const WIN = 30 * 60000;
  function resetCode(username, window) {
    const h = crypto.createHmac('sha256', secret).update('reset:' + username.toLowerCase() + ':' + window).digest();
    return [...h.subarray(0, 8)].map((x) => ALPHA[x % 32]).join('');
  }

  const subOf = (u) => {
    const exp = db.subs[u.id] || null, active = !!exp && exp > nowIso(), role = roleOf(u);
    return { active, expiresAt: exp, role, canPlay: active || role === 'admin' };
  };

  // ================= маршруты (формат ответов как у сервера)
  return async function handle(method, p, body, token, hwidHint) {
    load();
    body = body || {};
    const err = (error, status = 400) => ({ error, code: error, _status: status });
    const needUser = () => authed(token);

    if (method === 'POST' && p === '/register') {
      if (limited('reg', 20, 3600000)) return err('too_many_attempts');
      const username = String(body.username || '').trim(), email = String(body.email || '').trim().toLowerCase(), password = String(body.password || '');
      if (!/^[A-Za-z0-9_]{3,16}$/.test(username)) return err('bad_username');
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 100) return err('bad_email');
      if (password.length < 8 || password.length > 128) return err('bad_password');
      if (db.users.some((u) => u.username.toLowerCase() === username.toLowerCase() || u.email === email)) return err('already_exists');
      const u = { id: db.nextId++, username, email, hash: hashPw(password), created: nowIso() };
      db.users.push(u);
      const t = newSession(u.id); save();
      return { token: t, user: pubUser(u) };
    }
    if (method === 'POST' && p === '/login') {
      if (limited('login', 10, 600000)) return err('too_many_attempts');
      const login = String(body.login || '').trim().toLowerCase(), password = String(body.password || '');
      const u = db.users.find((x) => x.username.toLowerCase() === login || x.email === login);
      if (!u || !checkPw(password, u.hash)) return err('bad_credentials', 401);
      const t = newSession(u.id); save();
      return { token: t, user: pubUser(u) };
    }
    if (method === 'POST' && p === '/reset-password') {
      if (limited('rst', 10, 600000)) return err('too_many_attempts');
      const login = String(body.login || '').trim().toLowerCase(), code = normKey(body.code), np = String(body.newPassword || '');
      if (np.length < 8 || np.length > 128) return err('bad_password');
      const u = db.users.find((x) => x.username.toLowerCase() === login || x.email === login);
      const w = Math.floor(Date.now() / WIN);
      if (!u || ![w, w - 1].some((k) => resetCode(u.username, k) === code)) return err('bad_reset_code');
      u.hash = hashPw(np);
      for (const k of Object.keys(db.sessions)) if (db.sessions[k].userId === u.id) delete db.sessions[k];
      save();
      return { ok: true };
    }

    // дальше нужен вход
    const u = needUser();
    if (!u) return err('not_authorized', 401);

    if (method === 'GET' && p === '/me') return { user: pubUser(u) };
    if (method === 'POST' && p === '/logout') { delete db.sessions[sha(token)]; save(); return { ok: true }; }
    if (method === 'GET' && p === '/subscription') return subOf(u);

    if (method === 'POST' && p === '/change-password') {
      if (limited('chpw:' + u.id, 5, 600000)) return err('too_many_attempts');
      const np = String(body.newPassword || '');
      if (np.length < 8 || np.length > 128) return err('bad_password');
      if (!checkPw(String(body.oldPassword || ''), u.hash)) return err('wrong_old_password', 401);
      u.hash = hashPw(np);
      const cur = sha(token);
      for (const k of Object.keys(db.sessions)) if (db.sessions[k].userId === u.id && k !== cur) delete db.sessions[k];
      save();
      return { ok: true };
    }

    if (method === 'POST' && p === '/activate-key') {
      if (limited('act:' + u.id, 10, 600000)) return err('too_many_attempts');
      const norm = normKey(body.key);
      if (norm.length !== 14 || !norm.startsWith('MC')) return err('bad_key');
      const k = decodeKey(norm);
      if (!k) return err('key_not_found', 404);
      const kh = sha(norm);
      if (db.usedKeys[kh]) return err('key_used', 409);
      if (k.trial) {
        const hw = String(body.hwid || '');
        if (!/^[a-f0-9]{64}$/.test(hw)) return err('bad_hwid');
        const hh = sha('hwid:' + hw);
        if (db.trials[hh] || Object.values(db.trials).includes(u.id)) return err('trial_already_used', 409);
        db.trials[hh] = u.id;
      }
      db.usedKeys[kh] = { by: u.id, at: nowIso() };
      const cur = subOf(u);
      const base = cur.active ? Date.parse(cur.expiresAt) : Date.now();
      const expiresAt = new Date(base + k.days * 86400000).toISOString();
      db.subs[u.id] = expiresAt;
      const rec = db.issued.find((x) => x.hash === kh); if (rec) { rec.used_by = u.username; rec.used_at = nowIso(); }
      save();
      return { active: true, expiresAt, days: k.days };
    }

    // ---- админ
    if (p.startsWith('/admin/')) {
      if (roleOf(u) !== 'admin') return err('forbidden', 403);
      if (method === 'POST' && p === '/admin/create-key') {
        const trial = !!body.trial, days = trial ? 1 : Number(body.days);
        if (![1, 7, 30].includes(days)) return err('bad_days');
        const count = Math.max(1, Math.min(20, Number(body.count) || 1));
        const type = trial ? 0 : days === 1 ? 1 : days === 7 ? 2 : 3;
        const keys = [];
        for (let i = 0; i < count; i++) {
          const { norm, pretty } = encodeKey(type);
          keys.push(pretty);
          db.issued.unshift({ id: db.nextId++, hash: sha(norm), key_hint: `MC-****-****-${norm.slice(-4)}`, duration_days: days, is_trial: trial ? 1 : 0, is_used: 0, created_at: nowIso(), used_at: null, used_by: null });
        }
        db.issued = db.issued.slice(0, 500);
        save();
        return { keys, days, trial };
      }
      if (method === 'GET' && p === '/admin/keys') {
        return { keys: db.issued.slice(0, 200).map((k) => ({ ...k, is_used: db.usedKeys[k.hash] ? 1 : 0, hash: undefined })) };
      }
      if (method === 'POST' && p === '/admin/reset-code') {
        const name = String(body.username || '').trim();
        if (!/^[A-Za-z0-9_]{3,16}$/.test(name)) return err('user_not_found', 404);
        const raw = resetCode(name, Math.floor(Date.now() / WIN));
        return { username: name, code: `${raw.slice(0, 4)}-${raw.slice(4)}`, minutes: 30 };
      }
      if (method === 'GET' && p === '/admin/users') {
        return { users: db.users.slice().reverse().map((x) => ({ id: x.id, username: x.username, email: x.email, role: roleOf(x), created_at: x.created, sub_expires: db.subs[x.id] || null })) };
      }
    }
    return err('not_found', 404);
  };
};
