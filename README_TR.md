# DroidDesk 1.2 – Windows 11'den esinlenen Android launcher

DroidDesk, Android için Windows 11 tarzı bir ana ekran (launcher) uygulamasıdır.
Bu sürüm (1.2) 1.1'e göre hata düzeltmeleri, performans iyileştirmeleri ve yeni özellikler içerir.

## 1.1 → 1.2 değişiklikleri

### Düzeltilen hatalar
- Hızlı ayarlar panelinin ayar değiştirdikten sonra iki kez dokunma istemesi düzeltildi (`quickOpen` durumu yeniden çizimde sıfırlanıyor).
- Android 15 (targetSdk 35) edge-to-edge: görev çubuğu artık sistem çubuklarının altında kalmıyor (WindowInsets, çentik ve klavye dahil).
- Saat artık canlı güncelleniyor (15 sn'de bir).
- Pencere boyutu / döndürme / serbest pencere değişiminde çekmece ve panel yeniden boyutlanıyor.
- Duvar kâğıdı artık örnekleme ile (`inSampleSize`) ve arka planda yükleniyor; büyük fotoğraflarda bellek taşması riski ve akış sızıntısı giderildi.
- "Dosyalar" ve "Güç" karoları gerçek işlevlerine bağlandı.
- Ana ekranda Geri tuşu artık launcher'ı kapatmıyor (sadece açık paneli kapatır).
- Çekmece yeniden boyutlandırma tutamacı 24 dp dokunma alanına büyütüldü; tutamaç ve ayar aralıkları (%30–82) tutarlı.
- Sürüm adı 1.2.0 olarak güncellendi.

### Performans
- Uygulama listesi, etiketler ve simgeler arka plan iş parçacığında bir kez yüklenip önbelleğe alınıyor.
- Çekmece `RecyclerView` ile çiziliyor; her harf girişinde tüm simgeler yeniden yüklenmiyor.
- Uygulama kurulum/kaldırma olayları dinleniyor, liste otomatik yenileniyor.

### Mimari
- Tek dosya yerine bölündü: `MainActivity`, `Prefs`, `AppRepository`, `Theme`, `Ui`, `GlassBackdrop`, servisler.
- Tüm metinler `strings.xml` içinde: İngilizce (varsayılan) ve Türkçe (`values-tr`).
- `onActivityResult` yerine Activity Result API; geri tuşu için `OnBackPressedCallback`.
- Eski tercihler (kısayollar, vurgu rengi) otomatik taşınır.

### Yeni özellikler
- **Bağlam menüsü** (uzun bas veya fare sağ tık): Aç, harici ekranda aç, masaüstüne ekle/kaldır, görev çubuğuna sabitle, sola/sağa taşı, uygulama bilgisi, kaldır. Fare üstüne gelince vurgu efekti.
- **Başlat menüsü**: Sabitlenmiş ve Son kullanılanlar şeritleri, tüm uygulamalar; Enter ilk sonucu açar; fiziksel klavyede açılınca doğrudan yazılabilir.
- **Görev çubuğu**: sabitlenmiş uygulamalar + son kullanılanlar (altında vurgu çizgisi), sistem tepsisi (ağ, pil/şarj, bildirim sayısı, saat).
- **Gerçek cam efekti** (Android 12+): panellerin arkasında arka planın bulanık kopyası. Eski sürümlerde yarı saydam panel.
- **Satır içi sistem panelleri** (Android 10+): İnternet ve Ses panelleri Ayarlar'a çıkmadan açılır.
- **Harici ekran**: bağlı ikinci ekran varsa menüde "Harici ekranda aç".
- **Klavye**: Win tuşu Başlat menüsünü açar, Esc kapatır, Alt+Tab (erişilebilirlik hizmetiyle, aşağıya bak).
- Uygulama ikonu ve çok dilli arayüz.

## İsteğe bağlı izinler (varsayılan kapalı)
- **Erişilebilirlik hizmeti** (Ayarlar → DroidDesk → Klavye kısayolları): Alt+Tab (sistem Son Uygulamalar), Win tuşu (herhangi bir uygulamadayken), güç menüsü ve bildirim çekmecesi için. Ekran içeriğini okumaz (`canRetrieveWindowContent=false`), yalnızca donanım tuş olaylarını ve genel eylemleri kullanır.
- **Bildirim erişimi**: tepsideki bildirim sayısı için. Yalnızca sayı kullanılır.
- `REQUEST_DELETE_PACKAGES`: "Kaldır" menü öğesi için. `ACCESS_NETWORK_STATE`: tepsideki ağ durumu için.

## GitHub Actions ile APK derleme
1. ZIP'i açın ve **içindekilerin tümünü** depo köküne yükleyin (`.github` klasörü dahil; ZIP'i tek dosya olarak yüklemeyin).
2. Actions sekmesinde `Build DroidDesk APK` iş akışını çalıştırın (veya `main`'e push edin).
3. Başarılı çalıştırmanın `DroidDesk-debug-apk` artifact'ini indirip kurun.
4. Ayarlar → Uygulamalar → Varsayılan uygulamalar → Ana ekran uygulaması'ndan DroidDesk'i seçin.

## Sınırlamalar
- Bu kaynak kod yazıldığı ortamda Android SDK ile derlenip cihazda denenmemiştir. GitHub Actions'taki ilk derleme doğrulama adımıdır; derleme hatası olursa Actions günlüğünü paylaşın.
- Standart bir launcher diğer uygulamaların pencerelerini kendi içine gömemez veya taşıyamaz. Pencere sınırları (`setLaunchBounds`) yalnızca cihaz serbest pencere modunu destekliyorsa uygulanır.
- Alt+Tab, Android'in Son Uygulamalar ekranını açar; Windows gibi canlı pencere geçişi değildir.
- Görev çubuğunda "çalışıyor" göstergesi yoktur (bunun için Kullanım Erişimi izni gerekir); alttaki çizgi "yakın zamanda açıldı" anlamına gelir.
- Masaüstü simgeleri serbest sürüklenmez; sıra, menüdeki "Sola/Sağa taşı" ile değiştirilir. Klasörler henüz yoktur.
- Wi-Fi/Bluetooth gibi kontroller sistem panellerini/ayarlarını açar; doğrudan aç/kapat izni her cihazda yoktur. Parlaklık kaydırıcısı yalnızca DroidDesk penceresini etkiler.

---

## English summary
DroidDesk 1.2 is a Windows 11-inspired Android launcher. Changes over 1.1: fixes (quick-panel double tap, Android 15 edge-to-edge insets, live clock, resize handling, wallpaper OOM), faster app loading (background load + RecyclerView), split architecture, TR/EN strings, context menus (long-press / right-click), Start menu with Pinned/Recent, taskbar pins, tray (network/battery/notifications/clock), real backdrop blur on Android 12+, Internet/Volume panels, external-display launch, Win/Esc/Alt+Tab keys (optional accessibility service). Build: upload all files to a GitHub repo root and run the `Build DroidDesk APK` workflow. Not compiled in the authoring environment; the first Actions run is the verification step.
