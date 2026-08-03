#!/usr/bin/env python3
"""Finalize Xylune's canonical package identity and GitHub Pages legal site."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "app.xylune.chat"
SHA1 = "59:54:74:CB:CC:00:73:74:65:3A:70:53:DF:37:92:DB:ED:16:AD:99"
SHA256 = "B9:D9:5D:F7:AD:06:61:55:93:41:62:32:27:CB:0C:C5:21:85:24:71:5A:F5:D7:B3:1A:F2:EC:D0:E7:D5:77:B9"
MS_HASH = "WVR0y8wAc3RlOnBT3zeS2+0WrZk="
MS_REDIRECT = "msauth://app.xylune.chat/WVR0y8wAc3RlOnBT3zeS2%2B0WrZk%3D"
SITE = "https://omerfaruknehir.github.io/Xylune"


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected one {label} in {path}, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def update_identity() -> None:
    build = ROOT / "app/build.gradle.kts"
    replace_once(
        build,
        '''            } else {
                // Public GitHub releases stay update-compatible with the previously
                // distributed .debug package while using the optimized release build type.
                applicationIdSuffix = ".debug"
                signingConfig = signingConfigs.getByName("debug")
            }
''',
        '''            } else {
                // Public GitHub releases use Xylune's canonical package while retaining
                // the repository's reproducible public signing certificate.
                signingConfig = signingConfigs.getByName("debug")
            }
''',
        "public release package block",
    )

    oauth = ROOT / "app/src/main/java/app/xylune/chat/transfer/CloudOAuthManager.kt"
    replace_once(
        oauth,
        '        return "msauth://${BuildConfig.APPLICATION_ID}/${base64Url(raw)}"\n',
        '        val signatureHash = Base64.encodeToString(raw, Base64.NO_WRAP)\n'
        '        return "msauth://${BuildConfig.APPLICATION_ID}/${Uri.encode(signatureHash)}"\n',
        "Microsoft redirect URI",
    )

    for relative in ("README.md", "BUILDING.md", "ci/README.md"):
        path = ROOT / relative
        if path.is_file():
            path.write_text(
                path.read_text(encoding="utf-8").replace("app.xylune.chat.debug", PACKAGE),
                encoding="utf-8",
            )


def privacy_text() -> str:
    return f'''---
layout: default
title: Privacy Policy / Gizlilik ve KVKK Aydınlatma Metni
---

# Xylune Privacy Policy

**Effective date: August 3, 2026**  
[Türkçe metin aşağıdadır.](#xylune-gizlilik-politikası-ve-kvkk-aydınlatma-metni)

Xylune is a local-first, bring-your-own-provider Android application maintained from Türkiye by **Ömer Faruk Nehir**. Xylune does not require a Xylune account and does not operate a central server that receives copies of chats or cloud backups.

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

Local data remains until deleted in Xylune, cleared through Android settings, or removed by uninstalling the app. Cloud backups remain until deleted in Xylune or through the provider. See the [data deletion instructions]({SITE}/data-deletion/).

Privacy questions and requests may be submitted through the [Xylune issue tracker](https://github.com/omerfaruknehir/Xylune/issues). GitHub issues are public; do not include passwords, tokens, identity documents, or other secrets. A private contact method may also be provided in the relevant OAuth consent screen.

---

# Xylune Gizlilik Politikası ve KVKK Aydınlatma Metni

**Yürürlük tarihi: 3 Ağustos 2026**

Xylune, **Ömer Faruk Nehir** tarafından Türkiye'den sürdürülen, yerel öncelikli ve kullanıcının kendi sağlayıcısını bağladığı bir Android uygulamasıdır. Xylune hesabı zorunlu değildir. Xylune geliştiricisi, sohbetlerin veya bulut yedeklerinin kopyalarını alan merkezi bir sunucu işletmez.

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

Yerel veriler Xylune içinden silinene, Android uygulama verileri temizlenene veya uygulama kaldırılana kadar tutulur. Bulut yedekleri Xylune veya sağlayıcı üzerinden silinene kadar ilgili sağlayıcıda kalır. Ayrıntılı adımlar için [veri silme sayfasına]({SITE}/data-deletion/) bakın.

## KVKK kapsamındaki haklar

6698 sayılı Kanun'un 11. maddesi çerçevesinde, uygulanabildiği ölçüde; kişisel veri işlenip işlenmediğini öğrenme, bilgi talep etme, işleme amacını ve amaca uygun kullanımı öğrenme, aktarılan üçüncü kişileri bilme, düzeltme, silme veya yok etme, bu işlemlerin aktarılan üçüncü kişilere bildirilmesini isteme, yalnızca otomatik analiz sonucu aleyhe çıkan sonuca itiraz etme ve hukuka aykırı işleme nedeniyle zararın giderilmesini talep etme hakları kullanılabilir.

Başvurular [Xylune GitHub issue sayfası](https://github.com/omerfaruknehir/Xylune/issues) üzerinden iletilebilir. GitHub issue'ları herkese açıktır; parola, token, kimlik belgesi veya başka bir sır paylaşmayın. İlgili OAuth onay ekranında ayrıca özel bir destek iletişim yöntemi gösterilebilir.
'''


def terms_text() -> str:
    return '''---
layout: default
title: Terms of Service / Kullanım Koşulları
---

# Xylune Terms of Service

**Effective date: August 3, 2026**  
[Türkçe metin aşağıdadır.](#xylune-kullanım-koşulları)

Xylune is open-source software distributed under the Apache License 2.0. That license governs copying, modification, and distribution of the source code. These terms govern use of the official Xylune application and related hosted documentation.

Users are responsible for their prompts, content, credentials, provider accounts, endpoints, generated results, backups, and actions performed through Xylune. Xylune may only be used in compliance with applicable law and the terms of connected AI, cloud, storage, website, and other third-party services.

Xylune is a client application and does not operate Google Drive, OneDrive, Dropbox, WebDAV/Nextcloud, S3-compatible storage, AI providers, or websites. Availability, quotas, prices, retention, account decisions, and provider content rules are controlled by those providers. Provider names and trademarks belong to their owners and do not imply sponsorship or endorsement.

Users must verify that backups complete successfully, test restoration, retain required passwords, and keep additional copies of important data. Passwordless archives are not encrypted. Experimental tools, generated widgets, Linux environments, automation, code execution, and model outputs must be reviewed before use and must not be relied on for safety-critical operation.

To the maximum extent permitted by law, Xylune is provided **as is** and **as available**, without warranties of merchantability, fitness for a particular purpose, uninterrupted operation, data preservation, provider compatibility, or error-free output. The developer and contributors are not liable for indirect, incidental, special, consequential, exemplary, or punitive damages, or for loss of data, credentials, accounts, profits, or access to third-party services.

These terms are governed by the laws of the Republic of Türkiye. Mandatory consumer protections and mandatory jurisdiction rules remain unaffected.

---

# Xylune Kullanım Koşulları

**Yürürlük tarihi: 3 Ağustos 2026**

Xylune kaynak kodu Apache License 2.0 altında dağıtılır. Kaynak kodun kopyalanması, değiştirilmesi ve dağıtımı bu lisansa tabidir. Bu koşullar resmî Xylune uygulamasının ve barındırılan belgelerin kullanımını düzenler.

Kullanıcı; istemlerinden, içeriklerinden, kimlik bilgilerinden, sağlayıcı hesaplarından, uç noktalarından, oluşturulan sonuçlardan, yedeklerinden ve Xylune üzerinden yaptığı işlemlerden sorumludur. Xylune yalnızca yürürlükteki mevzuata ve bağlanan yapay zekâ, bulut, depolama, internet sitesi veya diğer üçüncü taraf hizmetlerin koşullarına uygun kullanılabilir.

Xylune bir istemci uygulamasıdır; Google Drive, OneDrive, Dropbox, WebDAV/Nextcloud, S3 uyumlu depolama, yapay zekâ sağlayıcıları veya internet sitelerini işletmez. Kullanılabilirlik, kota, fiyat, saklama, hesap kararları ve içerik kuralları ilgili sağlayıcılarca belirlenir. Sağlayıcı adları ve markaları sahiplerine aittir; sponsorluk veya onay anlamına gelmez.

Kullanıcı yedeklemenin başarıyla tamamlandığını doğrulamalı, geri yüklemeyi test etmeli, gerekli parolaları saklamalı ve önemli verilerin ek kopyalarını tutmalıdır. Parolasız arşivler şifrelenmez. Deneysel araçlar, oluşturulan widget'lar, Linux ortamları, otomasyon, kod çalıştırma ve model çıktıları kullanılmadan önce incelenmeli; güvenlik açısından kritik işlerde bunlara güvenilmemelidir.

Kanunun izin verdiği azami ölçüde Xylune **olduğu gibi** ve **mevcut haliyle** sunulur. Ticari elverişlilik, belirli bir amaca uygunluk, kesintisiz çalışma, veri korunması, sağlayıcı uyumluluğu veya hatasız sonuç garantisi verilmez. Geliştirici ve katkıda bulunanlar dolaylı, arızi, özel, sonuçsal, örnek veya cezai zararlardan; veri, kimlik bilgisi, hesap, kazanç veya üçüncü taraf hizmet erişimi kaybından sorumlu tutulamaz.

Bu koşullara Türkiye Cumhuriyeti hukuku uygulanır. Tüketici mevzuatından doğan emredici haklar ve zorunlu yetki kuralları saklıdır.
'''


def deletion_text() -> str:
    return f'''---
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

For a support or privacy request, use the [Xylune issue tracker](https://github.com/omerfaruknehir/Xylune/issues). Do not post secrets or identity documents in a public issue.

---

# Xylune veri silme

Xylune merkezi bir kullanıcı hesabı işletmez. Verilerin çoğu Android cihazda veya kullanıcının seçtiği sağlayıcıda tutulur.

- Tekil sohbet, anı, sağlayıcı, taslak veya diğer kayıtları ilgili Xylune ekranından silin.
- Tüm yerel verileri kaldırmak için Android'de **Ayarlar → Uygulamalar → Xylune → Depolama → Veriyi temizle** yolunu kullanın veya uygulamayı kaldırın.
- Bulut yedeklerini silmek için **Xylune → Ayarlar → Yedekleme ve aktarım** bölümünde hedefi açın, yedeği seçin ve **Sil** komutunu kullanın.
- Sağlayıcı bağlantısını kesmek yalnızca cihazdaki oturumu veya kimlik bilgisini siler; sağlayıcıdaki mevcut yedekleri otomatik silmez.
- Google, Microsoft veya Dropbox hesap güvenliği sayfasından Xylune erişimini iptal etmek gelecekteki erişimi durdurur; mevcut dosyaları ayrıca silmek gerekebilir.

Destek veya gizlilik başvurusu için [Xylune issue sayfasını](https://github.com/omerfaruknehir/Xylune/issues) kullanın. Herkese açık issue içinde sır veya kimlik belgesi paylaşmayın.
'''


def write_site() -> None:
    docs = ROOT / "docs"
    (docs / "privacy").mkdir(parents=True, exist_ok=True)
    (docs / "terms").mkdir(parents=True, exist_ok=True)
    (docs / "data-deletion").mkdir(parents=True, exist_ok=True)

    (docs / "_config.yml").write_text('''title: Xylune\ndescription: Native Android. Private by design.\ntheme: jekyll-theme-minimal\nshow_downloads: false\n''', encoding="utf-8")
    (docs / "index.md").write_text(f'''---
layout: default
title: Xylune
---

# Xylune

**Native Android. Private by design.**

Xylune is a local-first Android application for user-configured AI providers, tools, programmable widgets, and portable backups.

- [Privacy Policy / Gizlilik ve KVKK Aydınlatma Metni]({SITE}/privacy/)
- [Terms of Service / Kullanım Koşulları]({SITE}/terms/)
- [Data Deletion / Veri Silme]({SITE}/data-deletion/)
- [Source code and releases](https://github.com/omerfaruknehir/Xylune)
- [Support and issues](https://github.com/omerfaruknehir/Xylune/issues)

Cloud backups are transferred directly between the user's device and the destination selected by the user. Xylune does not operate a central cloud-backup service.
''', encoding="utf-8")
    privacy = privacy_text()
    terms = terms_text()
    deletion = deletion_text()
    (docs / "privacy/index.md").write_text(privacy, encoding="utf-8")
    (docs / "terms/index.md").write_text(terms, encoding="utf-8")
    (docs / "data-deletion/index.md").write_text(deletion, encoding="utf-8")
    (ROOT / "PRIVACY.md").write_text(privacy.replace("---\nlayout: default\ntitle: Privacy Policy / Gizlilik ve KVKK Aydınlatma Metni\n---\n\n", ""), encoding="utf-8")
    (ROOT / "TERMS.md").write_text(terms.replace("---\nlayout: default\ntitle: Terms of Service / Kullanım Koşulları\n---\n\n", ""), encoding="utf-8")
    (ROOT / "DATA_DELETION.md").write_text(deletion.replace("---\nlayout: default\ntitle: Data Deletion / Veri Silme\n---\n\n", ""), encoding="utf-8")


def update_setup_docs() -> None:
    google = ROOT / "docs/GOOGLE_DRIVE_SETUP.md"
    google.write_text(f'''# Google Drive app-data authorization

Xylune requests only `https://www.googleapis.com/auth/drive.appdata` and stores backups in Drive's hidden `appDataFolder`. No OAuth client secret belongs in the APK or repository.

## Google Cloud configuration

1. Enable Google Drive API.
2. Configure and publish the OAuth consent screen, or add permitted test users while in Testing.
3. Use these public URLs:
   - Homepage: `{SITE}/`
   - Privacy: `{SITE}/privacy/`
   - Terms: `{SITE}/terms/`
4. Create an OAuth client ID of type Android.
5. Register the package and signing SHA-1 below.

## Public GitHub release identity

- Package: `{PACKAGE}`
- SHA-1: `{SHA1}`
- SHA-256: `{SHA256}`

A protected release keeps package `{PACKAGE}` but uses its private signing certificate, so it needs another Android OAuth client for that certificate SHA-1.

Xylune displays its current package, SHA-1, and SHA-256 in the Google Drive diagnostic card when registration is missing.
''', encoding="utf-8")

    cloud = ROOT / "docs/CLOUD_PROVIDERS_SETUP.md"
    text = cloud.read_text(encoding="utf-8").replace("app.xylune.chat.debug", PACKAGE)
    marker = "Xylune uses Authorization Code + PKCE and requests `offline_access`; do not create or embed a client secret.\n"
    extra = f'''\nFor the public GitHub release:\n\n- Package: `{PACKAGE}`\n- Microsoft signature hash: `{MS_HASH}`\n- Generated redirect URI: `{MS_REDIRECT}`\n\nThe signature hash is the standard Base64 encoding of the signing certificate's SHA-1 digest. A privately signed release needs a second Android platform entry with the same package and its own signature hash.\n'''
    if extra.strip() not in text:
        if marker not in text:
            raise RuntimeError("OneDrive setup marker not found")
        text = text.replace(marker, marker + extra, 1)
    if "## Public legal URLs" not in text:
        text += f'''\n## Public legal URLs\n\n- Homepage: `{SITE}/`\n- Privacy: `{SITE}/privacy/`\n- Terms: `{SITE}/terms/`\n- Data deletion: `{SITE}/data-deletion/`\n\nUse these URLs in Google Auth Platform, Microsoft Entra, Dropbox, and provider review forms.\n'''
    cloud.write_text(text, encoding="utf-8")


def update_changelog() -> None:
    path = ROOT / "CHANGELOG.md"
    text = path.read_text(encoding="utf-8")
    anchor = "## 0.23.1 — 2026-08-03\n"
    addition = "- Publish bilingual GitHub Pages privacy, KVKK disclosure, terms, and data-deletion pages; standardize public releases on `app.xylune.chat` and document the exact Google and Microsoft signing identities.\n"
    if addition not in text:
        if anchor not in text:
            raise RuntimeError("0.23.1 changelog heading not found")
        text = text.replace(anchor, anchor + "\n" + addition, 1)
    path.write_text(text, encoding="utf-8")


def validate() -> None:
    build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    release_block = build.split("buildTypes {", 1)[1].split("debug {", 1)[0]
    if 'applicationIdSuffix = ".debug"' in release_block:
        raise RuntimeError("Release package still has the .debug suffix")
    oauth = (ROOT / "app/src/main/java/app/xylune/chat/transfer/CloudOAuthManager.kt").read_text(encoding="utf-8")
    required = [
        "Base64.encodeToString(raw, Base64.NO_WRAP)",
        "Uri.encode(signatureHash)",
        PACKAGE,
        SHA1,
        MS_HASH,
        "6698",
    ]
    combined = "\n".join(path.read_text(encoding="utf-8") for path in [
        ROOT / "docs/GOOGLE_DRIVE_SETUP.md",
        ROOT / "docs/CLOUD_PROVIDERS_SETUP.md",
        ROOT / "docs/privacy/index.md",
        ROOT / "PRIVACY.md",
    ]) + oauth
    missing = [token for token in required if token not in combined]
    if missing:
        raise RuntimeError("Missing legal/identity tokens: " + ", ".join(missing))


def main() -> None:
    update_identity()
    write_site()
    update_setup_docs()
    update_changelog()
    validate()
    print(f"Canonical release package: {PACKAGE}")
    print(f"Signing SHA-1: {SHA1}")
    print(f"Microsoft signature hash: {MS_HASH}")
    print(f"Microsoft redirect URI: {MS_REDIRECT}")


if __name__ == "__main__":
    main()
