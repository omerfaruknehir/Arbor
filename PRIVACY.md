# Xylune Privacy Policy

**Effective date: August 3, 2026**  
[Türkçe metin aşağıdadır.](#xylune-gizlilik-politikası-ve-kvkk-aydınlatma-metni)

Xylune is a local-first, bring-your-own-provider Android application maintained from Türkiye by **Ömer Faruk Nehir**. Xylune does not require an account and does not operate a central server that receives copies of chats or cloud backups.

## Data handled on the device

Xylune may store chats, prompts, model responses, drafts, attachments, memories, settings, tool results, generated content, and optional Linux-environment data on the user's device. API keys, OAuth sessions, WebDAV credentials, and S3 credentials are stored in encrypted app storage backed by Android Keystore where supported. Credentials and OAuth sessions are excluded from portable Xylune backup archives.

The official build does not automatically send analytics or crash reports to a Xylune-operated service. Diagnostic data leaves the device only when the user explicitly exports or sends it.

## AI providers, tools, and permissions

When a user invokes an AI provider, web research, a URL, a generated widget, a local tool, or another external service, Xylune sends the information needed for that user-requested operation directly to the selected service. This may include prompts, conversation context, attachments, search queries, tool inputs, location when separately permitted, or generated outputs. Those services process data under their own terms and privacy policies.

## Cloud backup

A backup can include chats, attachments, settings, memories, and optional Linux-environment files selected by the user. The archive is transferred directly between the device and the selected destination:

- **Google Drive:** only the hidden `appDataFolder`, through `https://www.googleapis.com/auth/drive.appdata`.
- **Microsoft OneDrive:** only the application's OneDrive folder, through `Files.ReadWrite.AppFolder`.
- **Dropbox:** only Xylune's Dropbox App folder, using scoped account and file permissions.
- **WebDAV / Nextcloud:** only the HTTPS endpoint and folder configured by the user.
- **S3-compatible storage:** only the HTTPS endpoint, bucket, and prefix configured by the user.
- **Android document providers:** only the folder or document permission granted through Android's system picker.

Google, Microsoft, and Dropbox may return an account label, name, or email address so Xylune can display the connected account. That value and the OAuth session remain on the device. Disconnecting removes the local session or credentials but does not automatically delete backups already stored by the provider.

Xylune's use and transfer of information received from Google APIs complies with the Google API Services User Data Policy, including the Limited Use requirements. Google user data is used only for user-requested backup, browsing, restore, and deletion functions and is not used by Xylune for advertising, profiling, or model training.

## International processing

Google, Microsoft, Dropbox, GitHub Pages, AI providers, and user-selected storage endpoints may process data outside Türkiye. The user chooses whether to connect these services. Each provider is responsible for its own infrastructure and legal mechanisms. Users must not upload data they are not authorized to transfer.

## Retention, deletion, and contact

Local data remains until deleted in Xylune, cleared through Android settings, or removed by uninstalling the app. Cloud backups remain until deleted in Xylune or through the provider. See the [data deletion instructions](https://omerfaruknehir.github.io/Xylune/data-deletion/).

Privacy questions and requests may be submitted through the [Xylune issue tracker](https://github.com/omerfaruknehir/Xylune/issues). GitHub issues are public; do not include passwords, tokens, identity documents, or other secrets. A private contact method may also be provided in the relevant OAuth consent screen.

---

# Xylune Gizlilik Politikası ve KVKK Aydınlatma Metni

**Yürürlük tarihi: 3 Ağustos 2026**

Xylune, **Ömer Faruk Nehir** tarafından Türkiye'den sürdürülen, yerel öncelikli ve kullanıcının kendi sağlayıcısını bağladığı bir Android uygulamasıdır. Xylune, herhangi bir online hesap açılmasını gerektirmez.. Xylune geliştiricisi, sohbetlerin veya bulut yedeklerinin kopyalarını alan merkezi bir sunucu işletmez.

## Veri sorumlusu ve kapsam

Xylune geliştiricisinin fiilen elde ettiği destek talepleri, OAuth uygulama yapılandırması ve proje güvenliğiyle ilgili sınırlı işlemler bakımından veri sorumlusu **Ömer Faruk Nehir**'dir. Yalnızca kullanıcının cihazında kalan ve geliştiriciye iletilmeyen içerikler bakımından geliştirici bu verilerin bir kopyasını görmez. Google, Microsoft, Dropbox, GitHub ve kullanıcının seçtiği diğer hizmetler kendi işleme faaliyetleri bakımından ayrıca sorumludur.

## İşlenen veya cihazda tutulan veri kategorileri

Xylune; sohbetleri, istemleri, model yanıtlarını, taslakları, ekleri, anıları, ayarları, araç sonuçlarını, oluşturulan içerikleri ve isteğe bağlı Linux ortamı dosyalarını cihazda tutabilir. API anahtarları, OAuth oturumları, WebDAV ve S3 kimlik bilgileri desteklenen cihazlarda Android Keystore destekli şifreli uygulama alanında saklanır. Kimlik bilgileri ve OAuth oturumları taşınabilir Xylune yedeklerine dahil edilmez.

Resmî sürüm, Xylune tarafından işletilen bir analitik veya çökme raporlama servisine otomatik veri göndermez. Tanılama verileri yalnızca kullanıcı açıkça dışa aktarıp paylaştığında cihazdan çıkar.

## İşleme amaçları, yöntem ve hukuki sebepler

Veriler; kullanıcının talep ettiği yerel depolama, model isteği, araç çalıştırma, yedekleme, geri yükleme, güvenlik ve destek işlevlerini sunmak amacıyla otomatik veya kısmen otomatik yöntemlerle işlenir. İşlemenin geliştirici tarafından gerçekleştirildiği ölçüde hukuki sebepler; bir hizmet ilişkisinin kurulması veya ifası için zorunluluk, bir hakkın tesisi/kullanılması/korunması, meşru menfaat ve ilgili işlemin niteliğine göre kullanıcının açık talebi veya açık rızasıdır. Sağlayıcı bağlantısı kurulmadan önce ilgili OAuth izin ekranı ayrıca gösterilir.

## Bulut sağlayıcılarına aktarım

Kullanıcı bir yedek oluşturduğunda seçtiği arşiv; Google Drive `appDataFolder`, OneDrive uygulama klasörü, Dropbox App klasörü, kullanıcının belirlediği WebDAV/Nextcloud klasörü, S3 uyumlu bucket/prefix veya Android sistem seçicisinde izin verilen hedefe doğrudan aktarılır. Sağlayıcı, bağlı hesabı göstermek için ad veya e-posta gibi bir hesap etiketi döndürebilir. Bağlantıyı kesmek cihazdaki oturumu siler; sağlayıcıda bulunan mevcut yedekleri kendiliğinden silmez.

## Yurt dışına aktarım

Google, Microsoft, Dropbox, GitHub Pages, yapay zekâ sağlayıcıları ve kullanıcı tarafından seçilen depolama uçları Türkiye dışında veri işleyebilir. Bu bağlantılar kullanıcının seçimiyle kurulur ve ilgili sağlayıcının koşulları ile veri aktarım mekanizmaları geçerlidir. 6698 sayılı Kanun'un 9. maddesi kapsamındaki yurt dışı aktarım kuralları değişebildiğinden, özellikle üçüncü kişilere ait veya kurumsal veriler yüklenmeden önce gerekli hukuki dayanak ve güvenceler kullanıcı tarafından değerlendirilmelidir.

## Saklama ve silme

Yerel veriler Xylune içinden silinene, Android uygulama verileri temizlenene veya uygulama kaldırılana kadar tutulur. Bulut yedekleri Xylune veya sağlayıcı üzerinden silinene kadar ilgili sağlayıcıda kalır. Ayrıntılı adımlar için [veri silme sayfasına](https://omerfaruknehir.github.io/Xylune/data-deletion/) bakın.

## KVKK kapsamındaki haklar

6698 sayılı Kanun'un 11. maddesi çerçevesinde, uygulanabildiği ölçüde; kişisel veri işlenip işlenmediğini öğrenme, bilgi talep etme, işleme amacını ve amaca uygun kullanımı öğrenme, aktarılan üçüncü kişileri bilme, düzeltme, silme veya yok etme, bu işlemlerin aktarılan üçüncü kişilere bildirilmesini isteme, yalnızca otomatik analiz sonucu aleyhe çıkan sonuca itiraz etme ve hukuka aykırı işleme nedeniyle zararın giderilmesini talep etme hakları kullanılabilir.

Başvurular [Xylune GitHub issue sayfası](https://github.com/omerfaruknehir/Xylune/issues) üzerinden iletilebilir. GitHub issue'ları herkese açıktır; parola, token, kimlik belgesi veya başka bir sır paylaşmayın. İlgili OAuth onay ekranında ayrıca özel bir destek iletişim yöntemi gösterilebilir.
