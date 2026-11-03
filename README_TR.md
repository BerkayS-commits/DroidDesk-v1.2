# DroidDesk 1.7

Android 7.0 (API 24) ve üzeri. Derleme hedefi API 35.

## 1.7'de değişenler
- **Yerleşik widget'lar kaldırıldı** (saat+hava durumu, takvim, sistem durumu, günün sözü). Bunların yerine cihazınızdaki **uygulamaların gerçek Android widget'ları** eklenebiliyor (AppWidgetHost). Masaüstüne uzun basın / sağ tıklayın → *Widget ekle*; ya da Ayarlar → Widget'lar. Yüklü uygulamaların widget'ları aranabilir bir listede görünür; ilk eklemede Android bir kez izin sorabilir, ayarı olan widget'ların ayar ekranı açılır. Widget'a uzun basın / sağ tıklayın: boyutlandır, taşı, kaldır. Köşe tutamacıyla serbest boyutlandırma var.
- **Dar ekranlarda (telefon, dikey) widget'lar kısayolların üstünde bir şerit olarak görünür**; geniş ekranlarda sağ sütunda durur.
- Hava durumu kaldırıldığı için **INTERNET izni de kaldırıldı**: uygulama artık hiçbir ağ isteği yapmıyor.
- **Dil seçimi:** Ayarlar → Sistem → Dil. Varsayılan **sistem dilidir**; seçim yapmazsanız uygulama telefonun diline uyar. Sunulan diller: English, Türkçe, Deutsch, Français, Español, Русский, العربية (Android 13+ sistem ayarlarındaki "Uygulama dilleri" ekranında da görünür). Çevirisi olmayan bir metin İngilizce gösterilir. Yeni dil eklemek için `res/values-xx/strings.xml` oluşturup `LocaleHelper.languages` ve `res/xml/locales_config.xml` dosyalarına eklemek yeterli.
- **Serbest pencere:** "Pencerelerde aç" seçeneği artık yalnızca sınır vermek yerine pencere modunu da istiyor (cihaz serbest pencereyi destekliyorsa). Ayarlar → Görev çubuğu → **Serbest pencere denetimi** cihazın durumunu (sistem özelliği, geliştirici ayarları, API erişimi, harici ekran) gösterir ve neyin açılması gerektiğini söyler. Bir kez `adb shell pm grant com.droiddesk.launcher android.permission.WRITE_SECURE_SETTINGS` verirseniz iki geliştirici anahtarı uygulamadan açılabilir (yeniden başlatma gerekir).

### Serbest pencere hakkında dürüst not
Serbest pencere bir **sistem özelliğidir**; üçüncü taraf bir uygulama bunu yalnızca *isteyebilir*. Pencere modu API'si gizli (hidden) olduğundan Android'in yeni sürümlerinde uygulamalara kapalı olabilir; Xiaomi/HyperOS ise kayan pencereleri kendi kapalı özelliğiyle yönetir. Bu yüzden Xiaomi 15 / HyperOS 4 / Android 17'de çalışacağını **garanti edemiyorum** ve cihazda denemedim. Denetim ekranı tam olarak hangi koşulun eksik olduğunu gösterecektir.

## 1.6'dan beri geçerli olanlar
## Bildirim paneli (sağ alt)
- **Tek dokunuş = aç/kapat, uzun basma = o ayarın Android sayfası.** Kutular açıkken renkli, kapalıyken sönük görünür.
- Yeni kutular: NFC, Rahatsız etmeyin, El feneri, Otomatik döndür, Koyu mod, Sessiz, Ekran açık kalsın, Konum, Pil tasarrufu, Ekran görüntüsü, Ekranı kilitle (Wi-Fi, Bluetooth, Uçak modu, Ekran, Ses, Güç, Ayarlar ile birlikte toplam 18).
- **Cihaz bilgisi kartları:** pil (yüzde, şarj, sıcaklık), ağ, depolama, bellek, cihaz modeli.
- **Hangilerinin görüneceği** Ayarlar → Bildirimler sekmesinde tek tek seçilir (kutular ve bilgi kartları ayrı).

## Son uygulamalar
- **Uygulama önizlemesi:** Ayarlar → Görev çubuğu → "Son uygulamalarda uygulama önizlemesi". Öndeki uygulamanın küçük ekran görüntüsü alınır ve bloklarda gösterilir (Android 11+, erişilebilirlik hizmeti gerekir). Görüntüler yalnızca bellekte tutulur; kaydedilmez, gönderilmez. Varsayılan kapalıdır.

## Uygulamanın üstüne gelen görev çubuğu
- Artık ayrı bir şerit değil, **gerçek görev çubuğunun kendisi**: aynı düzen, Başlat düğmesi, arama, gezinme düğmeleri, sabitli ve etkin uygulamalar, ışıklar, tepsi simgeleri ve saat. Aynı koddan çiziliyor.
- **Geri getirme bölgesi** (Ayarlar → Fare ve tuşlar): kapalı / alt kenar / sağ alt köşe / sol alt köşe. Bir uygulama açıkken fareyi o bölgeye götürünce görev çubuğu yeniden görünür. Kısayol tuşu ve dışına dokununca kapanma aynen duruyor.

## İzinler
Yeni (hepsi isteğe bağlı): Wi-Fi/Bluetooth durumu ve değiştirme, NFC, Rahatsız etmeyin erişimi, Sistem ayarlarını değiştirme (otomatik döndürme için). İlk kullanımda ilgili sistem ekranı açılır.

## Sınırlamalar (dürüst liste)
- **Android üçüncü taraf uygulamaya bu anahtarları vermez:** Android 10+ sürümlerinde Wi-Fi, Bluetooth (Android 12+), Uçak modu, NFC ve **koyu mod** doğrudan açılıp kapatılamaz. Bu kutular **durumu gösterir**, dokununca ilgili sistem panelini açar (Wi-Fi ve NFC için hızlı panel, diğerleri için ayar sayfası). Android 9 ve altında Wi-Fi, Android 11 ve altında Bluetooth gerçekten açılıp kapanır. El feneri, Rahatsız etmeyin (izinle), Otomatik döndür (izinle), Sessiz, Ekran açık kalsın, Ekran görüntüsü ve Kilitle gerçekten çalışır. Ekran görüntüsü ve Kilitle Android 9+ ve erişilebilirlik hizmeti ister.
- **Önizleme** korumalı pencerelerde (bankacılık vb.) çalışmaz; Android 10 ve altında hiç kullanılamaz. Önizleme, uygulamayı en son önde gördüğümüz andaki görüntüdür, canlı içerik değildir.
- Daha önce belirtilen kısıtlar geçerlidir: son uygulamalar listesi Android'in gerçek görev listesi değildir, "Kapat" arka plan işlemlerini sonlandırır, pencere modu cihazın serbest pencere desteğine bağlıdır.
- Kaynak kod Android SDK ile **derlenip cihazda denenmemiştir** (1.7 değişiklikleri de); Android 16 ve 17'de davranış doğrulanamadı.
