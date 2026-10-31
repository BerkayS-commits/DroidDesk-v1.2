# DroidDesk 1.4 – Windows 11'den esinlenen Android launcher

1.4 sürümü, 1.3 üzerine gelen istekleri uygular. Kod modüllere bölündü: `MainActivity` (kabuk ve yönlendirme), `Taskbar`, `DesktopWidgets`, `NotificationCenter`, `SearchResults`, `SettingsPanel`, `FilesWindow`.

## Yeni ve değişen özellikler

**Bildirimler / sağ alt panel**
- Bildirimler artık yukarıdan çekilmez; sağ alttaki **bildirim merkezi** panelinde listelenir: dokununca açar, tek tek veya hepsini temizler, uygulamaya göre gruplar (daraltılabilir), önizlemeyi gizleyebilirsin.
- Panelde **Bildirimler** ve **Hızlı ayarlar** sekmeleri var. Başlıktaki iki düğme **DroidDesk ayarlarına** ve doğrudan **Android cihaz ayarlarına** gider.
- Özelleştirme: panel genişliği, varsayılan sekme, gruplama, önizleme, süregelen bildirimler, en fazla bildirim sayısı, hangi hızlı kutuların görüneceği ve kutu şekli (daire / yuvarlatılmış kare).
- Bildirim erişimi izni gerekir (yalnızca ekranda göstermek için kullanılır, saklanmaz/gönderilmez).

**Ayarlar (yeniden tasarlandı)**
- Launcher içinde tam ekran ayar paneli: Görünüm, Görev çubuğu, Uygulama çekmecesi, Widget'lar, Bildirimler, Fare ve tuşlar, Arama, Sistem sekmeleri. Anahtarlar, seçim çipleri, kaydırıcılar, renk örnekleri, duvar kâğıdı önizlemeleri ve simge şekli önizlemeleri.
- **Vurgu renkleri:** 13 hazır renk + ton/doygunluk/parlaklıkla özel renk.
- **Gömülü duvar kâğıtları:** 8 adet (Dağ gölü, Aurora, Okyanus, Çöl kumulları, Yıldızlı gece, Orman, Neon, Pastel). Kodla çizildiği için ek görsel dosyası gerekmez. İstersen kendi fotoğrafını da seçebilirsin.

**Simgeler**
- **Şekil:** Orijinal, daire, yuvarlatılmış kare, kare, **su damlası**, yaprak, altıgen.
- **Simge paketleri:** ADW/Nova/Go biçimindeki (appfilter) yüklü paketler listelenir ve seçilir.

**Görev çubuğu**
- Uygulama çekmecesi düğmesi artık Windows logosuna benzemez: nokta ızgarası, halka veya altıgen seçilebilir. Rengi vurgu rengini izler.
- **Etkin uygulamaların altında ışık:** parlak çubuk = şu an önde; kısa çubuk = yakın zamanda kullanıldı. Sabitleme aynen çalışır; sabitli olmayan ama etkin uygulamalar geçici olarak görünür.
- **Uzun bas ve sürükle:** görev çubuğundaki uygulamaları yana kaydırarak sırala. Hareket ettirmeden bırakırsan menüsü açılır.
- Simge konumu (sol/orta), arama düğmesini aç/kapat, son uygulamalar düğmesini aç/kapat, tepsi simgelerini tek tek aç/kapat.
- Pencerede aç seçeneği (cihaz destekliyorsa).

**Arama**
- Görev çubuğundaki arama düğmesi çekmeceyi aratmaya hazır açar. Sonuçlar: **uygulamalar, klasörler ve dosyalar**. Dosya/klasör araması için "Tüm dosyalara erişim" izni gerekir (ayarlardan verilir). Enter ilk sonucu açar.

**Uygulama çekmecesi**
- Konum: **sol / orta / sağ**. Ayrıca genişlik, yükseklik, sütun sayısı (otomatik/4/5/6) ve uygulama adlarını gösterme seçenekleri.

**Widget'lar**
- Her widget'ı **serbestçe boyutlandır** (köşe tutamacını sürükle) veya Küçük/Orta/Büyük hazır boyut seç; yukarı/aşağı taşı.
- **Varsayılan widget'ları kaldırma:** tek tek kaldır, "Tüm widget'ları kaldır" veya "Varsayılanları geri yükle".

**Fare ve dokunmatik**
- Sağ tık menüleri: masaüstü, uygulama simgeleri, görev çubuğu, Başlat düğmesi (hızlı bağlantılar), tepsi, widget'lar. Fareyle üzerine gelince vurgu ve ipucu görünür. Dokunmatikte uzun basma aynı menüleri açar.
- **Seçilebilir davranışlar:** simgede sağ tık (menü / aç / uygulama bilgisi), masaüstünde sağ tık (menü / hiçbir şey), simgeleri açma (tek / çift tıklama), boş masaüstünde çift tıklama (hiçbir şey / çekmece / menü / bildirimler).

**Pencere modu tuşu**
- Fare ve tuşlar sekmesinde bir tuş kombinasyonu **kaydedebilir** veya hazır listeden seçebilirsin. Tuşa basınca öndeki uygulama Ana ekran üzerinden pencere modunda yeniden açılır (erişilebilirlik hizmeti gerekir; aşağıdaki sınırlamaya bak).

## İsteğe bağlı izinler (hepsi varsayılan kapalı, sistem ayarlarından verilir)
- **Erişilebilirlik hizmeti:** Alt+Tab, Win tuşu, güç menüsü, pencere modu tuşu. Ekran içeriğini okumaz.
- **Bildirim erişimi:** bildirim merkezi.
- **Kullanım erişimi:** etkin uygulama ışığı için gerçek ön plan/yakın zamanlı kullanım bilgisi.
- **Tüm dosyalara erişim:** dosya ve klasör araması.
- **İnternet:** yalnızca hava durumu için; şehir adı girmezsen hiçbir ağ isteği yapılmaz.

## GitHub Actions ile APK derleme
1. ZIP'i açın ve **içindekilerin tümünü** depo köküne yükleyin (`.github` dahil).
2. Actions → `Build DroidDesk APK` iş akışını çalıştırın.
3. `DroidDesk-debug-apk` artifact'ini indirip kurun, ana ekran olarak DroidDesk'i seçin.

## Sınırlamalar (dürüst liste)
- Kaynak kod Android SDK ile **derlenip cihazda denenmemiştir**; ilk Actions derlemesi doğrulamadır.
- **Pencere modu tuşu** Android'in izin verdiği kadar çalışır: DroidDesk başka uygulamanın penceresini doğrudan küçültemez. Bunun yerine uygulamayı pencere sınırlarıyla yeniden açar. Cihazda **serbest pencere (freeform) desteği açık olmalı** (Geliştirici seçenekleri → "Serbest biçimli pencereleri etkinleştir"; ayarlarda kısayol var). Desteklemeyen cihazda uygulama tam ekran açılır ve bir uyarı gösterilir. Bazı uygulamalar yeniden boyutlandırmayı reddeder.
- **Işık:** Android çalışan uygulamaları listelemeye izin vermez. Kullanım erişimiyle "önde / son 30 dk / son 2 saat" bilgisi alınır; tam olarak "arka planda çalışıyor" bilgisi yoktur. İzin verilmezse yalnızca DroidDesk'ten açılan uygulamalar ışık alır.
- **Dosya araması** harici depolamadaki dosya ve klasör adlarına bakar (içeriğe bakmaz); ilk kullanımda dizin oluşturulurken kısa süre beklenebilir. `Android/` klasörü hariçtir.
- **Simge paketleri** yalnızca yaygın "appfilter" biçimini destekler; pakette karşılığı olmayan uygulamalar seçili şekille çizilir.
- Alt+Tab sistemin Son Uygulamalar ekranını açar. Widget'lar telefon dikey modunda (genişlik < 720 dp) gizlenir.
- Hava durumu simgeleri emojidir. Taslaktaki Geri Dönüşüm Kutusu / Yeni klasör yoktur. Dosyalar penceresi kısayolları sistem uygulamalarını açar.
- Wi-Fi/Bluetooth/Uçak modu kutuları sistem panelini veya ayarını açar; doğrudan aç/kapat yapmaz. Parlaklık yalnızca DroidDesk penceresini etkiler.
