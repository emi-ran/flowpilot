/* FlowPilot landing page — navigation, i18n, studio demo, motion. */

const LANGS = ['tr', 'en'];
const STORAGE_KEY = 'flowpilot_lang_v2';
const ADB_COMMAND = 'adb install FlowPilot-v2.2.0.apk';
const AUTOPLAY_MS = 7000;

const I18N = {
  tr: {
    nav_studio: 'Stüdyo',
    nav_caps: 'Kabiliyetler',
    nav_how: 'Nasıl çalışır',
    nav_compare: 'Mimari',
    nav_install: 'Kurulum',
    nav_faq: 'SSS',
    skip_link: 'İçeriğe geç',
    loading: 'Yükleniyor…',

    cta_download: 'APK indir',
    cta_download_apk: 'APK indir · 2,6 MB',
    cta_explore: 'Kural stüdyosunu aç',
    cta_source: 'Kaynak kodu',
    cta_title: 'Kendi otomasyonunuzu kurun.',
    cta_desc: '2,6 MB, root yok, telemetri yok. GPL-3.0 lisanslı, tamamen açık kaynak.',

    hero_eyebrow: 'Açık kaynak · Root gerektirmez · v2.2.0',
    hero_title: 'Android\'i <span class="accent">kendi kurallarınızla</span> yönetin.',
    hero_desc: 'Olay tabanlı otomasyon motoru: tetikleyici gerçekleştiğinde yalnız gereken dinleyici çalışır, ayrıcalıklı eylem Shizuku IPC ile uygulanır. Sürekli çalışan wake-lock yok.',
    meta_android: 'Android 8.0 ve üzeri',
    meta_telemetry: 'telemetri',
    meta_lang: 'arayüz',
    copy: 'Kopyala',
    copy_adb: 'ADB komutunu kopyala',
    copied: 'Kopyalandı',

    float_rule_title: 'Kural tetiklendi',
    float_rule_sub: 'Gece rutini',
    float_priv_title: 'Shizuku IPC',
    float_priv_sub: 'Wi-Fi kapatıldı',

    stat_apk: 'Release APK boyutu',
    stat_telemetry: 'Telemetri ve analitik',
    stat_min: 'Minimum Android sürümü',
    stat_lang: 'Uygulama dili',

    studio_eyebrow: 'İnteraktif stüdyo',
    studio_title: 'Kuralı seçin, cihazda nasıl çalıştığını izleyin.',
    studio_desc: 'Aşağıdaki üç hazır kural gerçek uygulamadan alınmış ekranlar ve kavramsal kural tanımıyla gösterilir. Tanımlar serileştirilmiş dışa aktarma biçimi değildir.',
    pipeline_trigger: 'Tetikleyici',
    pipeline_condition: 'Koşul',
    pipeline_action: 'Eylemler',

    rule_0_title: 'Gece rutini',
    rule_0_tag: 'Zaman',
    rule_0_desc: 'Saat 23:00\'te otomatik parlaklığı kapatır, seviyeyi %10 yapar ve Rahatsız Etmeyin\'i açar.',
    rule_1_title: 'Evden çıkış',
    rule_1_tag: 'Ağ',
    rule_1_desc: 'Ev Wi-Fi bağlantısı koptuğunda Wi-Fi\'ı kapatır ve otomatik parlaklığı açar.',
    rule_2_title: 'Pil koruma',
    rule_2_tag: 'Güç',
    rule_2_desc: 'Pil %20\'nin altına indiğinde Bluetooth\'u kapatır, parlaklığı en düşüğe alır.',
    rule_3_title: 'Kural oluşturucu',
    rule_3_tag: 'Arayüz',
    rule_3_desc: 'Tetikleyici, koşul ve sıralı eylemleri görsel oluşturucuda birleştirin.',
    action_steps: '{n} adım',
    action_none: 'Yok',

    caps_eyebrow: 'Kabiliyet haritası',
    caps_title: 'Tetikleyiciler ve eylemler, tek bakışta.',
    caps_desc: 'Android\'in yerel API sınırları içinde çalışır. Mavi işaretli eylemler Shizuku IPC gerektirir; kalanlar standart API\'lerle uygulanır.',
    caps_triggers: 'Tetikleyiciler',
    caps_actions: 'Ekran, güç ve ses',
    caps_privileged: 'Sistem ve donanım',
    caps_note: 'Shizuku IPC gerektirir',
    trig_time: 'Belirli saat / periyot',
    trig_geofence: 'Geofence',
    trig_bat_level: 'Pil seviyesi',
    trig_bat_state: 'Şarj bağlandı / kesildi',
    trig_wifi_state: 'Wi-Fi bağlandı / kesildi',
    trig_wifi_ssid: 'SSID eşleşmesi',
    trig_bt_state: 'Bluetooth cihazı',
    trig_nfc: 'NFC etiketi',
    act_bright_level: 'Parlaklık seviyesi',
    act_bright_auto: 'Otomatik parlaklık',
    act_vol_media: 'Medya / zil sesi',
    act_vol_ringer: 'Sessiz / titreşim',
    act_dnd: 'Rahatsız Etmeyin',
    act_notify: 'Sistem bildirimi',
    act_wifi_toggle: 'Wi-Fi aç / kapat',
    act_bt_toggle: 'Bluetooth aç / kapat',
    act_hotspot: 'Mobil veri / uçak modu / hotspot',
    act_launch_app: 'Uygulama başlat',
    act_volume_preset: 'Ses profili',
    act_webhook: 'Webhook',
    act_sms: 'SMS / telefon',

    how_eyebrow: 'Çalışma modeli',
    how_title: 'Motor yalnız gerektiğinde uyanır.',
    how_desc: 'Kural oluşturmak, dinlemek ve uygulamak üç ayrı sorumluluk. Etkin kural yoksa ilgili dinleyici de kapanır.',
    how_1_title: 'Kuralı yazın',
    how_1_desc: 'Tetikleyici, koşul ve sıralı eylemleri görsel oluşturucuda birleştirin; hazır şablonlardan içe aktarın.',
    how_2_title: 'Motor dinler',
    how_2_desc: 'Yalnız etkin kuralların ihtiyaç duyduğu alıcı ve dinleyiciler kayıtlı kalır; kural devre dışı kalınca bırakılır.',
    how_3_title: 'Eylem uygulanır',
    how_3_desc: 'Değerlendirme yerelde yapılır; ayrıcalıklı eylem allowlist üzerinden Shizuku IPC\'ye, diğerleri standart API\'ye gider.',

    gal_eyebrow: 'Arayüz',
    gal_title: 'Material 3 Expressive, Jetpack Compose.',
    gal_desc: 'Dinamik renk uyumu, yüksek bilgi yoğunluğu ve tek elle erişilebilir kontroller.',
    gal_1_title: 'Ana panel',
    gal_1_sub: 'Aktif kurallar ve servis durumu',
    gal_2_title: 'Hazır şablonlar',
    gal_2_sub: 'Doğrulanmış otomasyonlar',
    gal_3_title: 'Kural oluşturucu',
    gal_3_sub: 'Kategori bazlı yapılandırma',
    gal_4_title: 'Ayarlar',
    gal_4_sub: 'Yedekleme ve tercihler',
    gal_5_title: 'Hakkında',
    gal_5_sub: 'Sürüm, lisans ve yetkiler',

    cmp_eyebrow: 'Mimari fark',
    cmp_title: 'Neden FlowPilot?',
    cmp_desc: 'Eski otomasyon araçları karmaşık izinler, sürekli çalışan arka plan döngüleri ve kapalı kaynakla gelir. FlowPilot modern Android pratikleriyle tasarlandı.',
    cmp_caption: 'FlowPilot ile geleneksel otomasyon araçlarının karşılaştırması',
    cmp_th: 'Kriter',
    cmp_legacy: 'Geleneksel araçlar',
    cmp_r1: 'Arka plan tüketimi',
    cmp_r1_old: 'Sürekli foreground service ve wake-lock',
    cmp_r1_new: 'Olay tabanlı alıcılar, ihtiyaç odaklı dinleyiciler',
    cmp_r2: 'Ayrıcalıklı erişim',
    cmp_r2_old: 'Root veya karmaşık ADB betikleri',
    cmp_r2_new: 'Standart Shizuku IPC, root gerekmez',
    cmp_r3: 'Uygulama boyutu',
    cmp_r3_old: 'Uygulamaya ve yapılandırmaya göre değişir',
    cmp_r3_new: '2,6 MB release asset',
    cmp_r4: 'Gizlilik',
    cmp_r4_old: 'Crashlytics, analitik SDK\'ları, hesap zorunluluğu',
    cmp_r4_new: 'Sıfır telemetri; iletişim yalnız açıkça kullandığınız özelliklerle',
    cmp_r5: 'Kaynak kodu',
    cmp_r5_old: 'Kapalı kaynak / ücretli',
    cmp_r5_new: 'GPL-3.0, GitHub\'da tam kaynak',

    inst_eyebrow: 'Kurulum',
    inst_title: 'İki adımda çalışır durumda.',
    inst_desc: 'Root gerekmez. Shizuku\'yu Android 11+ Kablosuz Hata Ayıklama ile telefonda veya USB üzerinden başlatın.',
    step_1_title: 'APK\'yı yükleyin',
    step_1_desc: 'GitHub Releases\'ten <code>FlowPilot-v2.2.0.apk</code> dosyasını indirin ya da ADB ile doğrudan kurun.',
    step_2_title: 'Shizuku\'yu başlatın',
    step_2_desc: 'Geliştirici Seçenekleri\'nden <em>Kablosuz Hata Ayıklama</em> ile eşleyin; ya da cihazı USB ile bağlayıp terminalden başlatın.',
    callout_geo_tag: 'Geofence ve pil',
    callout_geo_title: 'Donanım tabanlı konum alanı',
    callout_geo_desc: 'Sürekli GPS taraması yerine Google Play Services geofence API\'si kullanılır. Arka planda giriş/çıkış algılamak için <strong>hassas konum</strong> ve <strong>arka planda her zaman izin</strong> gerekir.',
    callout_backup_tag: 'Yedek ve gizlilik',
    callout_backup_title: 'Şifreli tam yedek',
    callout_backup_desc: 'Normal JSON dışa aktarma webhook sırlarını çıkarır ve içe aktarılan kuralları devre dışı bırakır. Şifreli tam yedek tüm kural verisini AES-256-GCM ve parola ile taşır; geri yükleme etkinlik durumunu korur.',
    callout_oem_tag: 'HyperOS / MIUI',
    callout_oem_title: 'Cihaz optimizasyonu',
    callout_oem_desc: 'Ekran kapalıyken dakik çalışması için FlowPilot\'a <strong>otomatik başlatma</strong> iznini verin ve pil tasarrufunu <strong>kısıtlama yok</strong> yapın.',
    compat_min: 'Android 8.0+',
    compat_target: 'Android 16',
    compat_java: 'Derleme araç zinciri',
    compat_license: 'Açık kaynak lisans',

    faq_eyebrow: 'SSS',
    faq_title: 'Sık sorulan sorular.',
    faq_1_q: 'Root gerekiyor mu?',
    faq_1_a: 'Hayır. Ayrıcalıklı eylemler Shizuku IPC üzerinden çalışır; cihazı rootlamanız gerekmez.',
    faq_2_q: 'Shizuku neden gerekli?',
    faq_2_a: 'Wi-Fi, Bluetooth, mobil veri ve uçak modu gibi sistem anahtarları normal uygulama izinleriyle değiştirilemez. FlowPilot bu eylemleri allowlist üzerinden Shizuku\'ya iletir; parlaklık, ses, DND ve bildirim gibi eylemler standart API\'lerle çalışır.',
    faq_3_q: 'Verilerim nereye gidiyor?',
    faq_3_a: 'Kurallar cihazda saklanır. Telemetri, analitik veya hesap yoktur; dış iletişim yalnızca sizin yapılandırdığınız webhook, SMS ve paylaşım gibi özelliklerle gerçekleşir.',
    faq_4_q: 'Yedekleme nasıl çalışıyor?',
    faq_4_a: 'Normal JSON dışa aktarma webhook URL, header ve gövdelerini çıkarır; içe aktarılan kurallar siz etkinleştirene kadar kapalı kalır. Şifreli tam yedek tüm veriyi parola türevli AES-256-GCM ile taşır ve geri yüklemede etkinlik durumunu korur.',
    faq_5_q: 'Hangi Android sürümlerini destekliyor?',
    faq_5_a: 'Android 8.0 (API 26) ve üzeri. Derleme hedefi Android 16 (API 36). Kablosuz Hata Ayıklama ile Shizuku başlatma Android 11+ gerektirir.',
    faq_6_q: 'Pil tüketimi nasıl?',
    faq_6_a: 'Motor olay tabanlıdır: kalıcı wake-lock veya sürekli CPU döngüsü yoktur. Dinleyiciler yalnız etkin kuralların ihtiyaç duyduğu sürece kayıtlı kalır.',

    footer_desc: 'Olay tabanlı Android otomasyon motoru. Kotlin Coroutines, Jetpack Compose ve Shizuku IPC ile geliştirildi.',
    mobile_cta_note: 'v2.2.0 · 2,6 MB',
    screen_alt_home_screen: 'FlowPilot ana paneli',
    screen_alt_presets_screen: 'Hazır şablon kataloğu',
    screen_alt_create_screen: 'Kural oluşturucu',
    screen_alt_settings_screen: 'Ayarlar ve yedekleme',
    screen_alt_about_dialog: 'Hakkında penceresi'
  },
  en: {
    nav_studio: 'Studio',
    nav_caps: 'Capabilities',
    nav_how: 'How it works',
    nav_compare: 'Architecture',
    nav_install: 'Install',
    nav_faq: 'FAQ',
    skip_link: 'Skip to content',
    loading: 'Loading…',

    cta_download: 'Download APK',
    cta_download_apk: 'Download APK · 2.6 MB',
    cta_explore: 'Open the rule studio',
    cta_source: 'Source code',
    cta_title: 'Build your own automation.',
    cta_desc: '2.6 MB, no root, no telemetry. GPL-3.0, fully open source.',

    hero_eyebrow: 'Open source · No root · v2.2.0',
    hero_title: 'Run Android by <span class="accent">your own rules</span>.',
    hero_desc: 'An event-driven automation engine: only the listeners a rule needs stay registered, and privileged actions run through Shizuku IPC. No always-on wake-lock.',
    meta_android: 'Android 8.0 and up',
    meta_telemetry: 'telemetry',
    meta_lang: 'interface',
    copy: 'Copy',
    copy_adb: 'Copy the ADB command',
    copied: 'Copied',

    float_rule_title: 'Rule fired',
    float_rule_sub: 'Night routine',
    float_priv_title: 'Shizuku IPC',
    float_priv_sub: 'Wi-Fi turned off',

    stat_apk: 'Release APK size',
    stat_telemetry: 'Telemetry and analytics',
    stat_min: 'Minimum Android version',
    stat_lang: 'App language',

    studio_eyebrow: 'Interactive studio',
    studio_title: 'Pick a rule and watch it run.',
    studio_desc: 'Three presets shown with real app screenshots and a conceptual rule definition. The definitions are not the serialized export format.',
    pipeline_trigger: 'Trigger',
    pipeline_condition: 'Condition',
    pipeline_action: 'Actions',

    rule_0_title: 'Night routine',
    rule_0_tag: 'Time',
    rule_0_desc: 'At 23:00 turns off auto-brightness, sets brightness to 10% and enables Do Not Disturb.',
    rule_1_title: 'Leaving home',
    rule_1_tag: 'Network',
    rule_1_desc: 'When the home Wi-Fi disconnects, turns Wi-Fi off and re-enables auto-brightness.',
    rule_2_title: 'Battery guard',
    rule_2_tag: 'Power',
    rule_2_desc: 'Below 20% battery turns Bluetooth off and drops brightness to the lowest level.',
    rule_3_title: 'Rule builder',
    rule_3_tag: 'UI',
    rule_3_desc: 'Combine triggers, conditions and ordered actions in the visual builder.',
    action_steps: '{n} steps',
    action_none: 'None',

    caps_eyebrow: 'Capability map',
    caps_title: 'Triggers and actions at a glance.',
    caps_desc: 'Built inside Android\'s native API limits. Blue items require Shizuku IPC; everything else uses standard APIs.',
    caps_triggers: 'Triggers',
    caps_actions: 'Display, power and audio',
    caps_privileged: 'System and hardware',
    caps_note: 'Requires Shizuku IPC',
    trig_time: 'Time / interval',
    trig_geofence: 'Geofence',
    trig_bat_level: 'Battery level',
    trig_bat_state: 'Charger connected / disconnected',
    trig_wifi_state: 'Wi-Fi connected / disconnected',
    trig_wifi_ssid: 'SSID match',
    trig_bt_state: 'Bluetooth device',
    trig_nfc: 'NFC tag',
    act_bright_level: 'Brightness level',
    act_bright_auto: 'Auto-brightness',
    act_vol_media: 'Media / ringer volume',
    act_vol_ringer: 'Silent / vibrate',
    act_dnd: 'Do Not Disturb',
    act_notify: 'System notification',
    act_wifi_toggle: 'Wi-Fi on / off',
    act_bt_toggle: 'Bluetooth on / off',
    act_hotspot: 'Mobile data / airplane mode / hotspot',
    act_launch_app: 'Launch app',
    act_volume_preset: 'Sound profile',
    act_webhook: 'Webhook',
    act_sms: 'SMS / call',

    how_eyebrow: 'Execution model',
    how_title: 'The engine wakes only when needed.',
    how_desc: 'Writing a rule, listening and applying it are three separate responsibilities. With no enabled rules, the related listener is released.',
    how_1_title: 'Write the rule',
    how_1_desc: 'Combine triggers, conditions and ordered actions in the visual builder, or import a preset.',
    how_2_title: 'The engine listens',
    how_2_desc: 'Only receivers and listeners that enabled rules need stay registered; they are released when the rule is disabled.',
    how_3_title: 'Actions apply',
    how_3_desc: 'Evaluation happens locally; privileged actions go through an allowlist to Shizuku IPC, everything else uses standard APIs.',

    gal_eyebrow: 'Interface',
    gal_title: 'Material 3 Expressive, Jetpack Compose.',
    gal_desc: 'Dynamic color harmony, high information density and one-handed controls.',
    gal_1_title: 'Home panel',
    gal_1_sub: 'Active rules and service status',
    gal_2_title: 'Presets',
    gal_2_sub: 'Validated automations',
    gal_3_title: 'Rule builder',
    gal_3_sub: 'Category-based configuration',
    gal_4_title: 'Settings',
    gal_4_sub: 'Backups and preferences',
    gal_5_title: 'About',
    gal_5_sub: 'Version, license and privileges',

    cmp_eyebrow: 'Architecture',
    cmp_title: 'Why FlowPilot?',
    cmp_desc: 'Legacy automation apps ship with complex permissions, always-on background loops and closed source. FlowPilot is built on modern Android practices.',
    cmp_caption: 'Comparison of FlowPilot and traditional automation apps',
    cmp_th: 'Criterion',
    cmp_legacy: 'Traditional apps',
    cmp_r1: 'Background cost',
    cmp_r1_old: 'Always-on foreground service and wake-lock',
    cmp_r1_new: 'Event-driven receivers, on-demand listeners',
    cmp_r2: 'Privileged access',
    cmp_r2_old: 'Root or complex ADB scripts',
    cmp_r2_new: 'Standard Shizuku IPC, no root',
    cmp_r3: 'App size',
    cmp_r3_old: 'Varies by app and configuration',
    cmp_r3_new: '2.6 MB release asset',
    cmp_r4: 'Privacy',
    cmp_r4_old: 'Crashlytics, analytics SDKs, account required',
    cmp_r4_new: 'Zero telemetry; traffic only through features you explicitly use',
    cmp_r5: 'Source code',
    cmp_r5_old: 'Closed source / paid',
    cmp_r5_new: 'GPL-3.0, full source on GitHub',

    inst_eyebrow: 'Install',
    inst_title: 'Running in two steps.',
    inst_desc: 'No root required. Start Shizuku over Android 11+ Wireless Debugging on the phone, or through USB.',
    step_1_title: 'Install the APK',
    step_1_desc: 'Download <code>FlowPilot-v2.2.0.apk</code> from GitHub Releases, or install it over ADB.',
    step_2_title: 'Start Shizuku',
    step_2_desc: 'Pair it from Developer Options → <em>Wireless Debugging</em>, or connect the device over USB and start it from a terminal.',
    callout_geo_tag: 'Geofence and battery',
    callout_geo_title: 'Hardware-backed geofencing',
    callout_geo_desc: 'Instead of continuous GPS polling, the Google Play Services geofence API is used. Detecting enter/exit in the background requires <strong>precise location</strong> and <strong>allow all the time</strong>.',
    callout_backup_tag: 'Backup and privacy',
    callout_backup_title: 'Encrypted full backup',
    callout_backup_desc: 'A standard JSON export strips webhook secrets and imports rules disabled. The encrypted full backup carries all rule data with AES-256-GCM and a passphrase, and preserves enabled state on restore.',
    callout_oem_tag: 'HyperOS / MIUI',
    callout_oem_title: 'Device optimization',
    callout_oem_desc: 'For reliable execution while the screen is off, grant FlowPilot <strong>autostart</strong> and set battery saver to <strong>no restrictions</strong>.',
    compat_min: 'Android 8.0+',
    compat_target: 'Android 16',
    compat_java: 'Build toolchain',
    compat_license: 'Open source license',

    faq_eyebrow: 'FAQ',
    faq_title: 'Frequently asked questions.',
    faq_1_q: 'Does it need root?',
    faq_1_a: 'No. Privileged actions run through Shizuku IPC, so the device does not need to be rooted.',
    faq_2_q: 'Why does it need Shizuku?',
    faq_2_a: 'System switches such as Wi-Fi, Bluetooth, mobile data and airplane mode cannot be changed with normal app permissions. FlowPilot sends those actions to Shizuku through an allowlist; brightness, audio, DND and notifications use standard APIs.',
    faq_3_q: 'Where does my data go?',
    faq_3_a: 'Rules are stored on the device. There is no telemetry, analytics or account; outbound traffic only happens through features you configure, such as webhooks, SMS and sharing.',
    faq_4_q: 'How does backup work?',
    faq_4_a: 'A standard JSON export strips webhook URLs, headers and bodies, and imported rules stay disabled until you enable them. The encrypted full backup carries all data with passphrase-derived AES-256-GCM and preserves enabled state on restore.',
    faq_5_q: 'Which Android versions are supported?',
    faq_5_a: 'Android 8.0 (API 26) and later. Build target is Android 16 (API 36). Starting Shizuku over Wireless Debugging requires Android 11+.',
    faq_6_q: 'What about battery usage?',
    faq_6_a: 'The engine is event-driven: no persistent wake-locks and no continuous CPU loops. Listeners stay registered only while enabled rules need them.',

    footer_desc: 'Event-driven Android automation engine. Built with Kotlin Coroutines, Jetpack Compose and Shizuku IPC.',
    mobile_cta_note: 'v2.2.0 · 2.6 MB',
    screen_alt_home_screen: 'FlowPilot home panel',
    screen_alt_presets_screen: 'Preset catalog',
    screen_alt_create_screen: 'Rule builder',
    screen_alt_settings_screen: 'Settings and backup',
    screen_alt_about_dialog: 'About dialog'
  }
};

/* -- studio rules --------------------------------------------------- */
const RULES = [
  {
    screen: 'home_screen',
    file: 'flowpilot_rule_night_mode.json',
    spec: (t) => ({
      rule_id: 'rule_preset_night_mode',
      name: t('rule_0_title'),
      enabled: true,
      trigger: { type: 'TIME', target_time: '23:00', repeat_days: ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'] },
      conditions: [{ type: 'SCREEN_ON', expected: true }],
      actions: [
        { type: 'AUTO_BRIGHTNESS_OFF' },
        { type: 'SET_BRIGHTNESS', value: 10 },
        { type: 'SET_RINGER_MODE', mode: 'SILENT' },
        { type: 'SET_DND', enabled: true }
      ]
    }),
    pipeline: (t) => ['TIME · 23:00', 'SCREEN_ON', t('action_steps').replace('{n}', '4')]
  },
  {
    screen: 'presets_screen',
    file: 'flowpilot_rule_leaving_home.json',
    spec: (t) => ({
      rule_id: 'rule_preset_leave_home',
      name: t('rule_1_title'),
      enabled: true,
      trigger: { type: 'WIFI_DISCONNECTED', previous_ssid: 'Home_Mesh_5G' },
      conditions: [],
      actions: [
        { type: 'WIFI_OFF', privileged_via: 'SHIZUKU_IPC' },
        { type: 'AUTO_BRIGHTNESS_ON' }
      ]
    }),
    pipeline: (t) => ['WIFI_DISCONNECTED', t('action_none'), t('action_steps').replace('{n}', '2')]
  },
  {
    screen: 'settings_screen',
    file: 'flowpilot_rule_battery_saver.json',
    spec: (t) => ({
      rule_id: 'rule_preset_battery_saver',
      name: t('rule_2_title'),
      enabled: true,
      trigger: { type: 'BATTERY_LEVEL_DROPS_BELOW', percentage: 20 },
      conditions: [{ type: 'IS_CHARGING', expected: false }],
      actions: [
        { type: 'BLUETOOTH_OFF', privileged_via: 'SHIZUKU_IPC' },
        { type: 'AUTO_BRIGHTNESS_OFF' },
        { type: 'SET_BRIGHTNESS', value: 10 }
      ]
    }),
    pipeline: (t) => ['BATTERY < 20%', 'IS_CHARGING = false', t('action_steps').replace('{n}', '3')]
  },
  {
    screen: 'create_screen',
    file: 'flowpilot_flow_builder.kt',
    spec: (t) => ({
      rule_id: 'rule_user_office_wifi',
      name: t('rule_3_title'),
      enabled: true,
      trigger: { type: 'WIFI_CONNECTED', ssid: 'Office_Corp' },
      conditions: [{ type: 'TIME_BETWEEN', from: '09:00', to: '18:00' }],
      actions: [
        { type: 'SET_VOLUME', stream: 'RINGER', level: 0 },
        { type: 'SET_VIBRATION', enabled: true }
      ]
    }),
    pipeline: (t) => ['WIFI_CONNECTED', 'TIME_BETWEEN', t('action_steps').replace('{n}', '2')]
  }
];

/* -- helpers -------------------------------------------------------- */
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));
const reduceMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const t = (key) => (I18N[currentLang] && I18N[currentLang][key]) || (I18N.tr[key] ?? key);

let currentLang = 'tr';

/* -- language ------------------------------------------------------- */
function applyLanguage(lang, persist = true) {
  currentLang = LANGS.includes(lang) ? lang : 'tr';
  const dict = I18N[currentLang];

  document.documentElement.lang = currentLang;

  $$('[data-i18n]').forEach((node) => {
    const key = node.dataset.i18n;
    if (dict[key] !== undefined) node.textContent = dict[key];
  });

  $$('[data-i18n-html]').forEach((node) => {
    const key = node.dataset.i18nHtml;
    if (dict[key] !== undefined) node.innerHTML = dict[key];
  });

  $$('[data-i18n-aria]').forEach((node) => {
    const key = node.dataset.i18nAria;
    if (dict[key] !== undefined) node.setAttribute('aria-label', dict[key]);
  });

  $$('img[data-screen]').forEach((img) => {
    const screen = img.dataset.screen;
    img.src = `assets/screenshots/webp/${currentLang}/${screen}.webp`;
    const altKey = `screen_alt_${screen}`;
    if (dict[altKey] && img.getAttribute('alt') !== '') img.alt = dict[altKey];
  });

  $$('.lang-btn').forEach((btn) => {
    const active = btn.dataset.lang === currentLang;
    btn.classList.toggle('is-active', active);
    btn.setAttribute('aria-pressed', String(active));
  });

  renderRule(activeRule, { animate: false });
  updateStudioTabsOverflow();

  if (persist) {
    try {
      localStorage.setItem(STORAGE_KEY, currentLang);
    } catch (_) {
      /* storage unavailable */
    }
  }
}

/* -- studio --------------------------------------------------------- */
let activeRule = 0;
let autoplayTimer = null;
let autoplayPaused = false;
let studioVisible = false;
const studio = $('.studio');
const studioPanel = $('#studio-panel');

function escapeHtml(value) {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function highlightJson(json) {
  const escaped = escapeHtml(json);
  return escaped.replace(
    /(&quot;(?:(?!&quot;).)*&quot;)(\s*:)?|\b(true|false)\b|\b(\d+(?:\.\d+)?)\b/g,
    (match, str, colon, bool, num) => {
      if (str) {
        return `<span class="${colon ? 'tok-key' : 'tok-str'}">${str}</span>${colon || ''}`;
      }
      if (bool) return `<span class="tok-bool">${bool}</span>`;
      if (num) return `<span class="tok-num">${num}</span>`;
      return match;
    }
  );
}

function renderRule(index, { animate = true } = {}) {
  const rule = RULES[index];
  if (!rule) return;
  activeRule = index;

  $$('.studio-tab').forEach((tab, i) => {
    const selected = i === index;
    tab.classList.toggle('is-active', selected);
    tab.setAttribute('aria-selected', String(selected));
    tab.tabIndex = selected ? 0 : -1;
  });

  const image = $('#studio-screen');
  image.dataset.screen = rule.screen;
  image.src = `assets/screenshots/webp/${currentLang}/${rule.screen}.webp`;
  image.alt = t(`screen_alt_${rule.screen}`);

  $('#studio-title').textContent = t(`rule_${index}_title`);
  $('#studio-desc').textContent = t(`rule_${index}_desc`);
  $('#spec-file').textContent = rule.file;

  const [trigger, condition, action] = rule.pipeline(t);
  $('#pipe-trigger').textContent = trigger;
  $('#pipe-condition').textContent = condition;
  $('#pipe-action').textContent = action;

  const spec = JSON.stringify(rule.spec(t), null, 2);
  $('#spec-code').innerHTML = highlightJson(spec);

  if (studioPanel) studioPanel.setAttribute('aria-labelledby', `tab-${index}`);

  if (animate && !reduceMotion()) {
    $$('.pipeline-step').forEach((step, i) => {
      step.classList.toggle('is-active', i === 0);
      step.style.transitionDelay = `${i * 60}ms`;
    });
    setTimeout(() => {
      $$('.pipeline-step').forEach((step, i) => {
        if (i > 0) step.classList.add('is-active');
      });
    }, 240);
  } else {
    $$('.pipeline-step').forEach((step) => step.classList.add('is-active'));
  }

  restartAutoplayProgress();
}

function nextRule() {
  renderRule((activeRule + 1) % RULES.length);
}

function restartAutoplayProgress() {
  if (!studio || reduceMotion() || autoplayPaused || !studioVisible) return;
  const progress = $('#tab-progress');
  if (!progress) return;
  studio.classList.remove('is-playing');
  void progress.offsetWidth;
  progress.style.setProperty('--tab-duration', `${AUTOPLAY_MS}ms`);
  studio.classList.add('is-playing');
  clearTimeout(autoplayTimer);
  autoplayTimer = setTimeout(nextRule, AUTOPLAY_MS);
}

function pauseAutoplay() {
  if (!studio) return;
  autoplayPaused = true;
  clearTimeout(autoplayTimer);
  studio.classList.remove('is-playing');
}

function resumeAutoplay() {
  if (!studio) return;
  autoplayPaused = false;
  restartAutoplayProgress();
}

function updateStudioTabsOverflow() {
  const el = document.querySelector('.studio-tabs');
  if (!el) return;
  el.classList.toggle('has-overflow', el.scrollWidth > el.clientWidth + 1);
}

function initStudio() {
  const tabs = $$('.studio-tab');
  updateStudioTabsOverflow();
  window.addEventListener('resize', updateStudioTabsOverflow, { passive: true });
  tabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      pauseAutoplay();
      renderRule(Number(tab.dataset.rule));
    });
    tab.addEventListener('keydown', (event) => {
      const keys = { ArrowRight: 1, ArrowLeft: -1 };
      if (!(event.key in keys)) return;
      event.preventDefault();
      const next = (Number(tab.dataset.rule) + keys[event.key] + tabs.length) % tabs.length;
      tabs[next].focus();
      renderRule(next);
    });
  });

  if (!studio) return;
  studio.addEventListener('pointerenter', pauseAutoplay);
  studio.addEventListener('pointerleave', resumeAutoplay);
  studio.addEventListener('focusin', pauseAutoplay);
  studio.addEventListener('focusout', (event) => {
    if (!studio.contains(event.relatedTarget)) resumeAutoplay();
  });
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) pauseAutoplay();
    else resumeAutoplay();
  });

  if ('IntersectionObserver' in window) {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          studioVisible = entry.isIntersecting;
          if (studioVisible) resumeAutoplay();
          else pauseAutoplay();
        });
      },
      { threshold: 0.35 }
    );
    observer.observe(studio);
  } else {
    studioVisible = true;
  }

  renderRule(0, { animate: false });
  updateStudioTabsOverflow();
}

/* -- scroll chrome -------------------------------------------------- */
function initScrollChrome() {
  const header = $('#site-header');
  const progress = $('#scroll-progress');
  const mobileCta = $('#mobile-cta');
  let ticking = false;

  const update = () => {
    const y = window.scrollY;
    const max = document.documentElement.scrollHeight - window.innerHeight;
    header.classList.toggle('is-scrolled', y > 8);
    if (progress) progress.style.width = `${max > 0 ? Math.min(100, (y / max) * 100) : 0}%`;

    if (mobileCta) {
      const atBottom = max - y < 160;
      mobileCta.classList.toggle('is-visible', y > 560 && !atBottom);
    }
    ticking = false;
  };

  window.addEventListener(
    'scroll',
    () => {
      if (!ticking) {
        ticking = true;
        requestAnimationFrame(update);
      }
    },
    { passive: true }
  );
  update();
}

function initScrollSpy() {
  const links = $$('.nav-link');
  const sections = links
    .map((link) => {
      const id = link.getAttribute('href');
      return id ? $(id) : null;
    })
    .filter(Boolean);

  if (!sections.length) return;

  const setActive = (id) => {
    links.forEach((link) => {
      const current = link.getAttribute('href') === `#${id}`;
      link.classList.toggle('is-current', current);
      if (current) link.setAttribute('aria-current', 'true');
      else link.removeAttribute('aria-current');
    });
  };

  let ticking = false;
  const update = () => {
    ticking = false;
    const probe = window.scrollY + 140;
    let current = sections[0].id;
    sections.forEach((section) => {
      if (section.offsetTop <= probe) current = section.id;
    });
    if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 4) {
      current = sections[sections.length - 1].id;
    }
    setActive(current);
  };

  window.addEventListener(
    'scroll',
    () => {
      if (!ticking) {
        ticking = true;
        requestAnimationFrame(update);
      }
    },
    { passive: true }
  );
  update();
}

/* -- reveals -------------------------------------------------------- */
function initReveals() {
  const items = $$('.reveal');
  if (!items.length) return;

  if (reduceMotion() || !('IntersectionObserver' in window)) {
    items.forEach((item) => item.classList.add('is-in'));
    return;
  }

  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('is-in');
        observer.unobserve(entry.target);
      });
    },
    { rootMargin: '0px 0px -8% 0px', threshold: 0.12 }
  );

  items.forEach((item, i) => {
    item.style.setProperty('--reveal-delay', `${Math.min(i % 3, 2) * 70}ms`);
    observer.observe(item);
  });
}

/* -- terminal ------------------------------------------------------- */
function initTerminal() {
  const term = $('.term');
  const body = $('#term-body');
  if (!term || !body) return;

  $$('p', body).forEach((line, i) => {
    line.style.transitionDelay = `${i * 70}ms`;
  });

  if (reduceMotion() || !('IntersectionObserver' in window)) {
    term.classList.add('is-played');
    return;
  }

  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        term.classList.add('is-played');
        observer.disconnect();
      });
    },
    { threshold: 0.3 }
  );
  observer.observe(term);
}

/* -- gallery -------------------------------------------------------- */
function initGallery() {
  const track = $('#gallery-track');
  if (!track) return;
  const step = () => {
    const first = track.querySelector('.shot');
    return first ? first.getBoundingClientRect().width + 16 : 240;
  };
  $('#gallery-prev')?.addEventListener('click', () => track.scrollBy({ left: -step(), behavior: reduceMotion() ? 'auto' : 'smooth' }));
  $('#gallery-next')?.addEventListener('click', () => track.scrollBy({ left: step(), behavior: reduceMotion() ? 'auto' : 'smooth' }));
}

/* -- copy + toast --------------------------------------------------- */
let toastTimer = null;
function toast(message) {
  const el = $('#toast');
  if (!el) return;
  el.textContent = message;
  el.classList.add('is-visible');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('is-visible'), 1800);
}

function initCopy() {
  const button = $('#adb-copy');
  if (!button) return;
  button.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(ADB_COMMAND);
      toast(t('copied'));
    } catch (_) {
      const area = document.createElement('textarea');
      area.value = ADB_COMMAND;
      area.setAttribute('readonly', '');
      area.style.position = 'fixed';
      area.style.opacity = '0';
      document.body.appendChild(area);
      area.select();
      document.execCommand('copy');
      area.remove();
      toast(t('copied'));
    }
  });
}

/* -- mobile menu ---------------------------------------------------- */
function initMenu() {
  const button = $('#menu-btn');
  const nav = $('#primary-nav');
  if (!button || !nav) return;

  const close = () => {
    nav.classList.remove('is-open');
    button.setAttribute('aria-expanded', 'false');
  };

  button.addEventListener('click', () => {
    const open = nav.classList.toggle('is-open');
    button.setAttribute('aria-expanded', String(open));
  });

  nav.addEventListener('click', (event) => {
    if (event.target.closest('a')) close();
  });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') close();
  });

  window.addEventListener('resize', () => {
    if (window.innerWidth > 900) close();
  });
}

/* -- preloader ------------------------------------------------------ */
function initPreloader() {
  const el = $('#preloader');
  if (!el) return;

  document.body.classList.add('is-loading');
  const started = performance.now();
  const minimum = reduceMotion() ? 0 : 420;

  const finish = () => {
    const wait = Math.max(0, minimum - (performance.now() - started));
    setTimeout(() => {
      el.classList.add('is-done');
      document.body.classList.remove('is-loading');
      el.addEventListener('transitionend', () => el.remove(), { once: true });
      setTimeout(() => el.remove(), 700);
    }, wait);
  };

  const fonts = document.fonts ? document.fonts.ready.catch(() => {}) : Promise.resolve();
  const hero = document.querySelector('.phone-hero img');
  const heroImage = hero && hero.decode ? hero.decode().catch(() => {}) : Promise.resolve();
  const loaded = document.readyState === 'complete'
    ? Promise.resolve()
    : new Promise((resolve) => window.addEventListener('load', resolve, { once: true }));

  Promise.race([
    Promise.all([fonts, heroImage, loaded]),
    new Promise((resolve) => setTimeout(resolve, 3000))
  ]).then(finish);
}

/* -- faq ------------------------------------------------------------ */
function initFaq() {
  $$('.faq details').forEach((details) => {
    const summary = details.querySelector('summary');
    const body = details.querySelector('.faq-body');
    if (!summary || !body) return;

    summary.addEventListener('click', (event) => {
      event.preventDefault();

      if (!details.open) {
        details.open = true;
        return;
      }

      if (reduceMotion()) {
        details.open = false;
        return;
      }

      let finished = false;
      const finish = () => {
        if (finished) return;
        finished = true;
        details.open = false;
        details.classList.remove('is-closing');
        body.removeEventListener('transitionend', onEnd);
        clearTimeout(fallback);
      };
      const onEnd = (e) => {
        if (e.target === body) finish();
      };
      const fallback = setTimeout(finish, 420);

      details.classList.add('is-closing');
      body.addEventListener('transitionend', onEnd);
    });
  });
}

/* -- boot ----------------------------------------------------------- */
document.addEventListener('DOMContentLoaded', () => {
  let saved = null;
  try {
    saved = localStorage.getItem(STORAGE_KEY);
  } catch (_) {
    /* storage unavailable */
  }

  initPreloader();
  applyLanguage(LANGS.includes(saved) ? saved : 'tr', false);
  if (document.fonts) document.fonts.ready.then(updateStudioTabsOverflow).catch(() => {});

  $$('.lang-btn').forEach((btn) => {
    btn.addEventListener('click', () => applyLanguage(btn.dataset.lang));
  });

  initStudio();
  initScrollChrome();
  initScrollSpy();
  initReveals();
  initTerminal();
  initGallery();
  initCopy();
  initFaq();
  initMenu();

  const year = $('#year');
  if (year) year.textContent = String(new Date().getFullYear());
});
