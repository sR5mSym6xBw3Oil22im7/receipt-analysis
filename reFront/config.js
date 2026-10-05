const isLocalFrontend = window.location.protocol === "file:"
  || window.location.hostname === ""
  || window.location.hostname === "localhost"
  || window.location.hostname === "127.0.0.1";

const backendBaseUrl = isLocalFrontend
  ? "http://localhost:8081"
  : "https://receipt-analysis-b8po.onrender.com";

const publicBaseUrl = isLocalFrontend
  ? "http://localhost:5500/index.html"
  : "https://sr5msym6xbw3oil22im7.github.io/receipt-analysis/reFront/index.html";

window.APP_CONFIG = {
  API_BASE_URL: backendBaseUrl,
  ADMIN_BASE_URL: `${backendBaseUrl}/admin`,
  SELECT_URL: `${backendBaseUrl}/admin/select.html`,
  PUBLIC_BASE_URL: publicBaseUrl
};

// ---- 装飾（バラ・ステンドグラス・つる） ----
// ログイン画面は未ログインでも読める config.js しか使えないため、装飾もここに置く。
// 装飾（バラ・ステンドグラス・つる）を描画する共通スクリプト。
// 参考画像はそのまま使わず、すべてSVGで描き起こしている。
(() => {
  const NS = "http://www.w3.org/2000/svg";

  // ---- 共通シンボル（バラ・葉・つぼみ）とグラデーション ----
  const sprite = `
<svg xmlns="${NS}" width="0" height="0" style="position:absolute;width:0;height:0;overflow:hidden" aria-hidden="true" focusable="false">
  <defs>
    <radialGradient id="g-petal" cx="50%" cy="45%" r="60%">
      <stop offset="0" stop-color="#e2557e"/>
      <stop offset=".55" stop-color="#ef8fab"/>
      <stop offset="1" stop-color="#f9cbd8"/>
    </radialGradient>
    <radialGradient id="g-petal-soft" cx="50%" cy="45%" r="60%">
      <stop offset="0" stop-color="#e994ad"/>
      <stop offset="1" stop-color="#fbe1e8"/>
    </radialGradient>
    <linearGradient id="g-leaf" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#8fae8f"/>
      <stop offset="1" stop-color="#557a62"/>
    </linearGradient>
    <linearGradient id="g-gold" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#e8cfa4"/>
      <stop offset=".5" stop-color="#c49a6c"/>
      <stop offset="1" stop-color="#e3c697"/>
    </linearGradient>
    <radialGradient id="g-sun" cx="78%" cy="18%" r="75%">
      <stop offset="0" stop-color="#fff" stop-opacity=".85"/>
      <stop offset=".45" stop-color="#fff" stop-opacity=".25"/>
      <stop offset="1" stop-color="#fff" stop-opacity="0"/>
    </radialGradient>
    <radialGradient id="g-oval" cx="50%" cy="45%" r="60%">
      <stop offset="0" stop-color="#fde7ee"/>
      <stop offset=".6" stop-color="#f3a9c0"/>
      <stop offset="1" stop-color="#e07a9c"/>
    </radialGradient>
    <filter id="f-blur" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="3"/></filter>
    <path id="p-petal" d="M0 0C-24-8-30-38 0-46C30-38 24-8 0 0Z"/>
  </defs>
  <symbol id="rose" viewBox="-50 -50 100 100">
    <g stroke="#9c4762" stroke-opacity=".55" stroke-width="1.4" stroke-linejoin="round">
      <g fill="url(#g-petal-soft)">
        <use href="#p-petal"/><use href="#p-petal" transform="rotate(72)"/><use href="#p-petal" transform="rotate(144)"/><use href="#p-petal" transform="rotate(216)"/><use href="#p-petal" transform="rotate(288)"/>
      </g>
      <g fill="url(#g-petal)" transform="rotate(36) scale(.68)">
        <use href="#p-petal"/><use href="#p-petal" transform="rotate(72)"/><use href="#p-petal" transform="rotate(144)"/><use href="#p-petal" transform="rotate(216)"/><use href="#p-petal" transform="rotate(288)"/>
      </g>
      <g fill="#e46c90" transform="scale(.4)">
        <use href="#p-petal"/><use href="#p-petal" transform="rotate(72)"/><use href="#p-petal" transform="rotate(144)"/><use href="#p-petal" transform="rotate(216)"/><use href="#p-petal" transform="rotate(288)"/>
      </g>
    </g>
    <path d="M-1 3a5 5 0 1 1 6-5a9 9 0 1 1-13-3" fill="none" stroke="#a83b5e" stroke-width="2" stroke-linecap="round"/>
  </symbol>
  <symbol id="leaf" viewBox="0 -14 44 28">
    <path d="M1 0C10-13 30-13 43 0C30 13 10 13 1 0Z" fill="url(#g-leaf)" stroke="#4d6b56" stroke-opacity=".5"/>
    <path d="M3 0H38" stroke="#e7efe5" stroke-opacity=".6" stroke-width="1.2"/>
  </symbol>
  <symbol id="bud" viewBox="-12 -20 24 32">
    <path d="M0-18C9-12 9 2 0 6C-9 2-9-12 0-18Z" fill="#e46c90" stroke="#9c4762" stroke-opacity=".5"/>
    <path d="M0 10C-8 8-10 0-6-4C-2 2 2 2 6-4C10 0 8 8 0 10Z" fill="url(#g-leaf)"/>
  </symbol>
</svg>`;
  document.body.insertAdjacentHTML("afterbegin", sprite);

  // ---- 乱数（毎回同じ模様になるよう種を固定） ----
  function rng(seed) {
    let s = seed >>> 0;
    return () => {
      s = (s * 1664525 + 1013904223) >>> 0;
      return s / 4294967296;
    };
  }

  // 尖頭アーチ（ゴシック窓）の外形
  function pointedArch(x, y, w, h, ratio = 0.82) {
    const r = w * ratio;
    const ah = Math.sqrt(r * r - (r - w / 2) ** 2);
    return `M${x} ${y + h}L${x} ${y + ah}A${r} ${r} 0 0 1 ${x + w / 2} ${y}A${r} ${r} 0 0 1 ${x + w} ${y + ah}L${x + w} ${y + h}Z`;
  }

  const GLASS = ["#fbe3ea", "#f6c9d6", "#f1b5c7", "#fdf1f4", "#f8d6df", "#ecc8df", "#fff7f9", "#f4d3cf", "#efbfd0", "#d3ebe7", "#f9dfe6"];

  // ステンドグラスのモザイク（ゆがんだ格子を三角形に分割）
  function mosaic(w, h, cell, seed, palette = GLASS) {
    const rand = rng(seed);
    const cols = Math.ceil(w / cell);
    const rows = Math.ceil(h / cell);
    const pts = [];
    for (let j = 0; j <= rows; j++) {
      pts.push([]);
      for (let i = 0; i <= cols; i++) {
        const edge = i === 0 || j === 0 || i === cols || j === rows;
        const jx = edge ? 0 : (rand() - 0.5) * cell * 0.7;
        const jy = edge ? 0 : (rand() - 0.5) * cell * 0.7;
        pts[j].push([(i * w) / cols + jx, (j * h) / rows + jy]);
      }
    }
    const pick = () => {
      const r = rand();
      if (palette === GLASS && r < 0.04) return GLASS[9]; // ときどき淡い水色
      if (palette !== GLASS && r < 0.05) return "#f3d9a8"; // ときどき琥珀色
      return palette[Math.floor(rand() * palette.length) % (palette === GLASS ? 9 : palette.length)];
    };
    const f = (p) => `${p[0].toFixed(1)} ${p[1].toFixed(1)}`;
    let out = "";
    for (let j = 0; j < rows; j++) {
      for (let i = 0; i < cols; i++) {
        const a = pts[j][i], b = pts[j][i + 1], c = pts[j + 1][i + 1], d = pts[j + 1][i];
        const r = rand();
        if (r < 0.35) {
          out += `<path d="M${f(a)}L${f(b)}L${f(c)}L${f(d)}Z" fill="${pick()}"/>`;
        } else if (r < 0.7) {
          out += `<path d="M${f(a)}L${f(b)}L${f(c)}Z" fill="${pick()}"/><path d="M${f(a)}L${f(c)}L${f(d)}Z" fill="${pick()}"/>`;
        } else {
          out += `<path d="M${f(a)}L${f(b)}L${f(d)}Z" fill="${pick()}"/><path d="M${f(b)}L${f(c)}L${f(d)}Z" fill="${pick()}"/>`;
        }
      }
    }
    return out;
  }

  // 窓の中を伸びるバラの枝
  function climbingRose(cx, top, bottom, scale, rand) {
    const len = bottom - top;
    const stem = `M${cx} ${bottom}C${cx - 18 * scale} ${bottom - len * 0.3} ${cx + 22 * scale} ${bottom - len * 0.55} ${cx - 6 * scale} ${top + len * 0.18}`;
    const branch = `M${cx + 4 * scale} ${bottom - len * 0.45}C${cx + 30 * scale} ${bottom - len * 0.55} ${cx + 34 * scale} ${bottom - len * 0.7} ${cx + 26 * scale} ${top + len * 0.3}`;
    let g = `<path d="${stem}" fill="none" stroke="#7d5a52" stroke-width="${3 * scale}" stroke-linecap="round"/>`;
    g += `<path d="${branch}" fill="none" stroke="#7d5a52" stroke-width="${2 * scale}" stroke-linecap="round"/>`;
    const leaves = [[-20, 0.72, -150], [8, 0.62, -30], [-14, 0.5, 200], [14, 0.38, -20], [-18, 0.3, 170], [30, 0.5, -60], [20, 0.62, 20]];
    for (const [dx, t, rot] of leaves) {
      const s = (18 + rand() * 6) * scale;
      g += `<use href="#leaf" width="${s * 1.6}" height="${s}" x="${-s * 0.1}" y="${-s / 2}" transform="translate(${cx + dx * scale} ${top + len * t}) rotate(${rot})"/>`;
    }
    const roses = [[-6, 0.16, 46], [-10, 0.47, 38], [26, 0.28, 22]];
    for (const [dx, t, s0] of roses) {
      const s = s0 * scale;
      g += `<use href="#rose" width="${s}" height="${s}" x="${cx + dx * scale - s / 2}" y="${top + len * t - s / 2}"/>`;
    }
    g += `<use href="#bud" width="${12 * scale}" height="${16 * scale}" x="${cx + 36 * scale}" y="${top + len * 0.12}"/>`;
    return g;
  }

  // ステンドグラスの窓を描く
  //   variant: "hero"（大きな三連窓）/ "small"（見出し用の小窓）/ "tile"（メニュー用）
  function stainedWindow(el) {
    const variant = el.dataset.window || "small";
    const seed = Number(el.dataset.seed || 7);
    const rand = rng(seed + 99);
    const W = variant === "hero" ? 360 : 160;
    const H = variant === "hero" ? 520 : 220;
    const pad = variant === "hero" ? 14 : 8;
    const id = `w${Math.floor(Math.random() * 1e9)}`;
    const outer = pointedArch(0, 0, W, H);
    const inner = pointedArch(pad, pad, W - pad * 2, H - pad * 2);
    const lead = variant === "hero" ? 1.6 : 1.2;

    let tracery = "";
    let decoration = "";
    const iw = W - pad * 2;
    if (variant === "hero") {
      // 三連の尖頭アーチと丸窓
      const lw = (iw - 16) / 3;
      const baseY = 210;
      for (let k = 0; k < 3; k++) {
        const lx = pad + k * (lw + 8);
        const ly = k === 1 ? baseY - 40 : baseY;
        tracery += `<path d="${pointedArch(lx, ly, lw, H - pad - ly, 0.9)}"/>`;
      }
      tracery += `<circle cx="${W / 2}" cy="112" r="50"/>`;
      tracery += `<circle cx="${W / 2 - 92}" cy="150" r="24"/><circle cx="${W / 2 + 92}" cy="150" r="24"/>`;
      tracery += `<path d="M${pad} ${H - 150}H${W - pad}"/>`;
      decoration += `<use href="#rose" width="88" height="88" x="${W / 2 - 44}" y="68"/>`;
      decoration += `<use href="#rose" width="36" height="36" x="${W / 2 - 110}" y="132" opacity=".85"/>`;
      decoration += `<use href="#rose" width="36" height="36" x="${W / 2 + 74}" y="132" opacity=".85"/>`;
      decoration += climbingRose(W / 2, 200, H - pad, 1.25, rand);
    } else if (variant === "tile") {
      tracery += `<path d="M${W / 2} ${pad + 70}V${H}"/><circle cx="${W / 2}" cy="62" r="26"/>`;
      tracery += `<path d="M${pad} ${H - 70}H${W - pad}"/>`;
      decoration += `<use href="#rose" width="46" height="46" x="${W / 2 - 23}" y="39"/>`;
    } else {
      tracery += `<circle cx="${W / 2}" cy="60" r="24"/><path d="M${W / 2} 84V${H}"/><path d="M${pad} ${H - 64}H${W - pad}"/>`;
      decoration += `<use href="#rose" width="44" height="44" x="${W / 2 - 22}" y="38"/>`;
      decoration += climbingRose(W / 2 + 6, 120, H - pad, 0.55, rand);
    }

    const cell = variant === "hero" ? 30 : 22;
    const tint = el.dataset.tint ? `<rect width="${W}" height="${H}" fill="${el.dataset.tint}" opacity=".18"/>` : "";
    el.innerHTML = `
<svg viewBox="-6 -6 ${W + 12} ${H + 12}" xmlns="${NS}" aria-hidden="true" focusable="false" preserveAspectRatio="xMidYMax meet">
  <defs><clipPath id="${id}"><path d="${inner}"/></clipPath></defs>
  <path d="${outer}" fill="#f1ecea" stroke="#cfc4c2" stroke-width="3"/>
  <g clip-path="url(#${id})">
    <g stroke="#9b7882" stroke-opacity=".75" stroke-width="${lead}" stroke-linejoin="round">${mosaic(W, H, cell, seed)}</g>
    ${tint}
    <g fill="none" stroke="#f3eeec" stroke-width="${variant === "hero" ? 7 : 4}">${tracery}</g>
    <g fill="none" stroke="#a88f93" stroke-width="1" opacity=".7">${tracery}</g>
    ${decoration}
    <rect width="${W}" height="${H}" fill="url(#g-sun)"/>
  </g>
  <path d="${inner}" fill="none" stroke="url(#g-gold)" stroke-width="2"/>
</svg>`;
  }

  // 画面の上の角から垂れ下がるつるバラ
  function vine(side) {
    const rand = rng(side === "left" ? 11 : 23);
    const flip = side === "right" ? "scale(-1 1) translate(-320 0)" : "";
    let g = "";
    const stems = [
      "M-10 20C60 40 110 10 170 60S250 120 300 90",
      "M-10 60C40 120 20 200 70 260S120 360 90 430",
      "M40 40C90 90 140 130 150 210"
    ];
    for (const d of stems) g += `<path d="${d}" fill="none" stroke="#8a6b5f" stroke-width="2.4" stroke-linecap="round" opacity=".75"/>`;
    const leaves = [[40, 34, 10], [96, 30, -20], [140, 50, 30], [210, 88, -10], [260, 104, 20], [30, 120, 100], [52, 200, 70], [84, 280, 120], [98, 360, 80], [120, 120, 40], [148, 180, 110], [70, 70, 60]];
    for (const [x, y, r] of leaves) {
      const s = 20 + rand() * 10;
      g += `<use href="#leaf" width="${s * 1.6}" height="${s}" x="0" y="${-s / 2}" transform="translate(${x} ${y}) rotate(${r})" opacity=".9"/>`;
    }
    const roses = [[18, 26, 62], [118, 34, 46], [178, 70, 54], [44, 160, 50], [80, 248, 40], [150, 214, 34], [282, 92, 30], [92, 400, 30]];
    for (const [x, y, s] of roses) {
      g += `<use href="#rose" width="${s}" height="${s}" x="${x - s / 2}" y="${y - s / 2}" transform="rotate(${Math.floor(rand() * 60)} ${x} ${y})"/>`;
    }
    g += `<use href="#bud" width="14" height="20" x="300" y="70"/><use href="#bud" width="14" height="20" x="84" y="420"/>`;
    const wrap = document.createElement("div");
    wrap.className = `vine vine-${side}`;
    wrap.setAttribute("aria-hidden", "true");
    wrap.innerHTML = `<svg viewBox="0 0 320 460" xmlns="${NS}"><g transform="${flip}">${g}</g></svg>`;
    document.body.append(wrap);
  }

  // ゆっくり舞う花びら（動きを減らす設定の場合は出さない）
  function petals(count) {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const layer = document.createElement("div");
    layer.className = "petal-layer";
    layer.setAttribute("aria-hidden", "true");
    for (let i = 0; i < count; i++) {
      const p = document.createElement("span");
      p.className = "petal";
      p.style.left = `${Math.random() * 100}%`;
      p.style.animationDuration = `${14 + Math.random() * 12}s`;
      p.style.animationDelay = `${-Math.random() * 20}s`;
      p.style.setProperty("--drift", `${(Math.random() - 0.5) * 160}px`);
      p.style.setProperty("--size", `${8 + Math.random() * 8}px`);
      layer.append(p);
    }
    document.body.append(layer);
  }

  document.querySelectorAll("[data-window]").forEach(stainedWindow);
  vine("left");
  vine("right");
  petals(9);
})();
