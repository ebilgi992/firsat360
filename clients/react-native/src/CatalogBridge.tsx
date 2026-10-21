/**
 * FIRSAT360 — İstemci Katalog Köprüsü (React Native başvuru uygulaması)
 *
 * Akış: kullanıcı market kartına dokunur → resmî broşür sayfası kullanıcının cihazında, yükleme katmanının ARKASINDA
 * açılır → profil betiği (bridgeScripts.ts; Android uygulamasıyla aynı kaynak) ~0,9 sn'de bir enjekte edilir ve
 * sonucu postMessage ile bildirir → sayfa görselleri tam ekran yerel galeriye, PDF'ler yerel PDF okuyucuya aktarılır;
 * WebView kapatılır.
 *
 * Sınırlar (bilinçli): doğrulama/CAPTCHA sayfası OTOMATİK GEÇİLMEZ — WebView kullanıcıya gösterilir, kullanıcı
 * tamamlarsa okuma sürer. Kimlik bilgisi, çerez, gizli anahtar veya özel API kullanılmaz; yalnızca sayfanın kullanıcıya
 * zaten gösterdiği görseller okunur. Görsel/PDF/gezinme adresleri https + izinli alan adı ailesiyle sınırlıdır.
 *
 * Bağımlılıklar: react-native-webview, react-native-pdf (PDF), react-native-safe-area-context (isteğe bağlı değil:
 * SafeAreaView yeterli). Galeri FlatList ile (ek bağımlılık yok); yakınlaştırma iOS'ta ScrollView maximumZoomScale ile.
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Dimensions,
  FlatList,
  Image,
  Linking,
  Modal,
  Platform,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { WebView, WebViewMessageEvent } from 'react-native-webview';
import type { ShouldStartLoadRequest } from 'react-native-webview/lib/WebViewTypes';
import Pdf from 'react-native-pdf';
import { BRIDGE_MARKETS, BridgeEntry, bridgeScript } from './bridgeScripts';

// ---- Köprü sonuç biçimleri (common.js ile aynı) ----
type PdfItem = { url: string; title: string; dates: string };
type BridgeResult =
  | { type: 'images'; urls: string[]; end?: boolean; total?: number }
  | { type: 'pdf'; items: PdfItem[] }
  | { type: 'navigate'; url: string }
  | { type: 'challenge' }
  | { type: 'pending'; error?: string };

type Phase =
  | { kind: 'loading' }
  | { kind: 'web'; note?: string } // kullanıcı sayfayı görüyor (doğrulama, "Sayfayı göster" veya bulunamadı)
  | { kind: 'gallery'; pages: string[] }
  | { kind: 'pdfChoice'; items: PdfItem[] }
  | { kind: 'pdf'; item: PdfItem };

export interface CatalogBridgeProps {
  /** Market kimliği: "a101" | "sok" | "migros" | "gratis" | "carrefoursa" | "rossmann" */
  targetMarket: string;
  /** Çalışma anındaki feed.json → client_bridge[targetMarket]; verilmezse derleme anındaki yapılandırma. */
  entry?: BridgeEntry;
  /** Görüntülenecek market adı (başlık). */
  marketName?: string;
  onClose: () => void;
  /** Sayfalar bulunduğunda (ör. cihaz önbelleğine yazmak için — Android'deki DeviceBrochures ile aynı fikir). */
  onPagesFound?: (market: string, pages: string[], sourceUrl: string) => void;
}

const POLL_MS = 900;
const CHALLENGE_POLL_MS = 3000;
const MAX_MS = 15000; // belge başına okuma süresi (parça değişimi / yönlendirme sıfırlayamaz)
const MAX_NAVIGATIONS = 2;
const FIRST_POLL_DELAY_MS = 1200;

/** Oturum başına rastgele değer: yalnızca bizim enjekte ettiğimiz betiğin mesajları kabul edilir. */
function makeNonce(): string {
  let n = '';
  for (let i = 0; i < 4; i++) n += Math.floor(Math.random() * 0x7fffffff).toString(36);
  return n + Date.now().toString(36);
}

// ---- Güvenlik yardımcıları ----
function hostOf(url: string): string {
  const m = /^https:\/\/([^/?#:]+)/i.exec(url);
  return m ? m[1].toLowerCase() : '';
}
/** "a101.com.tr" ailesi: a101.com.tr ve *.a101.com.tr. */
function inFamily(url: string, families: string[]): boolean {
  const h = hostOf(url);
  return !!h && families.some((f) => h === f || h.endsWith('.' + f));
}
function safeUrls(list: unknown, families: string[]): string[] {
  if (!Array.isArray(list)) return [];
  const out: string[] = [];
  for (const u of list) {
    if (typeof u === 'string' && u.startsWith('https://') && inFamily(u, families) && !out.includes(u)) out.push(u);
  }
  return out;
}
function familyOf(url: string): string {
  const parts = hostOf(url).split('.');
  // "www.a101.com.tr" → "a101.com.tr", "katalog.carrefoursa.com" → "carrefoursa.com"
  const tld2 = parts.length >= 3 && /^(com|net|org|gov|edu|gen|web|bel|k12)$/.test(parts[parts.length - 2]);
  return parts.slice(tld2 ? -3 : -2).join('.');
}
/** Zarfı ({n, r}) açar; nonce tutmuyorsa null (sayfanın kendi mesajı — sayaçlara dokunulmaz). */
function parseEnvelope(raw: string, nonce: string): BridgeResult | null | undefined {
  try {
    const env = JSON.parse(raw);
    if (!env || env.n !== nonce || typeof env.r !== 'string') return undefined;
    const r = JSON.parse(env.r);
    return r && typeof r.type === 'string' ? (r as BridgeResult) : null;
  } catch {
    return undefined;
  }
}

export default function CatalogBridge({ targetMarket, entry, marketName, onClose, onPagesFound }: CatalogBridgeProps) {
  const cfg = entry ?? BRIDGE_MARKETS[targetMarket];
  const webRef = useRef<WebView>(null);
  const [phase, setPhase] = useState<Phase>({ kind: 'loading' });
  const [uri, setUri] = useState<string | undefined>(cfg?.url);
  const [status, setStatus] = useState('Resmî siteden güncel broşürler alınıyor…');

  // Durumsuz betik + birikimli durum burada (Android WebScreen ile aynı kurallar)
  const nonce = useRef(makeNonce()).current;
  const deadlines = useRef(new Map<string, number>());
  const challengeShown = useRef(false);
  const lastCount = useRef(-1);
  const navigations = useRef(0);
  const best = useRef<string[]>([]);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const done = useRef(false);

  // allowedHosts boşsa başlangıç sayfasının kendi alan ailesi (hiçbir mesajın kabul edilmemesi → sonsuz bekleme olmaz)
  const families = useMemo(
    () => (cfg?.allowedHosts?.length ? cfg.allowedHosts : cfg ? [familyOf(cfg.url)] : []),
    [cfg],
  );
  const script = useMemo(() => bridgeScript(cfg?.profile ?? 'generic', nonce), [cfg, nonce]);
  const deadlineFor = useCallback((pageUrl: string) => {
    const key = pageUrl.split('#')[0];
    let d = deadlines.current.get(key);
    if (d === undefined) {
      d = Date.now() + MAX_MS;
      deadlines.current.set(key, d);
    }
    return d;
  }, []);

  const stopPolling = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
  }, []);

  const schedule = useCallback(
    (delay: number) => {
      stopPolling();
      timer.current = setTimeout(() => webRef.current?.injectJavaScript(script), delay);
    },
    [script, stopPolling],
  );

  useEffect(() => stopPolling, [stopPolling]);

  const handOffImages = useCallback(
    (pages: string[]) => {
      done.current = true;
      stopPolling();
      onPagesFound?.(targetMarket, pages, uri ?? cfg?.url ?? '');
      setPhase({ kind: 'gallery', pages }); // WebView ağacından çıkar → bellek bırakılır
    },
    [cfg, onPagesFound, stopPolling, targetMarket, uri],
  );

  const onMessage = useCallback(
    (ev: WebViewMessageEvent) => {
      if (done.current) return;
      // Mesaj, sayfanın kendi betiklerinden de gelebilir: nonce'u, kaynağı ve her adresi doğrula.
      const res = parseEnvelope(ev.nativeEvent.data, nonce);
      if (res === undefined) return; // bizim betiğimiz değil → yok say (sayaç/zamanlayıcı değişmez)
      if (!inFamily(ev.nativeEvent.url, families)) return;
      const pageUrl = ev.nativeEvent.url;

      switch (res?.type) {
        case 'challenge':
          // Otomatik çözülmez: kullanıcıya gösterilir; 3 sn'de bir yalnızca geçilip geçilmediğine bakılır.
          if (!challengeShown.current) {
            challengeShown.current = true;
            setPhase({ kind: 'web', note: 'Site bir doğrulama istiyor. Adımı tamamladığınızda broşürler otomatik açılır.' });
          }
          deadlines.current.set(pageUrl.split('#')[0], Date.now() + MAX_MS);
          schedule(CHALLENGE_POLL_MS);
          return;
        case 'pdf': {
          const items = (Array.isArray(res.items) ? res.items : [])
            .filter((i) => i && typeof i.url === 'string' && i.url.startsWith('https://') && inFamily(i.url, families))
            .map((i) => ({ url: i.url, title: String(i.title || 'Broşür'), dates: String(i.dates || '') }));
          if (items.length) {
            done.current = true;
            stopPolling();
            setPhase(items.length === 1 ? { kind: 'pdf', item: items[0] } : { kind: 'pdfChoice', items });
            return;
          }
          break;
        }
        case 'navigate':
          if (
            typeof res.url === 'string' && res.url.startsWith('https://') && inFamily(res.url, families) &&
            res.url !== pageUrl && navigations.current < MAX_NAVIGATIONS &&
            familyOf(pageUrl) === familyOf(cfg?.url ?? '') // yalnızca marketin kendi sayfasından geçilir
          ) {
            navigations.current += 1;
            lastCount.current = -1;
            stopPolling();
            setStatus('Resmî katalog açılıyor…');
            setUri(res.url); // onLoadEnd → okuma yeniden başlar
            return;
          }
          break;
        case 'images': {
          const pages = safeUrls(res.urls, families);
          if (pages.length > best.current.length) best.current = pages;
          // Geçiş: sayfa sonuna ulaşıldı VE iki ölçüm aynı (tembel görseller yüklendi) → eksik sayfa kalmaz.
          if (pages.length >= 2 && pages.length === lastCount.current && res.end) return handOffImages(pages);
          lastCount.current = pages.length;
          setStatus(pages.length ? `${pages.length} broşür sayfası bulundu…` : 'Resmî siteden güncel broşürler alınıyor…');
          break;
        }
        default:
          break;
      }

      if (Date.now() > deadlineFor(pageUrl)) {
        stopPolling();
        if (best.current.length >= 2) return handOffImages(best.current); // süre doldu ama sayfalar var
        setPhase({ kind: 'web', note: 'Bu sayfada broşür görseli bulunamadı; resmî sayfa gösteriliyor.' });
        return;
      }
      schedule(POLL_MS);
    },
    [cfg, deadlineFor, families, handOffImages, nonce, schedule, stopPolling],
  );

  const onLoadEnd = useCallback(() => {
    if (done.current) return;
    lastCount.current = -1;
    schedule(FIRST_POLL_DELAY_MS);
  }, [schedule]);

  /** Üst çerçevede yalnızca izinli alanlarda gezinme; diğer bağlantılar sistem tarayıcısında açılır. */
  const onShouldStart = useCallback(
    (req: ShouldStartLoadRequest) => {
      if (req.isTopFrame === false) return true; // gömülü katalog çerçeveleri (ör. fliphtml5 iframe)
      if (req.url.startsWith('https://') && inFamily(req.url, families)) return true;
      if (/^https?:\/\//.test(req.url)) Linking.openURL(req.url).catch(() => undefined);
      return false;
    },
    [families],
  );

  if (!cfg) {
    return (
      <Centered>
        <Text style={s.muted}>Bu market için resmî broşür köprüsü tanımlı değil.</Text>
        <Btn label="Kapat" onPress={onClose} />
      </Centered>
    );
  }

  const title = `${marketName ?? targetMarket} broşür`;

  if (phase.kind === 'gallery') return <NativeGallery title={title} pages={phase.pages} onClose={onClose} />;
  if (phase.kind === 'pdf') return <NativePdf title={phase.item.title || title} item={phase.item} onClose={onClose} />;
  if (phase.kind === 'pdfChoice') {
    return (
      <SafeAreaView style={s.root}>
        <Header title={title} onClose={onClose} />
        <ScrollView contentContainerStyle={{ padding: 16 }}>
          {phase.items.map((it) => (
            <Pressable key={it.url} style={s.choice} onPress={() => setPhase({ kind: 'pdf', item: it })}
              accessibilityRole="button" accessibilityLabel={`${it.title} ${it.dates}`}>
              <Text style={s.choiceTitle}>{it.title}</Text>
              {!!it.dates && <Text style={s.muted}>{it.dates}</Text>}
            </Pressable>
          ))}
        </ScrollView>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={s.root}>
      <Header title={title} onClose={onClose} />
      <View style={{ flex: 1 }}>
        <WebView
          ref={webRef}
          source={{ uri: uri ?? cfg.url }}
          originWhitelist={['https://*']}
          onLoadEnd={onLoadEnd}
          onMessage={onMessage}
          onShouldStartLoadWithRequest={onShouldStart}
          javaScriptEnabled
          domStorageEnabled
          javaScriptCanOpenWindowsAutomatically={false}
          setSupportMultipleWindows={false}
          allowFileAccess={false}
          allowsInlineMediaPlayback={false}
          mixedContentMode="never"
          incognito={false} // kullanıcının kendi tamamladığı doğrulama çerezi oturumda kalsın
          style={{ flex: 1 }}
        />
        {phase.kind === 'loading' && (
          <View style={s.overlay} accessibilityLabel="Resmî siteden güncel broşürler alınıyor">
            <ActivityIndicator size="large" color="#F2F3F7" />
            <Text style={s.overlayText}>{status}</Text>
            <Text style={s.overlayHost}>{hostOf(uri ?? cfg.url)}</Text>
            <Btn label="Sayfayı göster" onPress={() => setPhase({ kind: 'web' })} ghost />
          </View>
        )}
        {phase.kind === 'web' && !!phase.note && (
          <View style={s.toast}><Text style={s.toastText}>{phase.note}</Text></View>
        )}
      </View>
    </SafeAreaView>
  );
}

// ---------------------------------------------------------------------------------------------------------------
// Yerel tam ekran galeri: yatay sayfalı FlatList, her sayfa yakınlaştırılabilir (iOS: ScrollView zoom; Android'de çift
// dokunma büyütmesi için react-native-reanimated-carousel / gesture-handler ile değiştirilebilir).
// ---------------------------------------------------------------------------------------------------------------
function NativeGallery({ title, pages, onClose }: { title: string; pages: string[]; onClose: () => void }) {
  const { width, height } = Dimensions.get('window');
  const [index, setIndex] = useState(0);
  const listRef = useRef<FlatList<string>>(null);
  const go = (i: number) => {
    const n = Math.max(0, Math.min(pages.length - 1, i));
    listRef.current?.scrollToIndex({ index: n, animated: true });
    setIndex(n);
  };
  return (
    <Modal visible animationType="fade" onRequestClose={onClose} statusBarTranslucent>
      <View style={[s.root, { backgroundColor: '#000' }]}>
        <FlatList
          ref={listRef}
          data={pages}
          keyExtractor={(u) => u}
          horizontal
          pagingEnabled
          showsHorizontalScrollIndicator={false}
          initialNumToRender={2}
          windowSize={3}
          getItemLayout={(_, i) => ({ length: width, offset: width * i, index: i })}
          onMomentumScrollEnd={(e) => setIndex(Math.round(e.nativeEvent.contentOffset.x / width))}
          renderItem={({ item, index: i }) => (
            <ScrollView
              style={{ width, height }}
              contentContainerStyle={{ width, height, alignItems: 'center', justifyContent: 'center' }}
              maximumZoomScale={4}
              minimumZoomScale={1}
              centerContent
              showsVerticalScrollIndicator={false}
            >
              <Image source={{ uri: item }} style={{ width, height: height * 0.86 }} resizeMode="contain"
                accessibilityLabel={`Sayfa ${i + 1} / ${pages.length}`} />
            </ScrollView>
          )}
        />
        <SafeAreaView style={s.galleryBar} pointerEvents="box-none">
          <Pressable onPress={onClose} hitSlop={12} accessibilityRole="button" accessibilityLabel="Kapat">
            <Text style={s.galleryClose}>✕</Text>
          </Pressable>
          <Text style={s.galleryTitle} numberOfLines={1}>{title}</Text>
          <Text style={s.galleryCount}>{index + 1} / {pages.length}</Text>
        </SafeAreaView>
        <View style={s.galleryNav} pointerEvents="box-none">
          <Btn label="‹ Önceki" onPress={() => go(index - 1)} ghost disabled={index === 0} />
          <Btn label="Sonraki ›" onPress={() => go(index + 1)} ghost disabled={index >= pages.length - 1} />
        </View>
      </View>
    </Modal>
  );
}

function NativePdf({ title, item, onClose }: { title: string; item: PdfItem; onClose: () => void }) {
  const [page, setPage] = useState({ cur: 1, total: 0 });
  const [error, setError] = useState(false);
  return (
    <SafeAreaView style={s.root}>
      <Header title={title} sub={page.total ? `${page.cur} / ${page.total}${item.dates ? ' · ' + item.dates : ''}` : item.dates}
        onClose={onClose} />
      {error ? (
        <Centered>
          <Text style={s.muted}>PDF açılamadı.</Text>
          <Btn label="Resmî sayfada aç" onPress={() => Linking.openURL(item.url)} />
        </Centered>
      ) : (
        <Pdf
          source={{ uri: item.url, cache: true }}
          trustAllCerts={false}
          enablePaging={Platform.OS === 'android'}
          horizontal={false}
          onLoadComplete={(n) => setPage({ cur: 1, total: n })}
          onPageChanged={(cur, total) => setPage({ cur, total })}
          onError={() => setError(true)}
          style={{ flex: 1, backgroundColor: '#0B0D12' }}
        />
      )}
    </SafeAreaView>
  );
}

// ---- Küçük UI parçaları ----
function Header({ title, sub, onClose }: { title: string; sub?: string; onClose: () => void }) {
  return (
    <View style={s.header}>
      <Pressable onPress={onClose} hitSlop={12} accessibilityRole="button" accessibilityLabel="Geri">
        <Text style={s.back}>‹</Text>
      </Pressable>
      <View style={{ flex: 1 }}>
        <Text style={s.headerTitle} numberOfLines={1}>{title}</Text>
        {!!sub && <Text style={s.headerSub} numberOfLines={1}>{sub}</Text>}
      </View>
    </View>
  );
}
function Btn({ label, onPress, ghost, disabled }: { label: string; onPress: () => void; ghost?: boolean; disabled?: boolean }) {
  return (
    <Pressable onPress={onPress} disabled={disabled} accessibilityRole="button"
      style={({ pressed }) => [s.btn, ghost && s.btnGhost, (pressed || disabled) && { opacity: disabled ? 0.35 : 0.7 }]}>
      <Text style={[s.btnText, ghost && { color: '#F2F3F7' }]}>{label}</Text>
    </Pressable>
  );
}
function Centered({ children }: { children: React.ReactNode }) {
  return <View style={[s.root, { alignItems: 'center', justifyContent: 'center', padding: 24 }]}>{children}</View>;
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#0B0D12' },
  header: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 10, gap: 12 },
  back: { color: '#F2F3F7', fontSize: 30, lineHeight: 30, paddingRight: 4 },
  headerTitle: { color: '#F2F3F7', fontSize: 17, fontWeight: '700' },
  headerSub: { color: 'rgba(242,243,247,0.7)', fontSize: 12.5, marginTop: 2 },
  overlay: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(11,13,18,0.95)', alignItems: 'center', justifyContent: 'center', padding: 24 },
  overlayText: { color: '#F2F3F7', fontSize: 15, fontWeight: '600', marginTop: 16, textAlign: 'center' },
  overlayHost: { color: 'rgba(242,243,247,0.7)', fontSize: 12.5, marginTop: 6, marginBottom: 22 },
  toast: { position: 'absolute', left: 16, right: 16, bottom: 24, backgroundColor: '#1C2030', borderRadius: 12, padding: 14 },
  toastText: { color: '#F2F3F7', fontSize: 14 },
  muted: { color: 'rgba(242,243,247,0.7)', fontSize: 14, textAlign: 'center', marginBottom: 12 },
  btn: { backgroundColor: '#FF5A1F', borderRadius: 12, paddingHorizontal: 18, paddingVertical: 12, minHeight: 44, justifyContent: 'center' },
  btnGhost: { backgroundColor: 'rgba(255,255,255,0.12)' },
  btnText: { color: '#fff', fontSize: 15, fontWeight: '600', textAlign: 'center' },
  choice: { backgroundColor: '#161A24', borderRadius: 14, padding: 16, marginBottom: 12 },
  choiceTitle: { color: '#F2F3F7', fontSize: 16, fontWeight: '600', marginBottom: 4 },
  galleryBar: { position: 'absolute', top: 0, left: 0, right: 0, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingTop: 8, gap: 12, backgroundColor: 'rgba(0,0,0,0.45)' },
  galleryClose: { color: '#fff', fontSize: 22, padding: 8 },
  galleryTitle: { flex: 1, color: '#fff', fontSize: 15, fontWeight: '600' },
  galleryCount: { color: '#fff', fontSize: 14, fontVariant: ['tabular-nums'] },
  galleryNav: { position: 'absolute', bottom: 28, left: 16, right: 16, flexDirection: 'row', justifyContent: 'space-between' },
});
