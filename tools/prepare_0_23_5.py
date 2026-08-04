#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# Predictive navigation: keep pages opaque, reduce motion, and accept a new
# back gesture even while an ordinary 120 ms transition is finishing.
# ---------------------------------------------------------------------------
predictive_path = "app/src/main/java/app/xylune/chat/ui/PredictiveNavigation.kt"
predictive = read(predictive_path)
predictive = replace_once(
    predictive,
    "private const val CommitFadeStart = 0.62f\n",
    "",
    "remove endpoint fade constant",
)
predictive = replace_once(
    predictive,
    '''internal fun predictiveBackOutgoingAlpha(progress: Float): Float {
    val fadeProgress = ((progress.coerceIn(0f, 1f) - CommitFadeStart) /
        (1f - CommitFadeStart)).coerceIn(0f, 1f)
    return 1f - NavigationEasing.transform(fadeProgress)
}
''',
    '''internal fun predictiveBackVisualProgress(progress: Float): Float =
    NavigationEasing.transform(progress.coerceIn(0f, 1f))

internal fun predictiveBackSourceScale(progress: Float): Float =
    1f - 0.04f * predictiveBackVisualProgress(progress)
''',
    "replace alpha curve with opaque motion curve",
)
predictive = replace_once(
    predictive,
    '''        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                settleImmediatelyOn(latestTargetState)
                progress.snapTo(0f)
            }
            throw cancelled
        }
''',
    '''        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                // Animatable mutation by an incoming predictive gesture cancels
                // the ordinary transition. Do not let that cancelled animation
                // overwrite the gesture's source/destination slots.
                if (mode != NavigationTransitionMode.PREDICTIVE) {
                    settleImmediatelyOn(latestTargetState)
                    progress.snapTo(0f)
                }
            }
            throw cancelled
        }
''',
    "protect predictive gesture from ordinary transition cancellation",
)
predictive = replace_once(
    predictive,
    '''    PredictiveBackHandler(
        enabled = appBackHandlerEnabled(
            ownerEnabled = backEnabled && backTarget != null && mode != NavigationTransitionMode.ORDINARY,
            imeVisible = imeVisible,
        ),
    ) { events ->
        val destinationState = latestBackTarget ?: return@PredictiveBackHandler
''',
    '''    PredictiveBackHandler(
        enabled = appBackHandlerEnabled(
            ownerEnabled = backEnabled && backTarget != null,
            imeVisible = imeVisible,
        ),
    ) { events ->
        // A quick second Back swipe must not fall through merely because the
        // previous button/page transition is still in its short settle phase.
        if (mode == NavigationTransitionMode.ORDINARY) {
            settleImmediatelyOn(latestTargetState)
            progress.snapTo(0f)
        }
        val destinationState = latestBackTarget ?: return@PredictiveBackHandler
''',
    "remove predictive-back dead zone",
)
predictive = replace_once(
    predictive,
    '''                            val p = progress.value.coerceIn(0f, 1f)
                            when (mode) {
                                NavigationTransitionMode.PREDICTIVE -> when {
                                    isSource -> {
                                        translationX = predictiveDirection * widthPx * 0.26f * p
                                        alpha = predictiveBackOutgoingAlpha(p)
                                        compositingStrategy = CompositingStrategy.ModulateAlpha
                                    }
                                    isDestination -> {
                                        translationX = -predictiveDirection * widthPx * 0.04f * (1f - p)
                                    }
                                }
''',
    '''                            val p = progress.value.coerceIn(0f, 1f)
                            when (mode) {
                                NavigationTransitionMode.PREDICTIVE -> {
                                    val visualProgress = predictiveBackVisualProgress(p)
                                    when {
                                        isSource -> {
                                            // Keep the active page fully opaque. Fading a whole
                                            // Compose tree exposed intermediate surfaces and made
                                            // text/panels appear to blink on real devices.
                                            translationX = predictiveDirection * widthPx * 0.10f * visualProgress
                                            val sourceScale = predictiveBackSourceScale(p)
                                            scaleX = sourceScale
                                            scaleY = sourceScale
                                        }
                                        isDestination -> {
                                            translationX = -predictiveDirection * widthPx * 0.04f * (1f - visualProgress)
                                        }
                                    }
                                }
''',
    "replace predictive graphics",
)
write(predictive_path, predictive)

# Update the pure math tests to lock in the no-opacity design.
test_path = "app/src/test/java/app/xylune/chat/ui/PredictiveNavigationMathTest.kt"
test = read(test_path)
test = replace_once(
    test,
    '''    @Test
    fun outgoingPageStaysOpaqueUntilEndpointFadeAndEndsTransparent() {
        assertEquals(1f, predictiveBackOutgoingAlpha(0f), .0001f)
        assertEquals(1f, predictiveBackOutgoingAlpha(.62f), .0001f)
        assertEquals(0f, predictiveBackOutgoingAlpha(1f), .0001f)

        val values = (0..20).map { predictiveBackOutgoingAlpha(it / 20f) }
        values.zipWithNext().forEach { (a, b) -> assertTrue(b <= a) }
    }
''',
    '''    @Test
    fun predictiveVisualProgressIsClampedAndMonotonic() {
        assertEquals(0f, predictiveBackVisualProgress(-1f), .0001f)
        assertEquals(0f, predictiveBackVisualProgress(0f), .0001f)
        assertEquals(1f, predictiveBackVisualProgress(1f), .0001f)
        assertEquals(1f, predictiveBackVisualProgress(2f), .0001f)

        val values = (0..20).map { predictiveBackVisualProgress(it / 20f) }
        values.zipWithNext().forEach { (a, b) -> assertTrue(b >= a) }
    }

    @Test
    fun predictiveSourceScaleRemainsVisibleAndEndsAtNinetySixPercent() {
        assertEquals(1f, predictiveBackSourceScale(0f), .0001f)
        assertEquals(.96f, predictiveBackSourceScale(1f), .0001f)
        assertTrue(predictiveBackSourceScale(.5f) in .96f..1f)
    }
''',
    "update predictive navigation math tests",
)
write(test_path, test)

# ---------------------------------------------------------------------------
# Settings: expose the exact bundled core prompt as selectable, read-only text;
# add a concise third-party-AI disclosure and direct legal-document links.
# ---------------------------------------------------------------------------
settings_path = "app/src/main/java/app/xylune/chat/ui/SettingsScreen.kt"
settings = read(settings_path)
settings = replace_once(
    settings,
    "import androidx.compose.foundation.text.KeyboardOptions\n",
    "import androidx.compose.foundation.text.KeyboardOptions\nimport androidx.compose.foundation.text.selection.SelectionContainer\n",
    "add selection import",
)
settings = replace_once(
    settings,
    "import app.xylune.chat.settings.NewChatDefaults\n",
    "import app.xylune.chat.settings.NewChatDefaults\nimport app.xylune.chat.settings.DEFAULT_XYLUNE_SYSTEM_PROMPT\nimport app.xylune.chat.settings.XYLUNE_CORE_PROMPT_REVISION\n",
    "add core prompt imports",
)
old_prompt_panel = '''    HorizontalDivider()
    SectionTitle("Xylune core prompt", "Built into this app version and updated with Xylune. It is intentionally not editable or copied into chats.")
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Security, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 12.dp)) {
                Text("Managed by Xylune", fontWeight = FontWeight.SemiBold)
                Text("Use Custom instruction profiles for tone and workflow preferences.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
'''
new_prompt_panel = '''    HorizontalDivider()
    SectionTitle(
        "Xylune core prompt",
        "The exact prompt bundled with this app version is shown below. It is selectable for inspection and intentionally read-only.",
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Security, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Managed by Xylune · revision $XYLUNE_CORE_PROMPT_REVISION", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Use custom instruction profiles for additional tone and workflow preferences.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectionContainer {
                    Text(
                        text = DEFAULT_XYLUNE_SYSTEM_PROMPT,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Text(
                "Xylune adds request-specific date, enabled-tool, research, memory, attachment, and generated-content instructions at runtime. Those dynamic layers are not editable either and are not presented as one misleading static block.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
'''
settings = replace_once(settings, old_prompt_panel, new_prompt_panel, "replace core prompt banner")
settings = replace_once(
    settings,
    ''') = SettingsPage {
    SectionTitle("Generated content", "Controls how Xylune handles AI-generated interactive UI.")
''',
    ''') = SettingsPage {
    val uriHandler = LocalUriHandler.current
    SectionTitle("Generated content", "Controls how Xylune handles AI-generated interactive UI.")
''',
    "add privacy page URI handler",
)
repair_pattern = re.compile(
    r'''(    SettingSlider\(\n        label = "Automatic repair attempts",\n        valueLabel = generatedRepairMaxAttempts\.toString\(\),\n        value = generatedRepairMaxAttempts\.toFloat\(\),\n        onValueChange = \{ viewModel\.setGeneratedRepairMaxAttempts\(it\.toInt\(\)\.coerceIn\(1, 5\)\) \},\n        valueRange = 1f\.\.5f,\n        steps = 3,\n        supportingText = "Invalid completed widgets, charts, and diagrams are repaired in place up to this limit\.",\n    \)\n)'''
)
match = repair_pattern.search(settings)
if not match:
    raise RuntimeError("append third-party AI disclosure: repair slider block not found")
legal_ui = '''
    HorizontalDivider()
    SectionTitle(
        "Third-party AI and services",
        "Xylune is a client, not an AI model host. Responses come from the provider or local server selected by the user.",
    )
    Text(
        "The Xylune maintainer does not create, train, host, pre-review, or endorse individual model outputs. AI output can be wrong, unsafe, biased, or unsuitable; verify it before relying on it. Provider terms, fees, retention, and content rules apply independently.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { uriHandler.openUri("https://github.com/omerfaruknehir/Xylune/blob/main/PRIVACY.md") },
            modifier = Modifier.weight(1f),
        ) { Text("Privacy") }
        OutlinedButton(
            onClick = { uriHandler.openUri("https://github.com/omerfaruknehir/Xylune/blob/main/TERMS.md") },
            modifier = Modifier.weight(1f),
        ) { Text("Terms") }
    }
'''
settings = settings[:match.end()] + legal_ui + settings[match.end():]
write(settings_path, settings)

# ---------------------------------------------------------------------------
# Privacy policy: state the actual local/direct architecture, limit the
# maintainer's role to data actually received, preserve mandatory legal rights,
# and distinguish ordinary support from statutory privacy requests.
# ---------------------------------------------------------------------------
privacy = r'''# Xylune Privacy Policy

**Effective date: August 4, 2026**  
[Türkçe metin aşağıdadır.](#xylune-gizlilik-politikası-ve-kvkk-aydınlatma-metni)

Xylune is a local-first, bring-your-own-provider Android application maintained from Türkiye by **Ömer Faruk Nehir**. It may be downloaded and used worldwide. Xylune does not require a Xylune account, does not operate an application backend that relays AI requests, and does not operate a central server that receives copies of chats or cloud backups.

This policy describes the official Xylune build and the limited processing controlled by the maintainer. Forks, modified builds, AI providers, storage providers, websites, and other third-party services have their own operators and policies.

## 1. Data that stays under the user's control

Xylune may store chats, prompts, model responses, drafts, attachments, memories, settings, tool results, generated content, workspaces, and optional Linux-environment data on the user's device. API keys, OAuth sessions, WebDAV credentials, and S3 credentials are stored in encrypted app storage backed by Android Keystore where supported. Credentials and OAuth sessions are excluded from portable Xylune archives.

The official build does not automatically send analytics, advertising identifiers, chat telemetry, or crash reports to a service operated by the Xylune maintainer. Diagnostic data reaches the maintainer only when a user deliberately exports and sends it.

The maintainer cannot remotely read, search, recover, correct, export, or delete data that remains only on a user's device. Uninstalling Xylune or clearing its Android app data is controlled by the user and Android, not by the maintainer.

## 2. Direct connections chosen by the user

When a user invokes an AI provider, web search, a URL, OAuth sign-in, cloud storage, a generated widget, or another external service, Xylune sends the information needed for that user-requested operation directly from the device to the selected service. Depending on the action, this can include prompts, conversation context, attachments, search queries, tool inputs, approximate or precise location when separately permitted, account identifiers, or generated outputs.

The maintainer does not receive a copy merely because Xylune initiated the direct connection. Each selected service independently determines its own collection, retention, training, security, international-transfer, and deletion practices under its terms and privacy policy. Users should review those documents before connecting a service.

## 3. Cloud backup scopes

A backup can include chats, attachments, settings, memories, and optional Linux-environment files selected by the user. The archive is transferred directly between the device and the selected destination:

- **Google Drive:** the hidden `appDataFolder`, through `https://www.googleapis.com/auth/drive.appdata`.
- **Microsoft OneDrive:** the application's OneDrive folder, through `Files.ReadWrite.AppFolder`.
- **Dropbox:** Xylune's Dropbox App folder, using scoped account and file permissions.
- **WebDAV / Nextcloud:** the HTTPS endpoint and folder configured by the user.
- **S3-compatible storage:** the HTTPS endpoint, bucket, and prefix configured by the user.
- **Android document providers:** the folder or document permission granted through Android's system picker.

Google, Microsoft, and Dropbox may return an account label, name, or email address so Xylune can display the connected account. That value and the OAuth session remain on the device. Disconnecting removes the local session or credentials but does not automatically delete backups already stored by the provider.

Xylune's use and transfer of information received from Google APIs is limited to user-requested backup, browsing, restore, and deletion functions. It is not used by the maintainer for advertising, profiling, or model training.

## 4. Data the maintainer may actually receive

The maintainer may process only information that is deliberately sent to channels under the maintainer's control, such as:

- public GitHub issues, discussions, or pull requests;
- private support or privacy correspondence;
- security reports and diagnostic files deliberately submitted by a user; and
- limited OAuth application administration information made available by an identity provider.

For that received information, the maintainer may determine the purpose and means of processing to operate support, security, abuse prevention, OAuth configuration, legal compliance, and project maintenance. The maintainer is not the storage intermediary for device-only chats or direct user-to-provider transfers and cannot fulfil access, correction, deletion, or portability requests for copies the maintainer never received or cannot identify.

Public GitHub content remains subject to GitHub's visibility and retention controls. Do not post passwords, API keys, tokens, identity documents, private chat logs, or other secrets in a public issue.

## 5. AI output and generated content

Xylune is a client interface, not the developer or host of the third-party AI models selected by users. The maintainer does not create, train, host, pre-review, or endorse individual model responses. Model output can be inaccurate, harmful, biased, unlawful, or unsuitable. This allocation of roles does not remove any responsibility that cannot legally be excluded. See the [Xylune Terms of Use](TERMS.md) for usage and warranty terms.

## 6. International use and transfers

Xylune can be used outside Türkiye. A user's device, AI provider, storage provider, GitHub, and user-configured endpoints may be located in different countries. International processing is initiated by the user's provider and endpoint choices. Mandatory privacy and consumer rights in the user's jurisdiction continue to apply where applicable; this policy does not waive them.

Users must not transfer personal, confidential, institutional, or third-party data unless they have the necessary authority and legal basis to do so.

## 7. Retention and deletion

Local data remains until the user deletes it in Xylune, clears Android app data, or uninstalls the app. Cloud backups remain until deleted through Xylune or the storage provider. The maintainer cannot delete a provider-side backup without access to the user's provider account and does not retain a central backup copy. See the [data deletion instructions](https://omerfaruknehir.github.io/Xylune/data-deletion/).

Information deliberately sent to the maintainer is retained only as reasonably needed for the relevant support, security, project-history, abuse-prevention, or legal purpose, subject to the controls and retention of the communication platform used.

## 8. Requests, contact, and response times

Bug reports and ordinary support requests may be submitted through the [Xylune issue tracker](https://github.com/omerfaruknehir/Xylune/issues). It is a volunteer-maintained open-source project: **no response time, availability, or support service level is promised for ordinary support**.

A private contact method may be shown in the relevant OAuth consent screen for requests that should not be public. A request should identify the communication or other data that the maintainer actually received; the maintainer may need reasonable information to verify identity and locate it. Valid privacy-rights requests are handled within any mandatory deadline that applies to the particular processing and jurisdiction. This paragraph does not create rights or obligations beyond applicable law and does not extend the maintainer's technical access to device-only or provider-controlled data.

## 9. Changes

This policy may be updated when Xylune's architecture, connected services, or legal requirements change. The effective date and repository history identify revisions.

---

# Xylune Gizlilik Politikası ve KVKK Aydınlatma Metni

**Yürürlük tarihi: 4 Ağustos 2026**

Xylune, **Ömer Faruk Nehir** tarafından Türkiye'den sürdürülen, yerel öncelikli ve kullanıcının kendi sağlayıcısını bağladığı bir Android uygulamasıdır. Dünyanın farklı ülkelerinden indirilebilir ve kullanılabilir. Xylune hesabı gerekmez; yapay zekâ isteklerini aktaran bir Xylune uygulama sunucusu veya sohbetlerin ve bulut yedeklerinin kopyalarını toplayan merkezi bir Xylune sunucusu işletilmez.

Bu metin resmî Xylune derlemesini ve geliştiricinin fiilen kontrol ettiği sınırlı işlemeyi açıklar. Forklar, değiştirilmiş derlemeler, yapay zekâ sağlayıcıları, depolama sağlayıcıları, internet siteleri ve diğer üçüncü taraf hizmetler kendi işletmecilerine ve politikalarına tabidir.

## 1. Kullanıcının kontrolünde kalan veriler

Xylune; sohbetleri, istemleri, model yanıtlarını, taslakları, ekleri, anıları, ayarları, araç sonuçlarını, oluşturulan içerikleri, çalışma alanlarını ve isteğe bağlı Linux ortamı verilerini kullanıcının cihazında tutabilir. API anahtarları, OAuth oturumları, WebDAV ve S3 kimlik bilgileri desteklenen cihazlarda Android Keystore destekli şifreli uygulama alanında saklanır. Kimlik bilgileri ve OAuth oturumları taşınabilir Xylune arşivlerine dahil edilmez.

Resmî derleme; analitik, reklam kimliği, sohbet telemetrisi veya çökme raporunu Xylune geliştiricisinin işlettiği bir servise otomatik olarak göndermez. Tanılama verileri yalnızca kullanıcı bunları bilerek dışa aktarıp geliştiriciye gönderirse geliştiriciye ulaşır.

Yalnızca cihazda kalan verileri geliştirici uzaktan okuyamaz, arayamaz, kurtaramaz, düzeltemez, dışa aktaramaz veya silemez. Uygulamayı kaldırma ve Android uygulama verisini temizleme işlemleri kullanıcı ve Android tarafından yönetilir.

## 2. Kullanıcının seçtiği doğrudan bağlantılar

Kullanıcı bir yapay zekâ sağlayıcısını, web aramasını, URL'yi, OAuth oturumunu, bulut depolamayı, oluşturulmuş widget'ı veya başka bir dış hizmeti kullandığında Xylune, kullanıcının talep ettiği işlem için gereken bilgiyi cihazdan doğrudan seçilen hizmete gönderir. İşleme göre bu bilgi; istem, sohbet bağlamı, ek, arama sorgusu, araç girdisi, ayrıca izin verilmiş yaklaşık veya kesin konum, hesap tanımlayıcısı ya da oluşturulmuş çıktı içerebilir.

Bağlantının Xylune tarafından başlatılması, geliştiricinin otomatik olarak bir kopya aldığı anlamına gelmez. Her hizmet; toplama, saklama, model eğitimi, güvenlik, yurt dışı aktarım ve silme uygulamalarını kendi koşulları ve gizlilik politikası kapsamında bağımsız olarak belirler. Kullanıcı, bir hizmeti bağlamadan önce bu belgeleri incelemelidir.

## 3. Bulut yedekleme kapsamları

Yedek; kullanıcının seçimine göre sohbet, ek, ayar, anı ve isteğe bağlı Linux ortamı dosyalarını içerebilir. Arşiv cihaz ile seçilen hedef arasında doğrudan aktarılır:

- **Google Drive:** `https://www.googleapis.com/auth/drive.appdata` kapsamıyla gizli `appDataFolder`.
- **Microsoft OneDrive:** `Files.ReadWrite.AppFolder` kapsamıyla uygulama klasörü.
- **Dropbox:** kapsamı sınırlandırılmış hesap ve dosya izinleriyle Xylune App klasörü.
- **WebDAV / Nextcloud:** kullanıcının yapılandırdığı HTTPS uç noktası ve klasör.
- **S3 uyumlu depolama:** kullanıcının yapılandırdığı HTTPS uç noktası, bucket ve prefix.
- **Android belge sağlayıcıları:** Android sistem seçicisinde izin verilen klasör veya belge.

Google, Microsoft ve Dropbox bağlı hesabı göstermek için ad veya e-posta gibi bir hesap etiketi döndürebilir. Bu değer ve OAuth oturumu cihazda kalır. Bağlantıyı kesmek cihazdaki oturumu veya kimlik bilgilerini kaldırır; sağlayıcıdaki mevcut yedekleri kendiliğinden silmez.

Google API'lerinden alınan bilgiler yalnızca kullanıcının istediği yedekleme, listeleme, geri yükleme ve silme işlevleri için kullanılır; geliştirici tarafından reklam, profilleme veya model eğitimi amacıyla kullanılmaz.

## 4. Geliştiricinin fiilen alabileceği veriler ve veri sorumluluğunun sınırı

Geliştirici yalnızca kendi kontrolündeki kanallara bilerek gönderilen şu tür bilgileri işleyebilir:

- herkese açık GitHub issue, discussion veya pull request içerikleri;
- özel destek veya gizlilik yazışmaları;
- kullanıcının bilerek ilettiği güvenlik raporları ve tanılama dosyaları; ve
- kimlik sağlayıcısının OAuth uygulama yönetimi kapsamında sunduğu sınırlı bilgiler.

Geliştirici, kendisine ulaşan bu bilgiler bakımından destek, güvenlik, kötüye kullanımın önlenmesi, OAuth yapılandırması, hukuki uyum ve proje bakımı amaç ve araçlarını belirlediği ölçüde veri sorumlusu olabilir. Geliştirici; yalnızca cihazda kalan sohbetlerin veya kullanıcıdan sağlayıcıya doğrudan aktarılan verilerin depolama aracısı değildir, bunların kopyasını elde etmez ve elde etmediği ya da kimliğini ilişkilendiremediği bir kopya için erişim, düzeltme, silme veya taşıma işlemi yapamaz.

Herkese açık GitHub içeriği GitHub'ın görünürlük ve saklama kontrollerine tabidir. Herkese açık bir issue içine parola, API anahtarı, token, kimlik belgesi, özel sohbet kaydı veya başka bir sır yazmayın.

## 5. Yapay zekâ çıktısı ve oluşturulmuş içerik

Xylune bir istemci arayüzüdür; kullanıcının seçtiği üçüncü taraf yapay zekâ modelinin geliştiricisi veya barındırıcısı değildir. Xylune geliştiricisi tek tek model yanıtlarını oluşturmaz, modeli eğitmez veya barındırmaz, yanıtları önceden incelemez ve onaylamaz. Model çıktısı yanlış, zararlı, taraflı, hukuka aykırı veya amaca elverişsiz olabilir. Bu rol ayrımı, hukuken sınırlandırılması mümkün olmayan bir sorumluluğu ortadan kaldırmaz. Kullanım ve garanti hükümleri için [Xylune Kullanım Koşulları](TERMS.md) belgesine bakın.

## 6. Yurt dışı kullanım ve aktarım

Xylune Türkiye dışında kullanılabilir. Kullanıcının cihazı, yapay zekâ sağlayıcısı, depolama sağlayıcısı, GitHub ve yapılandırdığı uç noktalar farklı ülkelerde bulunabilir. Yurt dışı işleme, kullanıcının sağlayıcı ve uç nokta seçimiyle başlatılır. Kullanıcının bulunduğu yerde uygulanması zorunlu gizlilik ve tüketici hakları varsa geçerliliğini korur; bu metin bu haklardan feragat ettirmez.

Kullanıcı; kişisel, gizli, kurumsal veya üçüncü kişilere ait verileri ancak gerekli yetki ve hukuki dayanağa sahipse aktarmalıdır.

## 7. Saklama ve silme

Yerel veriler kullanıcı Xylune içinde silene, Android uygulama verisini temizleyene veya uygulamayı kaldırana kadar cihazda kalır. Bulut yedekleri Xylune ya da depolama sağlayıcısı üzerinden silinene kadar sağlayıcıda kalır. Geliştirici, kullanıcının sağlayıcı hesabına erişmeden sağlayıcıdaki yedeği silemez ve merkezi bir yedek kopyası saklamaz. Ayrıntılı adımlar için [veri silme sayfasına](https://omerfaruknehir.github.io/Xylune/data-deletion/) bakın.

Geliştiriciye bilerek gönderilen bilgi; ilgili destek, güvenlik, proje geçmişi, kötüye kullanımın önlenmesi veya hukuki amaç için makul ölçüde gerekli olduğu sürece ve kullanılan iletişim platformunun kontrollerine tabi olarak saklanır.

## 8. Başvuru, iletişim ve yanıt süreleri

Hata bildirimleri ve olağan destek talepleri [Xylune issue sayfası](https://github.com/omerfaruknehir/Xylune/issues) üzerinden iletilebilir. Bu, gönüllü sürdürülen açık kaynaklı bir projedir: **olağan destek için yanıt süresi, erişilebilirlik veya hizmet seviyesi taahhüt edilmez**.

Herkese açık olmaması gereken başvurular için ilgili OAuth onay ekranında özel bir iletişim yöntemi gösterilebilir. Başvuru, geliştiricinin fiilen aldığı yazışmayı veya diğer veriyi tanımlamalıdır; kimliğin doğrulanması ve verinin bulunması için makul ek bilgi istenebilir. Geçerli kişisel veri başvuruları, somut işlemeye ve uygulanabilir hukuka göre zorunlu olan süre içinde sonuçlandırılır. Bu hüküm, uygulanabilir hukukun ötesinde ek bir hak veya yükümlülük yaratmaz ve geliştiriciye cihazda ya da sağlayıcı kontrolünde bulunan verilere teknik erişim sağlamaz.

6698 sayılı Kanun uygulanıyorsa, ilgili kişi Kanun'un 11. maddesindeki haklarını yalnızca geliştiricinin veri sorumlusu olduğu somut işleme bakımından kullanabilir. Başvurunun usulü, kimlik doğrulaması, ücret ve cevap süresi yürürlükteki mevzuata tabidir. Başka ülkelerdeki zorunlu haklar da uygulanabildiği ölçüde saklıdır.

## 9. Değişiklikler

Xylune mimarisi, bağlı hizmetler veya hukuki gereklilikler değiştiğinde bu politika güncellenebilir. Yürürlük tarihi ve depo geçmişi değişiklikleri gösterir.
'''
write("PRIVACY.md", privacy)

terms = r'''# Xylune Terms of Use

**Effective date: August 4, 2026**  
[Türkçe metin aşağıdadır.](#xylune-kullanım-koşulları)

These terms apply to the official Xylune Android application and project materials maintained by **Ömer Faruk Nehir**. By using Xylune, you agree to these terms to the extent they form an enforceable agreement under applicable law. Mandatory consumer, privacy, product-safety, and other rights that cannot lawfully be waived remain unaffected.

## 1. What Xylune is

Xylune is an open-source, local-first client that lets users connect AI providers, local model servers, storage providers, web resources, and local tools of their choice. Xylune does not provide a hosted AI model, does not relay ordinary model requests through a Xylune backend, and does not guarantee continued compatibility with any third-party service.

## 2. Third-party models and services

The user selects and authorizes each AI provider, account, endpoint, model, search source, storage service, or local server. Those services are independent third parties governed by their own terms, privacy policies, availability, geographic restrictions, prices, quotas, and content rules.

The Xylune maintainer does **not** create, train, host, operate, pre-review, or endorse the selected models or their individual outputs. Displaying or transporting an output does not make the maintainer its author, publisher, professional adviser, or guarantor. A provider can change or discontinue its behavior without notice to Xylune.

## 3. AI output is not reliable by default

AI-generated text, code, files, searches, citations, calculations, images, widgets, tool instructions, and other output may be false, incomplete, outdated, biased, unsafe, infringing, or otherwise unsuitable. Xylune's validation, permission, repair, citation, and safety features reduce some risks but do not prove that an output is correct or lawful.

You must independently review output before relying on it, publishing it, executing code or commands, installing packages, spending money, making a legal/medical/financial/safety decision, or using it in production or safety-critical work. Xylune and AI output are not a substitute for qualified professional advice.

## 4. User responsibilities

You are responsible for:

- using Xylune and connected services lawfully and only with data and accounts you are authorized to use;
- safeguarding API keys, credentials, devices, backups, and exported archives;
- reviewing provider permissions, retention, training settings, fees, quotas, and output rules;
- confirming generated commands, code, packages, widgets, links, and file operations before allowing consequential actions;
- maintaining independent backups of important data; and
- complying with licenses, intellectual-property rights, privacy duties, institutional rules, sanctions, export controls, and local law applicable to your use.

Do not use Xylune to harm others, gain unauthorized access, distribute malware, evade service restrictions, or process data you are not entitled to process.

## 5. Local tools and generated interfaces

Python, Linux/PRoot, package installation, file operations, generated snippets, and Home-screen widgets can modify app-private data, consume resources, make network requests, or produce unexpected results. Android confinement, prompts, capability checks, compilation, and repair loops are safeguards, not warranties or a secure virtual machine. Do not execute untrusted code or treat a successful check as proof of safety.

## 6. Availability, updates, and support

Xylune may change, break, suspend features, remove compatibility, or stop being maintained. No uptime, response time, maintenance period, backward compatibility, or support service level is promised. GitHub issues and other support channels may be unanswered or answered late. Security and privacy requests remain subject to any mandatory duties that apply to data actually received and controlled by the maintainer.

## 7. Warranty disclaimer

To the maximum extent permitted by applicable law, Xylune and its project materials are provided **“AS IS”** and **“AS AVAILABLE,”** without express or implied warranties, including warranties of accuracy, non-infringement, merchantability, fitness for a particular purpose, security, availability, data preservation, provider compatibility, or error-free operation.

## 8. Limitation of liability

To the maximum extent permitted by applicable law, the maintainer and contributors are not liable for indirect, incidental, special, exemplary, punitive, or consequential loss; loss of data, credentials, accounts, revenue, opportunity, reputation, or business; provider charges; third-party AI output; user decisions; or damage caused by connected services, generated content, local tools, modified builds, or use contrary to these terms.

Nothing in these terms excludes or limits liability, remedies, or consumer rights where exclusion or limitation is prohibited, including liability that applicable law makes non-waivable. A clause that is invalid or unenforceable is limited or severed only to the minimum extent necessary; the rest remains in effect.

## 9. Open-source licence and modified builds

Source code use, modification, and distribution are governed by the repository's open-source licences. A fork or modified build may have different behavior, signing, data flows, permissions, or operators. Its distributor is responsible for accurately describing those changes; it is not represented as an official build merely because it uses some Xylune code.

## 10. Governing rules and changes

These terms are interpreted under applicable law, with mandatory protections of the user's jurisdiction preserved where they apply. The terms may be updated with project or legal changes. The effective date and repository history identify revisions. Continuing to use a later version after being given reasonable notice may constitute acceptance only to the extent permitted by applicable law.

---

# Xylune Kullanım Koşulları

**Yürürlük tarihi: 4 Ağustos 2026**

Bu koşullar, **Ömer Faruk Nehir** tarafından sürdürülen resmî Xylune Android uygulaması ve proje materyalleri için geçerlidir. Xylune'u kullanmanız, uygulanabilir hukuka göre icra edilebilir bir sözleşme oluşturduğu ölçüde bu koşulları kabul ettiğiniz anlamına gelir. Hukuken feragat edilemeyen zorunlu tüketici, gizlilik, ürün güvenliği ve diğer haklar saklıdır.

## 1. Xylune nedir

Xylune, kullanıcının seçtiği yapay zekâ sağlayıcısını, yerel model sunucusunu, depolama sağlayıcısını, web kaynağını ve yerel aracı bağlayan açık kaynaklı, yerel öncelikli bir istemcidir. Xylune barındırılan bir yapay zekâ modeli sunmaz, olağan model isteklerini bir Xylune sunucusundan geçirmez ve üçüncü taraf hizmetlerle sürekli uyumluluk garanti etmez.

## 2. Üçüncü taraf modeller ve hizmetler

Her yapay zekâ sağlayıcısını, hesabı, uç noktasını, modeli, arama kaynağını, depolama hizmetini veya yerel sunucuyu kullanıcı seçer ve yetkilendirir. Bunlar kendi koşulları, gizlilik politikaları, erişilebilirlikleri, bölgesel sınırlamaları, fiyatları, kotaları ve içerik kuralları olan bağımsız üçüncü taraflardır.

Xylune geliştiricisi seçilen modeli veya tek tek çıktıları **oluşturmaz, eğitmez, barındırmaz, işletmez, önceden incelemez veya onaylamaz**. Bir çıktının gösterilmesi ya da taşınması geliştiriciyi çıktının yazarı, yayıncısı, profesyonel danışmanı veya garantörü yapmaz. Sağlayıcı davranışını Xylune'a haber vermeden değiştirebilir veya hizmeti durdurabilir.

## 3. Yapay zekâ çıktısı varsayılan olarak güvenilir değildir

Yapay zekâ tarafından oluşturulan metin, kod, dosya, arama, kaynak, hesaplama, görsel, widget, araç talimatı ve diğer çıktılar yanlış, eksik, eski, taraflı, güvensiz, hak ihlal eden veya başka şekilde amaca elverişsiz olabilir. Xylune'un doğrulama, izin, onarım, kaynak ve güvenlik özellikleri bazı riskleri azaltır; çıktının doğru veya hukuka uygun olduğunu ispatlamaz.

Bir çıktıya güvenmeden, yayımlamadan, kod/komut çalıştırmadan, paket kurmadan, para harcamadan, hukuki/tıbbi/mali/güvenlik kararı vermeden ya da üretim veya kritik bir işte kullanmadan önce bağımsız olarak incelemelisiniz. Xylune ve yapay zekâ çıktısı yetkin profesyonel danışmanlığın yerine geçmez.

## 4. Kullanıcının sorumlulukları

Kullanıcı şunlardan sorumludur:

- Xylune'u ve bağlı hizmetleri hukuka uygun ve yalnızca kullanmaya yetkili olduğu veri ve hesaplarla kullanmak;
- API anahtarlarını, kimlik bilgilerini, cihazı, yedekleri ve dışa aktarılan arşivleri korumak;
- sağlayıcı izinlerini, saklama/eğitim ayarlarını, ücretleri, kotaları ve çıktı kurallarını incelemek;
- sonuç doğuran işlemlere izin vermeden önce oluşturulan komut, kod, paket, widget, bağlantı ve dosya işlemlerini doğrulamak;
- önemli verilerin bağımsız yedeğini tutmak; ve
- kullanımına uygulanan lisans, fikrî mülkiyet, gizlilik, kurum kuralı, yaptırım, ihracat kontrolü ve yerel hukuk hükümlerine uymak.

Xylune'u başkasına zarar vermek, yetkisiz erişim sağlamak, zararlı yazılım dağıtmak, hizmet kısıtlarını aşmak veya işlemeye yetkili olmadığınız veriyi işlemek için kullanmayın.

## 5. Yerel araçlar ve oluşturulmuş arayüzler

Python, Linux/PRoot, paket kurulumu, dosya işlemleri, oluşturulan snippet'lar ve ana ekran widget'ları uygulamaya özel veriyi değiştirebilir, kaynak tüketebilir, ağ isteği yapabilir veya beklenmeyen sonuç üretebilir. Android izolasyonu, onaylar, yetenek kontrolleri, derleme ve onarım döngüleri birer güvenlik önlemidir; garanti veya güvenli bir sanal makine değildir. Güvenmediğiniz kodu çalıştırmayın ve başarılı bir kontrolü güvenlik ispatı saymayın.

## 6. Erişilebilirlik, güncellemeler ve destek

Xylune değişebilir, bozulabilir, özellikleri askıya alabilir, uyumluluğu kaldırabilir veya sürdürülmesi sona erebilir. Çalışma süresi, yanıt süresi, bakım dönemi, geriye dönük uyumluluk veya destek hizmet seviyesi taahhüt edilmez. GitHub issue'ları ve diğer destek kanalları yanıtsız kalabilir veya geç yanıtlanabilir. Güvenlik ve gizlilik başvuruları, yalnızca geliştiricinin fiilen aldığı ve kontrol ettiği veriler için uygulanabilecek zorunlu yükümlülüklere tabidir.

## 7. Garanti reddi

Uygulanabilir hukukun izin verdiği azami ölçüde Xylune ve proje materyalleri; doğruluk, hak ihlali yapmama, satılabilirlik, belirli amaca uygunluk, güvenlik, erişilebilirlik, veri koruma, sağlayıcı uyumluluğu veya hatasız çalışma dahil açık ya da örtülü hiçbir garanti olmadan **“OLDUĞU GİBİ”** ve **“MEVCUT OLDUĞU ÖLÇÜDE”** sunulur.

## 8. Sorumluluğun sınırlandırılması

Uygulanabilir hukukun izin verdiği azami ölçüde geliştirici ve katkıda bulunanlar; dolaylı, arızi, özel, örnek niteliğinde, cezai veya sonuçsal zararlardan; veri, kimlik bilgisi, hesap, gelir, fırsat, itibar veya iş kaybından; sağlayıcı ücretlerinden; üçüncü taraf yapay zekâ çıktısından; kullanıcı kararlarından; bağlı hizmet, oluşturulmuş içerik, yerel araç, değiştirilmiş derleme veya bu koşullara aykırı kullanımdan doğan zararlardan sorumlu değildir.

Bu koşullardaki hiçbir hüküm, uygulanabilir hukukun sınırlandırılmasını yasakladığı sorumluluğu, başvuru yolunu veya tüketici hakkını ortadan kaldırmaz ya da sınırlandırmaz. Geçersiz veya uygulanamaz bir hüküm yalnızca gerekli olan en dar ölçüde sınırlandırılır veya ayrılır; kalan hükümler geçerliliğini sürdürür.

## 9. Açık kaynak lisansı ve değiştirilmiş derlemeler

Kaynak kodun kullanımı, değiştirilmesi ve dağıtımı depodaki açık kaynak lisanslarına tabidir. Fork veya değiştirilmiş derleme farklı davranış, imza, veri akışı, izin veya işletmeciye sahip olabilir. Bu değişiklikleri doğru açıklama sorumluluğu dağıtıcıya aittir; bazı Xylune kodlarını kullanması onu resmî derleme yapmaz.

## 10. Uygulanabilir kurallar ve değişiklikler

Bu koşullar uygulanabilir hukuka göre yorumlanır; kullanıcının bulunduğu yerde geçerli zorunlu korumalar saklıdır. Proje veya hukuki gereklilikler değiştiğinde koşullar güncellenebilir. Yürürlük tarihi ve depo geçmişi değişiklikleri gösterir. Makul bildirimden sonra yeni sürümü kullanmaya devam etmek, yalnızca uygulanabilir hukukun izin verdiği ölçüde kabul sayılabilir.
'''
write("TERMS.md", terms)

# README legal visibility and current release references.
readme_path = "README.md"
readme = read(readme_path)
readme = replace_once(
    readme,
    '''  <a href="https://github.com/omerfaruknehir/Xylune/issues">Report an issue</a>
''',
    '''  <a href="https://github.com/omerfaruknehir/Xylune/issues">Report an issue</a>
  ·
  <a href="PRIVACY.md">Privacy</a>
  ·
  <a href="TERMS.md">Terms</a>
''',
    "add README legal links",
)
readme = replace_once(readme, "Current version: **0.23.0**", "Current version: **0.23.5**", "README version")
readme = replace_once(readme, "`Xylune-0.23.0-release.apk`", "`Xylune-0.23.5-release.apk`", "README APK name")
readme = replace_once(
    readme,
    '''Xylune is provided **“AS IS”**, without warranties of any kind. Use, modify, and distribute it at your own risk. To the maximum extent permitted by applicable law, the author and contributors are not responsible for data loss, device damage, account loss, service charges, security incidents, or any other direct or indirect consequences arising from the app. Review the source, keep backups, and do not rely on Xylune for safety-critical or irreplaceable work.
''',
    '''Xylune is provided **“AS IS”**, without warranties of any kind. Use, modify, and distribute it at your own risk. Xylune is a client rather than an AI-model host: the maintainer does not create, train, host, pre-review, or endorse individual third-party model outputs. Review the source and output, keep backups, and do not rely on Xylune for safety-critical or irreplaceable work. The detailed allocation of responsibility and all mandatory-rights exceptions are in the [Terms of Use](TERMS.md).
''',
    "update README AI disclaimer",
)
write(readme_path, readme)

# Version and release documentation.
gradle_path = "app/build.gradle.kts"
gradle = read(gradle_path)
gradle = replace_once(gradle, 'versionCode = 173\n        versionName = "0.23.4"', 'versionCode = 174\n        versionName = "0.23.5"', "bump app version")
write(gradle_path, gradle)

changelog_path = "CHANGELOG.md"
changelog = read(changelog_path)
entry = '''## 0.23.5 — 2026-08-04

- Keep predictive-back pages fully opaque, reduce the excessive page travel, and remove the short gesture dead zone while a prior page transition is settling.
- Show the exact bundled Xylune core prompt as selectable, non-editable text, including its revision and a clear distinction from request-specific runtime layers.
- Rewrite the bilingual privacy notice around Xylune's actual local/direct architecture, worldwide use, data the maintainer can genuinely access, and mandatory-rights-only response obligations.
- Add bilingual Terms of Use that separate Xylune from third-party AI providers, reject warranties and support SLAs where lawful, and preserve non-waivable consumer rights.
- Retain the six-file canonical GitHub release asset count and upload order introduced in 0.23.4.

'''
if not changelog.startswith("## 0.23.2"):
    raise RuntimeError("CHANGELOG: unexpected first release heading")
write(changelog_path, entry + changelog)

release_notes = '''# Xylune 0.23.5

This release repairs predictive Back, makes Xylune's built-in instructions inspectable, and narrows the legal documents to the app's real local/direct role.

## Predictive Back

- Active pages remain fully opaque throughout the gesture; the whole Compose tree is no longer faded through intermediate surfaces.
- Page travel is reduced and a small scale response replaces the visually unstable endpoint fade.
- A new Back gesture can take ownership while the previous short page animation is still settling instead of falling through or doing nothing.

## Inspectable core prompt

**Settings → Custom instructions** now shows the exact bundled Xylune core prompt as selectable, read-only text with its revision. Xylune also explains that date, tool, research, memory, attachment, and generated-content instructions are assembled dynamically for each request and are not user-editable.

## Privacy and third-party AI roles

- The bilingual privacy policy now states that Xylune can be used worldwide and does not operate a chat relay or central backup server.
- The maintainer's data role is limited to information actually submitted to maintainer-controlled support, security, project, or OAuth-administration channels.
- Ordinary volunteer support has no promised response time; mandatory privacy-request deadlines remain preserved where they legally apply.
- New bilingual Terms of Use state that users select independent AI providers and that the Xylune maintainer does not create, train, host, pre-review, or endorse individual model outputs.
- Liability and warranty limitations apply only to the maximum extent allowed by law and do not waive mandatory consumer rights.

## Release files

The release continues the 0.23.4 invariant: exactly six assets in the same canonical order—APK, AAB, source ZIP, source tarball, release manifest, then SHA-256 list.
'''
write("docs/releases/RELEASE_NOTES_0.23.5.md", release_notes)

# The one-shot workflow removes this helper before committing the real change.
print("Prepared Xylune 0.23.5 source changes")
