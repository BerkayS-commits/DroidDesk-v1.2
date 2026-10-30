# DroidDesk 1.3 – Windows 11'den esinlenen Android launcher

1.3 sürümü, paylaşılan tasarım taslağına göre yeniden düzenlendi.

## Taslağa göre yapılanlar
- **Masaüstü:** Dosyalar, Chrome, YouTube, Play Store simgeleri sol sütunda (dikey akış, ilk açılışta yüklüyse otomatik eklenir). Simge boyutu: Görünüm → Küçük/Orta/Büyük.
- **Widget'lar (sağ sütun, ekran ≥ 720 dp):** Saat + hava durumu, takvim (ay gezinme), sistem durumu halkaları (pil, depolama, RAM), günün sözü. Sağ tık menüsünden açılıp kapatılır.
- **Masaüstü bağlam menüsü** (boş masaüstüne uzun bas veya fare sağ tık): Duvar kâğıdı değiştir, Görünüm ›, Sırala ›, Yenile, Widget ekle ›, Uygulama çekmecesini aç, Dosyalar penceresi, Ayarlar.
- **Görev çubuğu:** ortalanmış Başlat, Ara, sabitlenmiş/son uygulamalar, Son Uygulamalar düğmesi; sağda tepsi (▲, Wi-Fi, Bluetooth, ses, bildirim zili, pil, saat). Bağlıysa Wi-Fi/Bluetooth dairesi vurgulanır.
- **Hızlı ayarlar:** renkli yuvarlak simgeler (Wi-Fi, Bluetooth, Uçak modu, Ekran, Ses, Pil, Güç, Ayarlar), simgeli ses ve parlaklık kaydırıcıları (ses simgesine dokunmak sessize alır).
- **Uygulama çekmecesi:** arama kutusu, sayfalı 4+ sütunlu ızgara, sayfa noktaları. Enter ilk sonucu açar.
- **Pencere yönetimi:** launcher içinde yüzen "Dosyalar" penceresi; başlıktan sürükle, küçült, büyüt/geri al, kapat, sağ alttan boyutlandır.
- **Ek özellikler:** vurgu rengi, duvar kâğıdı, cam efekti (Android 12+ gerçek bulanıklık), görev çubuğu boyutu, Alt+Tab, takvim ve sistem araçları.

## 1.2'den devralınanlar
Android 15 edge-to-edge düzeltmeleri, arka planda uygulama yükleme, serbest pencere yeniden boyutlandırma, TR/EN dil desteği, kurulum/kaldırma algılama, bağlam menüsünde sabitle/taşı/uygulama bilgisi/kaldır/harici ekranda aç.

## İsteğe bağlı izinler (varsayılan kapalı)
- **Erişilebilirlik hizmeti:** Alt+Tab, Win tuşu, güç menüsü, bildirim çekmecesi. Ekran içeriğini okumaz.
- **Bildirim erişimi:** zil simgesi ve sayı için; yalnızca sayı kullanılır.
- **İnternet:** yalnızca hava durumu için. Şehir adını girerseniz Open-Meteo'ya gönderilir; boş bırakırsanız hiçbir ağ isteği yapılmaz.

## GitHub Actions ile APK derleme
1. ZIP'i açın ve **içindekilerin tümünü** depo köküne yükleyin (`.github` dahil).
2. Actions → `Build DroidDesk APK` iş akışını çalıştırın.
3. `DroidDesk-debug-apk` artifact'ini indirip kurun, ana ekran olarak DroidDesk'i seçin.

## Sınırlamalar (dürüst liste)
- Kaynak kod Android SDK ile derlenip cihazda **denenmemiştir**; ilk Actions derlemesi doğrulama adımıdır.
- Taslaktaki "Geri Dönüşüm Kutusu" ve "Yeni klasör" yoktur (Android'de karşılığı yok / klasör özelliği henüz yok).
- Diğer uygulamaların pencereleri taşınamaz veya boyutlandırılamaz; bu yalnızca launcher içindeki Dosyalar penceresi için geçerlidir. Başka uygulamalar için `setLaunchBounds` yalnızca cihaz serbest pencere modunu destekliyorsa uygulanır.
- Dosyalar penceresindeki kısayollar sistemin Belgeler, İndirilenler, Galeri ve Müzik uygulamalarını açar; kendi dosya yöneticisi değildir.
- Alt+Tab sistemin Son Uygulamalar ekranını açar. Widget'lar telefon dikey modunda (genişlik < 720 dp) gizlenir.
- Hava durumu simgeleri emoji, widget'lar sürüklenip yeniden konumlandırılamaz.
- Wi-Fi/Bluetooth/Uçak modu kutuları sistem panelini veya ayarını açar; doğrudan aç/kapat yapmaz. Parlaklık yalnızca DroidDesk penceresini etkiler.
