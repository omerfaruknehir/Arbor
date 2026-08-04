---
layout: default
title: Data Deletion / Veri Silme
---

# Xylune data deletion

Xylune has no central user account and no Xylune-controlled backend containing user chats or backups. Most data is stored on the Android device or in a provider selected by the user. The developer normally cannot access or delete that data remotely.

## Delete local data

- Delete individual chats, memories, providers, drafts, or other records from the relevant Xylune screen.
- To remove all local Xylune data, use Android **Settings → Apps → Xylune → Storage → Clear data**, or uninstall Xylune.
- Clearing data or uninstalling also removes locally stored encrypted OAuth sessions and cloud credentials.

Because local data is not copied to a developer-controlled server, sending a deletion request to the developer does not delete data from the device. The user must use the controls above.

## Delete cloud backups

Open **Xylune → Settings → Backup & transfer**, select the connected destination, browse backups, and choose **Delete**. A backup can also be deleted directly through Google Drive app-data controls, OneDrive Apps/Xylune, Dropbox Apps/Xylune, the configured WebDAV/Nextcloud folder, or the configured S3 bucket/prefix.

Disconnecting a provider removes the local session or credentials but does **not** automatically delete backups already stored there. Revoking Xylune in the Google, Microsoft, or Dropbox account security page stops future access but likewise does not necessarily delete stored files.

The selected provider controls its own copies, retention, account records, and deletion process. The developer cannot delete provider-held data without access granted by the user through the app.

## Developer-held correspondence

A user may separately ask for deletion of personal information intentionally sent to the developer in a private support message, security report, or similar correspondence, subject to applicable law and any information that must be retained for security, dispute resolution, or legal compliance. Public GitHub content is also handled by GitHub and may remain in repository history.

A privacy inquiry may be initiated through the [Xylune issue tracker](https://github.com/omerfaruknehir/Xylune/issues) without posting personal or secret information. Request a private channel if needed. General support has no guaranteed response time and may receive no response; mandatory legal deadlines remain unaffected where they apply.

---

# Xylune veri silme

Xylune merkezi bir kullanıcı hesabı veya kullanıcı sohbetlerini ve yedeklerini içeren Xylune kontrollü bir sunucu işletmez. Verilerin çoğu Android cihazda veya kullanıcının seçtiği sağlayıcıda tutulur. Geliştirici normal olarak bu verilere uzaktan erişemez veya bunları uzaktan silemez.

## Yerel verileri silme

- Tekil sohbet, anı, sağlayıcı, taslak veya diğer kayıtları ilgili Xylune ekranından silin.
- Tüm yerel verileri kaldırmak için Android'de **Ayarlar → Uygulamalar → Xylune → Depolama → Veriyi temizle** yolunu kullanın veya uygulamayı kaldırın.
- Uygulama verilerini temizlemek veya uygulamayı kaldırmak, cihazdaki şifreli OAuth oturumlarını ve bulut kimlik bilgilerini de kaldırır.

Yerel veriler geliştirici kontrollü bir sunucuya kopyalanmadığından geliştiriciye silme talebi göndermek cihazdaki verileri silmez. Kullanıcının yukarıdaki cihaz kontrollerini kullanması gerekir.

## Bulut yedeklerini silme

Bulut yedeklerini silmek için **Xylune → Ayarlar → Yedekleme ve aktarım** bölümünde hedefi açın, yedeği seçin ve **Sil** komutunu kullanın. Yedekler ayrıca Google Drive uygulama verisi denetimleri, OneDrive Apps/Xylune, Dropbox Apps/Xylune, yapılandırılan WebDAV/Nextcloud klasörü veya S3 bucket/prefix üzerinden silinebilir.

Sağlayıcı bağlantısını kesmek yalnızca cihazdaki oturumu veya kimlik bilgisini siler; sağlayıcıdaki mevcut yedekleri otomatik silmez. Google, Microsoft veya Dropbox hesap güvenliği sayfasından Xylune erişimini iptal etmek gelecekteki erişimi durdurur; mevcut dosyaları ayrıca silmek gerekebilir.

Seçilen sağlayıcı kendi kopyalarını, saklama uygulamalarını, hesap kayıtlarını ve silme sürecini yönetir. Kullanıcı uygulama üzerinden erişim vermedikçe geliştirici sağlayıcıdaki verileri silemez.

## Geliştiricinin elindeki yazışmalar

Kullanıcı, özel destek mesajı, güvenlik bildirimi veya benzeri bir yazışma ile geliştiriciye bilerek gönderdiği kişisel bilgilerin silinmesini ayrıca talep edebilir. Güvenlik, uyuşmazlık çözümü veya hukuki yükümlülükler için tutulması gereken bilgiler bakımından uygulanabilir mevzuat geçerlidir. Herkese açık GitHub içeriği GitHub tarafından da işlenir ve depo geçmişinde kalabilir.

Gizlilikla ilgili bir talep, kişisel veya gizli bilgi paylaşılmadan [Xylune issue sayfası](https://github.com/omerfaruknehir/Xylune/issues) üzerinden başlatılabilir. Gerekiyorsa özel iletişim kanalı talep edin. Genel destek için cevap süresi garantisi yoktur ve hiç cevap verilmeyebilir; uygulanabildiği yerde emredici yasal süreler saklıdır.
