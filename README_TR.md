# DroidDesk 1.5 – klavye ve fareyle kullanılan masaüstü hissi veren Android launcher

Desteklenen Android sürümleri: **7.0 (API 24) ve üzeri**. Derleme hedefi Android 15 (API 35); daha yeni sürümlerde yalnızca herkese açık API'ler kullanılır ve hedef sürüm 35 olduğu için sistem uyumluluk davranışı uygular. Android 16 ve sonrası bu ortamda denenemedi (aşağıya bak).

## 1.5'te yeni / düzeltilen

**Düzeltmeler**
- **Sağ tık / uzun basma menüsü artık çekmecenin önünde.** Sorun, çekmece kartının yüksekliğinin (elevation) menüden fazla olmasıydı; menü en üst katmana alındı.
- **Simge şekli masaüstünü de kapsıyor:** Şekil ve simge paketi masaüstü, görev çubuğu, çekmece, son uygulamalar ve bildirim paneli için aynı kaynaktan geliyor. Tek bir simgenin işlenememesi artık listeyi boşaltmıyor (o simge orijinal çiziliyor).

**Tuş seçim ekranı (Ayarlar → Fare ve tuşlar)**
- Basılan kombinasyonu canlı gösteren ve hazır kısayol sunan bir ekran. İki bağlama kullanılır:
  1. **Görev çubuğunu açık uygulamanın üzerine getiren tuş.** Tuşa basınca görev çubuğu o an açık olan uygulamanın üstünde belirir (Başlat, sabitli ve etkin uygulamalar, son uygulamalar, saat, isteğe bağlı gezinme düğmeleri); dışına dokununca kapanır. İsteğe bağlı olarak **fareyi ekranın alt kenarına götürünce** de açılır.
  2. Öndeki uygulamayı pencere modunda yeniden açan tuş.
- Üst katman, erişilebilirlik hizmeti ile çizilir (ayrıca "üstte görüntüle" izni gerekmez).

**Uygulama çekmecesi**
- **Yukarıdan aşağıya kaydırma** (varsayılan; fare tekerleği çalışır) veya sayfalı mod, ayarlardan seçilir.
- **8 farklı düğme simgesi:** nokta, halka, altıgen, kareler, yıldız, şimşek, çiçek, elmas.

**Masaüstü hissi**
- Masaüstü, çekmece, görev çubuğu ve son uygulamalar ekranında fareyle üzerinden geçerken simgeler büyür; el imleci görünür; klavyeyle gezerken odaklanan öğe çerçevelenir.
- **Klavye kısayolları:** Win = çekmece, Win+D masaüstü, Win+E dosyalar, Win+S arama, Win+A bildirimler, Win+I ayarlar, Win+Tab / Alt+Tab son uygulamalar, Ctrl+Esc çekmece, Esc paneller, ok tuşları + Enter simgeler arasında gezinme ve açma, Delete masaüstü kısayolunu kaldırma. (Başka uygulamadayken çalışması için erişilebilirlik hizmeti gerekir.)

**Son uygulamalar (yeniden tasarlandı)**
- Düğme ve Alt+Tab artık **kare bloklar halinde** bir geçiş ekranı açar: her blokta uygulama simgesi ve adı, tek tek kapatma düğmesi; altta **"Tümünü kapat"**. Klavye: Tab/oklar seç, Enter aç, Delete kapat, Esc çık; Alt tuşunu bırakınca seçili uygulama açılır.
- İstersen düğmeyi sistemin kendi son uygulamalar ekranına geri çevirebilirsin (Görev çubuğu ayarları).

**Sistem gezinmesi**
- Görev çubuğuna **Geri, Ana ekran, Son uygulamalar** düğmeleri eklenebilir. Ayarlardan veya görev çubuğuna sağ tıklayarak açılıp kapanır.

## İsteğe bağlı izinler (varsayılan kapalı)
- **Erişilebilirlik hizmeti:** Win/Alt+Tab kısayolları, açık uygulamanın üstünde görev çubuğu ve geçiş ekranı, Geri düğmesi, güç menüsü, pencere modu tuşu. Ekran içeriğini okumaz.
- **Bildirim erişimi**, **Kullanım erişimi** (etkin uygulama ışığı ve son uygulamalar sırası), **Tüm dosyalara erişim** (dosya araması), **İnternet** (yalnızca hava durumu).
- **Arka plan işlemlerini sonlandırma** (normal izin): "Tümünü kapat" için.

## GitHub Actions ile APK derleme
1. ZIP'i açın ve **içindekilerin tümünü** depo köküne yükleyin (`.github` dahil).
2. Actions → `Build DroidDesk APK` iş akışını çalıştırın.
3. `DroidDesk-debug-apk` artifact'ini indirip kurun, ana ekran olarak DroidDesk'i seçin.

## Sınırlamalar (dürüst liste)
- Kaynak kod Android SDK ile **derlenip cihazda denenmemiştir**; ilk Actions derlemesi doğrulamadır. Android 7–15 için API sürümü korumaları eklendi; **Android 16 ve 17'de davranış doğrulanamadı**.
- **"Kapat" ne yapar:** Android, başlatıcılara başka uygulamayı kapatma yetkisi vermez. "Kapat" ve "Tümünü kapat", uygulamanın **arka plan işlemlerini sonlandırır** ve listeden çıkarır. Ön plandaki uygulama kapanmaz. Bazı üreticiler bunu kısıtlar.
- **Son uygulamalar listesi:** Android uygulamalara gerçek son görev listesini vermez. Liste, "Kullanım erişimi" verilirse kullanım olaylarından, verilmezse DroidDesk'ten açılan uygulamalardan oluşur. Önizleme (ekran görüntüsü) gösterilemez; bloklarda simge ve ad vardır.
- **Açık uygulamanın üstündeki görev çubuğu** erişilebilirlik hizmeti ister. Oradan uygulama başlatma bazı Android sürümlerinde arka plan başlatma kısıtına takılabilir; başlatılamazsa uyarı görünür. Başlat düğmesi Ana ekrana dönüp çekmeceyi açar.
- **Pencere modu tuşu** uygulamayı pencere sınırlarıyla yeniden açar ve cihazda **serbest pencere desteği** gerektirir (Geliştirici seçenekleri). Desteklemeyen cihazda tam ekran açılır.
- Işık, dosya araması, simge paketi, hava durumu ve diğer önceki sınırlamalar 1.4'teki gibidir. Dosya/klasör araması harici depolamada ad eşleştirir; içeriğe bakmaz.
- Wi-Fi/Bluetooth/Uçak modu kutuları sistem panelini veya ayarını açar; doğrudan aç/kapat yapmaz.
