---
layout: default
title: Data Deletion / Veri Silme
---

# Xylune data deletion

Xylune has no central user account. Most data is stored on the Android device or in a provider selected by the user.

## Delete local data

- Delete individual chats, memories, providers, drafts, or other records from the relevant Xylune screen.
- To remove all local Xylune data, use Android **Settings → Apps → Xylune → Storage → Clear data**, or uninstall Xylune.
- Clearing data or uninstalling also removes locally stored encrypted OAuth sessions and cloud credentials.

## Delete cloud backups

Open **Xylune → Settings → Backup & transfer**, select the connected destination, browse backups, and choose **Delete**. A backup can also be deleted directly through Google Drive app data controls, OneDrive Apps/Xylune, Dropbox Apps/Xylune, the configured WebDAV/Nextcloud folder, or the configured S3 bucket/prefix.

Disconnecting a provider removes the local session or credentials but does **not** automatically delete backups already stored there. Revoking Xylune in the Google, Microsoft, or Dropbox account security page stops future access but likewise does not necessarily delete stored files.

For a non-confidential support or privacy request, use the [Xylune issue tracker](https://github.com/omerfaruknehir/Xylune/issues). GitHub issues are public, so do not post secrets or identity documents. For a confidential OAuth-related request, use the private contact method shown on the relevant OAuth consent screen. The maintainer can act only on information actually received and controlled through an official Xylune channel.

---

# Xylune veri silme

Xylune merkezi bir kullanıcı hesabı işletmez. Verilerin çoğu Android cihazda veya kullanıcının seçtiği sağlayıcıda tutulur.

- Tekil sohbet, anı, sağlayıcı, taslak veya diğer kayıtları ilgili Xylune ekranından silin.
- Tüm yerel verileri kaldırmak için Android'de **Ayarlar → Uygulamalar → Xylune → Depolama → Veriyi temizle** yolunu kullanın veya uygulamayı kaldırın.
- Bulut yedeklerini silmek için **Xylune → Ayarlar → Yedekleme ve aktarım** bölümünde hedefi açın, yedeği seçin ve **Sil** komutunu kullanın.
- Sağlayıcı bağlantısını kesmek yalnızca cihazdaki oturumu veya kimlik bilgisini siler; sağlayıcıdaki mevcut yedekleri otomatik silmez.
- Google, Microsoft veya Dropbox hesap güvenliği sayfasından Xylune erişimini iptal etmek gelecekteki erişimi durdurur; mevcut dosyaları ayrıca silmek gerekebilir.

Gizli olmayan destek veya gizlilik başvurusu için [Xylune issue sayfasını](https://github.com/omerfaruknehir/Xylune/issues) kullanın. GitHub issue'ları herkese açıktır; sır veya kimlik belgesi paylaşmayın. Gizli OAuth başvurusu için ilgili OAuth onay ekranındaki özel iletişim yöntemini kullanın. Geliştirici yalnızca resmî Xylune kanalı üzerinden fiilen aldığı ve kontrol ettiği bilgi üzerinde işlem yapabilir.
