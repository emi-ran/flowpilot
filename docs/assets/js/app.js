// Rule definitions for Interactive Workbench
const rules = [
  {
    screen: "home_screen",
    file: "flowpilot_rule_night_mode.json",
    code: `{\n  "rule_id": "rule_preset_night_mode",\n  "name": "Gece Rutini (Sessiz & Ekran)",\n  "enabled": true,\n  "trigger": {\n    "type": "TIME",\n    "target_time": "23:00",\n    "repeat_days": ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]\n  },\n  "conditions": [\n    { "type": "SCREEN_ON", "expected": true }\n  ],\n  "actions": [\n    { "type": "AUTO_BRIGHTNESS_OFF" },\n    { "type": "SET_BRIGHTNESS", "value": 15 },\n    { "type": "SET_RINGER_MODE", "mode": "SILENT" },\n    { "type": "SET_DND", "enabled": true }\n  ]\n}`
  },
  {
    screen: "presets_screen",
    file: "flowpilot_rule_leaving_home.json",
    code: `{\n  "rule_id": "rule_preset_leave_home",\n  "name": "Evden Çıkış (Hızlı Güç)",\n  "enabled": true,\n  "trigger": {\n    "type": "WIFI_DISCONNECTED",\n    "previous_ssid": "Home_Mesh_5G"\n  },\n  "conditions": [],\n  "actions": [\n    { "type": "WIFI_OFF", "privileged_via": "SHIZUKU_IPC" },\n    { "type": "AUTO_BRIGHTNESS_ON" },\n    { "type": "SET_VOLUME", "stream": "RINGER", "level": 80 }\n  ]\n}`
  },
  {
    screen: "settings_screen",
    file: "flowpilot_rule_battery_saver.json",
    code: `{\n  "rule_id": "rule_preset_battery_saver",\n  "name": "Kritik Pil Koruma",\n  "enabled": true,\n  "trigger": {\n    "type": "BATTERY_LEVEL_DROPS_BELOW",\n    "percentage": 20\n  },\n  "conditions": [\n    { "type": "IS_CHARGING", "expected": false }\n  ],\n  "actions": [\n    { "type": "BLUETOOTH_OFF", "privileged_via": "SHIZUKU_IPC" },\n    { "type": "AUTO_BRIGHTNESS_OFF" },\n    { "type": "SET_BRIGHTNESS", "value": 10 }\n  ]\n}`
  },
  {
    screen: "create_screen",
    file: "flowpilot_flow_builder.kt",
    code: `// FlowPilot Rule Construction Pipeline\nval newRule = AutomationRule(\n    id = UUID.randomUUID().toString(),\n    title = "Ofis Wi-Fi Bağlandığında",\n    trigger = Trigger.WifiConnected(ssid = "Office_Corp"),\n    condition = Condition.TimeBetween("09:00", "18:00"),\n    actions = listOf(\n        Action.SetVolume(stream = AudioStream.RINGER, level = 0),\n        Action.SetVibration(enabled = true)\n    ),\n    enabled = true\n)\nruleRepository.saveRule(newRule)`
  }
];

let currentRuleIdx = 0;
let currentLang = 'tr';

// Translations Dictionary
const translations = {
  tr: {
    nav_workbench: "İncele",
    nav_matrix: "Kabiliyetler",
    nav_comparison: "Mimari",
    nav_install: "Kurulum",
    hero_badge: "Açık Kaynak & Bağımsız",
    hero_title: "Root Gerektirmeyen Android Otomasyon Motoru.",
    hero_desc: "Gereksiz pil tüketen sürekli arka plan servisleri ve hantal yapılar yerine; Shizuku IPC, Kotlin Coroutines ve modern Android olay yayınlarıyla çalışan hafif, gizlilik odaklı otomasyon.",
    btn_download: "APK İndir (v1.0.2)",
    btn_copy: "KOPYALA",
    copied: "✓ KOPYALANDI",
    stat_apk_label: "APK Boyutu",
    stat_runtime_label: "Çalışma Modeli",
    stat_shizuku_label: "Yetki Katmanı",
    stat_telemetry_label: "Telemetri & Ağ",
    stat_telemetry_val: "0 telemetri <small>(Yalnızca yapılandırılan webhook'lar ağ kullanır)</small>",
    wb_label: "İnteraktif Kural Mühendisliği",
    wb_title: "Uygulamayı ve Otomasyon Mantığını İnceleyin",
    wb_desc: "Aşağıdaki hazır kuralları seçerek hem cihaz ekranındaki görünümünü hem de arkasındaki veri/eylem yapısını canlı olarak görün.",
    rule_0_title: "Gece Rutini (Sessiz & Ekran)",
    rule_0_tag: "ZAMAN",
    rule_0_summary: "Saat 23:00 olunca otomatik parlaklığı kapat, seviyeyi %10 yap ve Rahatsız Etmeyin'e geç.",
    rule_1_title: "Evden Çıkış (Hızlı Güç)",
    rule_1_tag: "AĞ",
    rule_1_summary: "Ev Wi-Fi bağlantısı koptuğunda Wi-Fi'ı devre dışı bırak ve otomatik parlaklığı aç.",
    rule_2_title: "Kritik Pil Koruma",
    rule_2_tag: "GÜÇ",
    rule_2_summary: "Pil %20'nin altına indiğinde Bluetooth'u kapat ve ekranı en düşük enerjiye al.",
    rule_3_title: "Kural Oluşturucu",
    rule_3_tag: "UI",
    rule_3_summary: "Tetikleyici, koşul ve sıralı eylem adımlarını görsel olarak yapılandırın.",
    comp_label: "Mimari Tercihler",
    comp_title: "Neden Geleneksel Araçlar Yerine FlowPilot?",
    comp_desc: "Yıllanmış otomasyon uygulamaları karmaşık izinler, sürekli CPU tüketen arka plan döngüleri ve kapalı kaynak kodlarla gelir. FlowPilot ise modern Android pratikleriyle tasarlandı.",
    th_feature: "Kriter / Mimari",
    th_legacy: "Geleneksel / Diğer Araçlar",
    comp_row_1_title: "Arka Plan Kaynak Tüketimi",
    comp_row_1_old: "Sürekli çalışan Foreground Service ve Wake-Lock",
    comp_row_1_new: "Olay tabanlı (Event-driven) Broadcast & WorkManager",
    comp_row_2_title: "Ayrıcalıklı Sistem Erişimi",
    comp_row_2_old: "Root veya karmaşık ADB WRITE_SECURE_SETTINGS betikleri",
    comp_row_2_new: "Standart Shizuku IPC (Root gerektirmez)",
    comp_row_3_title: "Uygulama Boyutu & Şişkinlik",
    comp_row_4_title: "Gizlilik & Telemetri",
    comp_row_4_old: "Crashlytics, analitik SDK'ları, hesap zorunluluğu",
    comp_row_4_new: "Sıfır telemetri, sıfır hesap, yalnızca kullanıcı yapılandırırsa ağ",
    comp_row_5_title: "Kaynak Kod Lisansı",
    comp_row_5_old: "Kapalı kaynak (Proprietary / Ücretli)",
    comp_row_5_new: "%100 Açık Kaynak (GitHub / Bağımsız)",
    mat_label: "Yetkinlik Haritası",
    mat_title: "Desteklenen Tetikleyiciler ve Eylemler",
    mat_desc: "FlowPilot, Android'in yerel API sınırları dahilinde çalışırken, ayrıcalıklı işlemler için Shizuku'dan yararlanır.",
    cat_triggers: "Tetikleyiciler (Triggers)",
    trig_time: "Belirli Saat / Periyot",
    trig_geofence: "Coğrafi Konum Alanı (Geofence)",
    trig_bat_level: "Pil Seviyesi Değişimi",
    trig_bat_state: "Şarj Bağlandı / Kesildi",
    trig_wifi_state: "Wi-Fi Bağlandı / Kesildi",
    trig_wifi_ssid: "Belirli SSID Eşleşmesi",
    trig_bt_state: "Bluetooth Cihaz Durumu",
    cat_display_audio: "Ekran, Güç ve Ses",
    act_bright_level: "Ekran Parlaklığı Ayarı",
    act_bright_auto: "Otomatik Parlaklık Aç/Kapa",
    act_vol_media: "Medya / Zil Sesi Seviyesi",
    act_vol_ringer: "Sessiz / Titreşim Modu",
    act_dnd: "Rahatsız Etmeyin (DND)",
    cat_hardware: "Donanım & Sistem Kontrolü",
    act_wifi_toggle: "Wi-Fi Aç / Kapat",
    act_bt_toggle: "Bluetooth Aç / Kapat",
    act_hotspot: "Taşınabilir Erişim Noktası",
    act_launch_app: "Uygulama / Aktivite Başlat",
    act_notify: "Özel Sistem Bildirimi",
    gal_label: "Arayüz Vitrini",
    gal_title: "Material 3 Expressive Tasarımı",
    gal_desc: "Telefonunuzun doğal parçası gibi hissettiren saf Jetpack Compose bileşenleri, dinamik renk uyumu ve yüksek bilgi yoğunluğu.",
    gal_1_title: "Ana Kontrol Paneli",
    gal_1_sub: "Aktif kurallar, durum anahtarları ve servis göstergesi.",
    gal_2_title: "Hazır Şablon Kataloğu",
    gal_2_sub: "Tek tıkla içe aktarılabilen doğrulanmış otomasyonlar.",
    gal_3_title: "Kural Oluşturucu",
    gal_3_sub: "Kategori bazlı tetikleyici ve eylem yapılandırması.",
    gal_4_title: "Sistem & Bilgi",
    gal_4_sub: "Sürüm, lisans ve cihaz ayrıcalık durumu.",
    inst_label: "Hızlı Dağıtım",
    inst_title: "Kurulum & Shizuku Yapılandırması",
    inst_desc: "Root gerekmez. Kablosuz Hata Ayıklama (Android 11+) veya tek bir ADB komutuyla 60 saniyede hazır.",
    step_1_badge: "ADIM 01",
    step_1_title: "APK'yı Yükleyin",
    step_1_desc: "GitHub Releases sayfasından derlenmiş FlowPilot-v1.0.2.apk dosyasını indirin veya ADB ile doğrudan cihaza kurun.",
    step_2_badge: "ADIM 02",
    step_2_title: "Shizuku'yu Başlatın",
    step_2_desc: "Geliştirici Seçenekleri'nden Kablosuz Hata Ayıklama üzerinden eşleyin veya cihazınızı USB ile bağlayıp terminalden başlatın.",
    geofence_box_tag: "GEOFENCE & PİL",
    geofence_box_title: "Donanım Tabanlı Konum Alanı (Geofencing)",
    geofence_box_desc: "Sürekli GPS taraması ve pil tüketen döngüler yerine Google Play Services donanım geofence API'si kullanılır. Giriş ve çıkışların arka planda güvenle algılanması için Hassas Konum (Fine Location) ve Arka Planda Her Zaman İzin Ver (Allow all the time) izni gereklidir.",
    backup_box_tag: "YEDEK & GİZLİLİK",
    backup_box_title: "Şifreli Tam Yedek",
    backup_box_desc: "Normal JSON dışa aktarma webhook sırlarını çıkarır ve içe aktarılan kuralları devre dışı bırakır. Şifreli tam yedek; tüm kural verisini, AES-256-GCM ve parola ile taşır; geri yükleme etkinlik durumunu da korur.",
    oem_box_title: "Xiaomi HyperOS / Cihaz Optimizasyonu Notu",
    oem_box_desc: "Xiaomi 15T Pro veya HyperOS/MIUI cihazlarda kuralların ekran kapalıyken dakik çalışması için: FlowPilot için Otomatik Başlatma (Autostart) iznini açın ve Pil Tasarrufu: Kısıtlama Yok (No Restrictions) olarak ayarlayın.",
    footer_sub: "Jetpack Compose, Kotlin Coroutines & Shizuku ile geliştirildi.",
    menu_open: "Menüyü Aç",
    menu_close: "Menüyü Kapat"
  },
  en: {
    nav_workbench: "Workbench",
    nav_matrix: "Capabilities",
    nav_comparison: "Architecture",
    nav_install: "Installation",
    hero_badge: "Open Source & Independent",
    hero_title: "Rootless Android Automation Engine.",
    hero_desc: "Forget bloated battery-draining foreground wake-locks and complex background services. FlowPilot delivers lean, privacy-first automation powered by Shizuku IPC, Kotlin Coroutines, and native Android broadcasts.",
    btn_download: "Download APK (v1.0.2)",
    btn_copy: "COPY",
    copied: "✓ COPIED",
    stat_apk_label: "APK Size",
    stat_runtime_label: "Runtime Model",
    stat_shizuku_label: "Privilege Layer",
    stat_telemetry_label: "Telemetry & Network",
    stat_telemetry_val: "Zero telemetry <small>(network only for configured webhooks)</small>",
    wb_label: "Interactive Rule Engineering",
    wb_title: "Inspect the App & Automation Logic",
    wb_desc: "Select any rule preset below to inspect both its on-device screenshot and the underlying structured rule definition AST.",
    rule_0_title: "Night Routine (Silent & Display)",
    rule_0_tag: "TIME",
    rule_0_summary: "At 23:00, turn off auto-brightness, set display brightness to 10%, and enable Do Not Disturb.",
    rule_1_title: "Leaving Home (Quick Power)",
    rule_1_tag: "NETWORK",
    rule_1_summary: "When disconnected from home Wi-Fi, disable Wi-Fi radio and enable auto-brightness.",
    rule_2_title: "Critical Battery Saver",
    rule_2_tag: "POWER",
    rule_2_summary: "When battery drops below 20%, turn off Bluetooth and reduce screen brightness.",
    rule_3_title: "Flow Builder",
    rule_3_tag: "UI",
    rule_3_summary: "Visually compose triggers, conditions, and sequential action steps with instant validation.",
    comp_label: "Architectural Choices",
    comp_title: "Why FlowPilot Over Legacy Tools?",
    comp_desc: "Legacy automation apps rely on complex permission hacks, perpetual CPU-wake services, and closed-source binaries. FlowPilot is engineered with modern Android best practices.",
    th_feature: "Criterion / Architecture",
    th_legacy: "Legacy / Other Tools",
    comp_row_1_title: "Background Resource Footprint",
    comp_row_1_old: "Continuous Foreground Service & persistent Wake-Lock",
    comp_row_1_new: "Event-driven BroadcastReceiver & WorkManager",
    comp_row_2_title: "Privileged System Access",
    comp_row_2_old: "Root or brittle ADB WRITE_SECURE_SETTINGS scripts",
    comp_row_2_new: "Standard Shizuku IPC (No root required)",
    comp_row_3_title: "App Size & Bloat",
    comp_row_4_title: "Privacy & Telemetry",
    comp_row_4_old: "Crashlytics, third-party analytics SDKs, mandatory account",
    comp_row_4_new: "Zero telemetry, zero accounts, network only when user configures it",
    comp_row_5_title: "Source Code License",
    comp_row_5_old: "Closed source (Proprietary / Paid subscriptions)",
    comp_row_5_new: "100% Open Source (GitHub / Independent)",
    mat_label: "Capability Matrix",
    mat_title: "Supported Triggers and Actions",
    mat_desc: "FlowPilot operates within Android's official permission framework and leverages Shizuku for privileged hardware toggles.",
    cat_triggers: "Triggers",
    trig_time: "Specific Time / Interval",
    trig_geofence: "Geofence Area (Enter / Exit)",
    trig_bat_level: "Battery Level Change",
    trig_bat_state: "Charger Connected / Disconnected",
    trig_wifi_state: "Wi-Fi Connected / Disconnected",
    trig_wifi_ssid: "Specific SSID Match",
    trig_bt_state: "Bluetooth Device State",
    cat_display_audio: "Display, Power & Audio",
    act_bright_level: "Manual Screen Brightness",
    act_bright_auto: "Auto-Brightness Toggle",
    act_vol_media: "Media / Ring Volume Levels",
    act_vol_ringer: "Silent / Vibrate Mode",
    act_dnd: "Do Not Disturb (DND Policy)",
    cat_hardware: "Hardware & System Controls",
    act_wifi_toggle: "Wi-Fi Toggle",
    act_bt_toggle: "Bluetooth Toggle",
    act_hotspot: "Portable Hotspot",
    act_launch_app: "Launch App / Activity",
    act_notify: "Custom System Notification",
    gal_label: "UI Showcase",
    gal_title: "Material 3 Expressive Craft",
    gal_desc: "Pure Jetpack Compose components designed to feel native to your phone, featuring dynamic color matching and dense readability.",
    gal_1_title: "Main Dashboard",
    gal_1_sub: "Active rules, master execution toggles, and service health.",
    gal_2_title: "Preset Catalog",
    gal_2_sub: "Verified pre-packaged automations ready to import in one click.",
    gal_3_title: "Rule Builder",
    gal_3_sub: "Category-filtered trigger and action selection sheets.",
    gal_4_title: "System & Info",
    gal_4_sub: "Build version, license, and device privilege status.",
    inst_label: "Fast Deployment",
    inst_title: "Installation & Shizuku Setup",
    inst_desc: "No root required. Ready in under 60 seconds via Wireless Debugging (Android 11+) or a single ADB shell command.",
    step_1_badge: "STEP 01",
    step_1_title: "Install the APK",
    step_1_desc: "Download FlowPilot-v1.0.2.apk from GitHub Releases or sideload it directly with adb install.",
    step_2_badge: "STEP 02",
    step_2_title: "Start Shizuku",
    step_2_desc: "Pair via Wireless Debugging in Developer Options or start it over USB via the standard start script.",
    geofence_box_tag: "GEOFENCE & BATTERY",
    geofence_box_title: "Hardware-Backed Geofencing",
    geofence_box_desc: "Instead of continuous battery-draining GPS loops, FlowPilot uses Google Play Services hardware geofencing. Reliable arrival and departure detection requires Precise Location ('Fine Location') and Background Location ('Allow all the time').",
    backup_box_tag: "BACKUP & PRIVACY",
    backup_box_title: "Encrypted Full Backup",
    backup_box_desc: "Normal JSON export removes webhook secrets and disables imported rules. Encrypted full backup carries complete rule data with AES-256-GCM and a password; restore also preserves enabled state.",
    oem_box_title: "Xiaomi HyperOS / OEM Optimization Note",
    oem_box_desc: "To ensure rules execute accurately when screen is off on Xiaomi 15T Pro or HyperOS/MIUI devices: grant Autostart permission for FlowPilot and set Battery Saver to 'No Restrictions'.",
    footer_sub: "Developed with Jetpack Compose, Kotlin Coroutines & Shizuku.",
    menu_open: "Open Menu",
    menu_close: "Close Menu"
  }
};

function selectRule(idx) {
  currentRuleIdx = idx;

  // Update buttons
  for (let i = 0; i < 4; i++) {
    const btn = document.getElementById(`wb-rule-${i}`);
    if (btn) btn.classList.toggle('active', i === idx);
  }

  // Update image with smooth crossfade & translateY
  const img = document.getElementById('wb-screen-img');
  if (img) {
    img.classList.remove('crossfade-in');
    img.classList.add('crossfade-out');
    setTimeout(() => {
      img.src = `assets/screenshots/${currentLang}/${rules[idx].screen}.png`;
      img.classList.remove('crossfade-out');
      img.classList.add('crossfade-in');
    }, 160);
  }

  // Update Code view with gentle opacity transition
  const specBox = document.getElementById('wb-spec-box');
  if (specBox) {
    specBox.style.opacity = '0.45';
    setTimeout(() => {
      const specName = document.getElementById('wb-spec-name');
      if (specName) specName.textContent = rules[idx].file;
      renderSyntaxHighlighted(rules[idx].code);
      specBox.style.opacity = '1';
    }, 140);
  }
}

function renderSyntaxHighlighted(raw) {
  const codeEl = document.getElementById('wb-code-content');
  if (!codeEl) return;

  // Escape HTML
  let formatted = raw
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');

  // Format JSON / Kotlin syntax simply
  formatted = formatted
    .replace(/"(.*?)":/g, '<span class="code-prop">"$1"</span>:')
    .replace(/:\s*"(.*?)"/g, ': <span class="code-str">"$1"</span>')
    .replace(/:\s*(\d+)/g, ': <span class="code-num">$1</span>')
    .replace(/:\s*(true|false)/g, ': <span class="code-kw">$1</span>')
    .replace(/(\/\/.*)/g, '<span class="code-comment">$1</span>')
    .replace(/\b(val|fun|class|listOf)\b/g, '<span class="code-kw">$1</span>');

  codeEl.innerHTML = formatted;
}

function setLang(lang, persist = true) {
  currentLang = lang;
  document.querySelectorAll('.lang-btn-tr').forEach(btn => btn.classList.toggle('active', lang === 'tr'));
  document.querySelectorAll('.lang-btn-en').forEach(btn => btn.classList.toggle('active', lang === 'en'));
  const btnTr = document.getElementById('btn-tr');
  const btnEn = document.getElementById('btn-en');
  if (btnTr) btnTr.classList.toggle('active', lang === 'tr');
  if (btnEn) btnEn.classList.toggle('active', lang === 'en');
  document.documentElement.lang = lang;

  // Update text nodes with data-i18n
  const dict = translations[lang];
  document.querySelectorAll('[data-i18n]').forEach(el => {
    const key = el.getAttribute('data-i18n');
    if (dict && dict[key]) {
      el.textContent = dict[key];
    }
  });
  document.querySelectorAll('[data-i18n-html]').forEach(el => {
    const key = el.getAttribute('data-i18n-html');
    if (dict && dict[key]) el.innerHTML = dict[key];
  });

  // Update menu toggle aria-label if present
  const menuBtn = document.getElementById('mobile-menu-btn');
  if (menuBtn) {
    const isOpen = menuBtn.classList.contains('active');
    menuBtn.setAttribute('aria-label', isOpen ? dict.menu_close : dict.menu_open);
  }

  // Update gallery screenshots
  document.querySelectorAll('.gal-img').forEach(img => {
    const screen = img.getAttribute('data-screen');
    img.src = `assets/screenshots/${lang}/${screen}.png`;
  });

  // Update workbench screen
  selectRule(currentRuleIdx);

  if (persist) {
    try {
      localStorage.setItem('flowpilot_lang_v2', lang);
    } catch (e) {}
  }
}

function copyAdbInstall(el) {
  const cmd = "adb install FlowPilot-v1.0.2.apk";
  if (window.getSelection) {
    window.getSelection().removeAllRanges();
  }
  navigator.clipboard.writeText(cmd).then(() => {
    const feedback = document.getElementById('adb-copy-feedback');
    const box = el || document.querySelector('.command-box');

    box.classList.add('copied-active');
    if (feedback) {
      feedback.textContent = translations[currentLang].copied || (currentLang === 'tr' ? "✓ KOPYALANDI" : "✓ COPIED");
    }

    setTimeout(() => {
      box.classList.remove('copied-active');
      if (feedback) {
        feedback.textContent = translations[currentLang].btn_copy || (currentLang === 'tr' ? "KOPYALA" : "COPY");
      }
    }, 750);
  }).catch(err => {
    console.error("Clipboard copy failed:", err);
  });
}

/* Mobile Menu Initialization */
function initMobileMenu() {
  const menuBtn = document.getElementById('mobile-menu-btn');
  const navItems = document.getElementById('nav-items');
  if (!menuBtn || !navItems) return;

  const toggleMenu = (open) => {
    const shouldOpen = typeof open === 'boolean' ? open : !navItems.classList.contains('open');
    navItems.classList.toggle('open', shouldOpen);
    menuBtn.classList.toggle('active', shouldOpen);
    menuBtn.setAttribute('aria-expanded', shouldOpen ? 'true' : 'false');
    const dict = translations[currentLang] || translations.tr;
    menuBtn.setAttribute('aria-label', shouldOpen ? dict.menu_close : dict.menu_open);
  };

  menuBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    toggleMenu();
  });

  // Close when clicking nav links
  navItems.querySelectorAll('.nav-link, .btn').forEach(link => {
    link.addEventListener('click', () => {
      if (window.innerWidth <= 820) {
        toggleMenu(false);
      }
    });
  });

  // Close when clicking outside
  document.addEventListener('click', (e) => {
    if (navItems.classList.contains('open') && !navItems.contains(e.target) && !menuBtn.contains(e.target)) {
      toggleMenu(false);
    }
  });

  // Close on Escape key
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && navItems.classList.contains('open')) {
      toggleMenu(false);
      menuBtn.focus();
    }
  });
}

/* Modular Initializations */
function initHeaderScroll() {
  const header = document.getElementById('site-header');
  if (!header) return;

  let ticking = false;
  const updateHeader = () => {
    const top = window.scrollY || document.documentElement.scrollTop;
    if (top > 16) {
      header.classList.add('scrolled');
    } else {
      header.classList.remove('scrolled');
    }
    ticking = false;
  };

  window.addEventListener('scroll', () => {
    if (!ticking) {
      window.requestAnimationFrame(updateHeader);
      ticking = true;
    }
  }, { passive: true });
  updateHeader();
}

function initScrollProgress() {
  const progressBar = document.getElementById('scroll-progress-bar');
  if (!progressBar) return;

  let ticking = false;
  const updateProgress = () => {
    const scrollTop = window.scrollY || document.documentElement.scrollTop;
    const scrollHeight = document.documentElement.scrollHeight - document.documentElement.clientHeight;
    const progress = scrollHeight > 0 ? Math.min(Math.max(scrollTop / scrollHeight, 0), 1) : 0;
    progressBar.style.transform = `scaleX(${progress})`;
    ticking = false;
  };

  window.addEventListener('scroll', () => {
    if (!ticking) {
      window.requestAnimationFrame(updateProgress);
      ticking = true;
    }
  }, { passive: true });
  updateProgress();
}

function initRevealAnimations() {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    document.querySelectorAll('[data-reveal], .workbench').forEach(el => el.classList.add('is-revealed'));
    return;
  }

  const observer = new IntersectionObserver((entries, obs) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('is-revealed');
        obs.unobserve(entry.target);
      }
    });
  }, {
    threshold: 0.12,
    rootMargin: '0px 0px -40px 0px'
  });

  document.querySelectorAll('[data-reveal], .workbench').forEach(el => {
    observer.observe(el);
  });
}

function initSpotlight() {
  if (window.matchMedia('(max-width: 768px), (hover: none)').matches) return;

  const surfaces = document.querySelectorAll('.spotlight-surface');
  surfaces.forEach(surface => {
    let isHovered = false;

    surface.addEventListener('mouseenter', () => {
      isHovered = true;
    });

    surface.addEventListener('mouseleave', () => {
      isHovered = false;
    });

    surface.addEventListener('mousemove', (e) => {
      if (!isHovered) return;
      const rect = surface.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      surface.style.setProperty('--mouse-x', `${x}px`);
      surface.style.setProperty('--mouse-y', `${y}px`);
    }, { passive: true });
  });
}

function initTerminalReveal() {
  const termBox = document.getElementById('term-window-box');
  if (!termBox) return;

  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    termBox.classList.add('is-revealed');
    return;
  }

  const observer = new IntersectionObserver((entries, obs) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        termBox.classList.add('is-revealed');
        obs.unobserve(termBox);
      }
    });
  }, {
    threshold: 0.2,
    rootMargin: '0px 0px -40px 0px'
  });

  observer.observe(termBox);
}

function initReducedMotion() {
  const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
  const handleMotionChange = (e) => {
    if (e.matches) {
      document.querySelectorAll('[data-reveal], .workbench, .term-window').forEach(el => el.classList.add('is-revealed'));
    }
  };
  if (mediaQuery.addEventListener) {
    mediaQuery.addEventListener('change', handleMotionChange);
  }
}

// Init on DOMContentLoaded
window.addEventListener('DOMContentLoaded', () => {
  const saved = localStorage.getItem('flowpilot_lang_v2');
  const initialLang = saved ||
    (navigator.language.toLowerCase().startsWith('tr') ? 'tr' : 'en');
  setLang(initialLang, Boolean(saved));
  selectRule(0);

  initMobileMenu();
  initHeaderScroll();
  initScrollProgress();
  initRevealAnimations();
  initSpotlight();
  initTerminalReveal();
  initReducedMotion();
});
